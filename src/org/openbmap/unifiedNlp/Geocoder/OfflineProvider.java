/*
	Radiobeacon - Openbmap Unified Network Location Provider
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbmap.unifiedNlp.Geocoder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openbmap.unifiedNlp.Preferences;
import org.openbmap.unifiedNlp.services.Cell;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OfflineProvider extends AbstractProvider implements ILocationProvider {

    // Default accuracy for wifi results (in meter)
    public static final int DEFAULT_WIFI_ACCURACY = 30;
    // Default accuracy for cell results (in meter)
    public static final int DEFAULT_CELL_ACCURACY = 3000;
    private static final String TAG = OfflineProvider.class.getName();
    private ILocationCallback mListener;

    /**
     * Keeps the SharedPreferences.
     */
    private SharedPreferences prefs = null;

    /**
     * Database containing well-known wifis from openbmap.org.
     */
    private SQLiteDatabase mCatalog;

    public OfflineProvider(final Context ctx, final ILocationCallback listener) {
        mListener = listener;
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        // Open catalog database
        String path = prefs.getString(Preferences.KEY_DATA_FOLDER, ctx.getExternalFilesDir(null).getAbsolutePath())
                + File.separator + prefs.getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.VAL_CATALOG_FILE);
        mCatalog = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void getLocation(final ArrayList<String> wifiList, final List<Cell> cellsList) {
        LocationQueryParams params = new LocationQueryParams(wifiList, cellsList);

        new AsyncTask<LocationQueryParams, Void, Location>() {
            private int EMPTY_WIFIS_QUERY = -1;
            private int EMPTY_CELLS_QUERY = -2;
            private int WIFIS_NOT_FOUND = -101;
            private int CELLS_NOT_FOUND = -102;
            private int CELLS_DATABASE_NA = -999;
            private int WIFIS_MATCH = 201;
            private int CELLS_MATCH = 202;

            private int state;

            @SuppressLint("DefaultLocale")
            @Override
            protected Location doInBackground(LocationQueryParams... params) {
                if (params == null) {
                    throw new IllegalArgumentException("Wifi list was null");
                }

                if (prefs.getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.CATALOG_NONE).equals(Preferences.CATALOG_NONE)) {
                    throw new IllegalArgumentException("No catalog database was specified");
                }

                ArrayList<String> wifiList = ((LocationQueryParams) params[0]).wifiList;
                String[] wifiQueryArgs = wifiList.toArray(new String[0]);
                HashMap<String, Location> wifiLocations = new HashMap<String, Location>();
                Location result = null;

                if (wifiQueryArgs.length < 1) {
                    Log.i(TAG, "Query contained no bssids");
                    state = EMPTY_WIFIS_QUERY;
                }

                if (state != EMPTY_WIFIS_QUERY) {
                    Log.d(TAG, "Trying wifi mode");
                    String whereClause = "";
                    for (String k : wifiList) {
                        if (whereClause.length() > 1) {
                            whereClause += " OR ";
                        }
                        whereClause += " bssid = ?";
                    }
                    for (int index = 0; index < wifiQueryArgs.length; index++) {
                        wifiQueryArgs[index] = wifiQueryArgs[index].replace(":", "").toUpperCase();
                    }
                    final String wifiSql = "SELECT latitude, longitude, bssid FROM wifi_zone WHERE " + whereClause;
                    //Log.d(TAG, sql);

                    Cursor c = mCatalog.rawQuery(wifiSql, wifiQueryArgs);
                    for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                        Location location = new Location(TAG);
                        location.setLatitude(c.getDouble(0));
                        location.setLongitude(c.getDouble(1));
                        location.setAccuracy(0);
                        location.setTime(System.currentTimeMillis());
                        Bundle b = new Bundle();
                        b.putString("source", "wifis");
                        b.putString("bssid", c.getString(2));
                        location.setExtras(b);
                        wifiLocations.put(c.getString(2), location);
                    }
                    c.close();
                    
                    /*
                     * Building a HashMap and then converting it to an array may seem inefficient at
                     * first, but the HashMap will be needed if we want to factor in signal strengths
                     * at a later point.
                     */
                    Location[] locations = wifiLocations.values().toArray(new Location[0]);
                    
                    if (locations.length == 0) {
                        state = WIFIS_NOT_FOUND;
                        Log.i(TAG, "No known wifis found");
                    } else if (locations.length == 1) {
                    	// We have just one location, pass it
                    	result = (Location) locations[0];
                    	// FIXME DEFAULT_WIFI_ACCURACY is way too optimistic IMHO
                    	result.setAccuracy(DEFAULT_WIFI_ACCURACY);
                        Bundle b = new Bundle();
                        b.putString("source", "wifis");
                        b.putStringArrayList("bssids", ((LocationQueryParams) params[0]).wifiList);
                        result.setExtras(b);
                        state = WIFIS_MATCH;
                        return result;
                    } else {
                    	/*
                    	 * Penalize outliers (which may be happen if a wifi has moved and the database
                    	 * still has the old location, or a mix of old and new location): Walk through
                    	 * the array, calculating distances between each possible pair of locations and
                    	 * store their mean square of that distance. This is the presumed variance (i.e.
                    	 * standard deviation, or accuracy, squared).
                    	 * 
                    	 * Note that we're "abusing" the accuracy field for variance (and interim values
                    	 * to calculate variance) until we've fused the individual locations into a
                    	 * final location. Only at that point will the true accuracy be set for that
                    	 * location.
                    	 * 
                    	 * Locations are fused using a simplified Kálmán filter: since accuracy (and
                    	 * thus variance) is a scalar value, we're applying a one-dimensional Kálmán
                    	 * filter to latitude and longitude independently. This may not be 100%
                    	 * mathematically correct - improvements welcome. 
                    	 * 
                    	 * TODO for now we are considering neither our own distance from the
                    	 * transmitter, nor the accuracy of the transmitter positions themselves (as we
                    	 * don't have these values). Distance from transmitter can be inferred from
                    	 * signal strength and is relatively easy to add, while accuracy of transmitter
                    	 * positions requires an additional column in the wifi catalog.
                    	 */
                    	for (int i = 0; i < locations.length; i++) {
                    		// TODO evaluate distance from cells as well
                    		for (int j = i + 1; j < locations.length; j++) {
                    			float[] distResults = new float[1];
                    			Location.distanceBetween(locations[i].getLatitude(),
                    					locations[i].getLongitude(),
                    					locations[j].getLatitude(),
                    					locations[j].getLongitude(),
                    					distResults);
                    			/*
                    			 * TODO instead of using raw distance, subtract the distance between the
                    			 * device and each transmitter from it (if device-transmitter distance
                    			 * is not known, assume a typical value). If the result is negative,
                    			 * assume zero instead.
                    			 */
                    			// take the square of the distance
                    			distResults[0] *= distResults[0];

                    			// add to the penalty count for the locations of both wifis
                    			locations[i].setAccuracy(locations[i].getAccuracy() + distResults[0]);
                    			locations[j].setAccuracy(locations[j].getAccuracy() + distResults[0]);
                    		}
                    		locations[i].setAccuracy(locations[i].getAccuracy() / (locations.length - 1));
                    		// TODO add square of distance from transmitter (additional source of error)
                    		
                    		if (i == 0)
                    			result = locations[i];
                    		else {
                    			float k = result.getAccuracy() / (result.getAccuracy() + ((Location) locations[i]).getAccuracy());
                    			result.setLatitude((1 - k) * result.getLatitude() + k * ((Location) locations[i]).getLatitude());
                    			result.setLongitude((1 - k) * result.getLongitude() + k * ((Location) locations[i]).getLongitude());
                    			result.setAccuracy((1 - k) * result.getAccuracy());
                    		}
                    	}
                    	
                    	// finally, set actual accuracy (square root of the interim value)
                    	result.setAccuracy((float) Math.sqrt(result.getAccuracy()));
                        Bundle b = new Bundle();
                        b.putString("source", "wifis");
                        b.putStringArrayList("bssids", ((LocationQueryParams) params[0]).wifiList);
                        result.setExtras(b);
                        state = WIFIS_MATCH;
                        return result;
                    }
                }
                // no wifi found, so try cells
                if (state == EMPTY_WIFIS_QUERY || state == WIFIS_NOT_FOUND) {
                    Log.d(TAG, "Trying cell mode");
                    if (!haveCellTables()) {
                        Log.w(TAG, "Cell tables not available. Check your database");
                        state = CELLS_DATABASE_NA;
                        return null;
                    }

                    if (cellsList.size() == 0) {
                        Log.w(TAG, "Query contained no cell infos, skipping update");
                        state = EMPTY_CELLS_QUERY;
                        return null;
                    }

                    Log.d(TAG, "Using " + cellsList.get(0).toString());
                    // Ignore the cell technology for the time being, using cell technology causes problems when cell supports different protocols, e.g.
                    // UMTS and HSUPA and HSUPA+
                    // final String cellSql = "SELECT AVG(latitude), AVG(longitude) FROM cell_zone WHERE cid = ? AND mcc = ? AND mnc = ? AND area = ? and technology = ?";
                    final String cellSql = "SELECT AVG(latitude), AVG(longitude) FROM cell_zone WHERE cid = ? AND mcc = ? AND mnc = ? AND area = ?";
                    try {
                        Cursor c = mCatalog.rawQuery(cellSql, new String[]{
                                String.valueOf(((Cell) cellsList.get(0)).cellId),
                                String.valueOf(((Cell) cellsList.get(0)).mcc),
                                String.valueOf(((Cell) cellsList.get(0)).mnc),
                                String.valueOf(((Cell) cellsList.get(0)).area)
                                /*,String.valueOf(((Cell) cellsList.get(0)).technology)*/
                        });

                        c.moveToFirst();
                        if (!c.isAfterLast()) {
                            result = new Location(TAG);
                            result.setLatitude(c.getDouble(0));
                            result.setLongitude(c.getDouble(1));
                            result.setAccuracy(DEFAULT_CELL_ACCURACY);
                            result.setTime(System.currentTimeMillis());
                            Bundle b = new Bundle();
                            b.putString("source", "cells");
                            result.setExtras(b);
                            c.close();
                            state = CELLS_MATCH;
                            return result;
                        } else {
                            state = CELLS_NOT_FOUND;
                            Log.i(TAG, "No known cells found");
                            return null;
                        }
                    } catch (SQLiteException e) {
                        Log.e(TAG, "SQLiteException! Update your database!");
                        return null;
                    }
                }
                return null;
            }

            /**
             * Check whether cell zone table exists
             */
            private boolean haveCellTables() {
                final String sql = "SELECT count(name) FROM sqlite_master WHERE type='table' AND name='cell_zone'";
                final Cursor c = mCatalog.rawQuery(sql, null);
                c.moveToFirst();
                if (!c.isAfterLast()) {
                    if (c.getLong(0) == 0) {
                        c.close();
                        return false;
                    }
                }
                c.close();
                return true;
            }

            @Override
            protected void onPostExecute(Location result) {
                if (result == null) {
                    Log.w(TAG, "Location was null");
                    return;
                }

                if (plausibleLocationUpdate(result)) {
                    Log.d(TAG, "Broadcasting location" + result.toString());
                    setLastLocation(result);
                    setLastFix(System.currentTimeMillis());
                    mListener.onLocationReceived(result);
                }
            }
        }.execute(params);
    }

    private static class LocationQueryParams {
        ArrayList<String> wifiList;
        List<Cell> cellsList;

        LocationQueryParams(ArrayList<String> wifiList, List<Cell> cellsList) {
            this.wifiList = wifiList;
            this.cellsList = cellsList;
        }
    }
}