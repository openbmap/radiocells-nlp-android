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
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import org.openbmap.unifiedNlp.Preferences;
import org.openbmap.unifiedNlp.services.Cell;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
    public void getLocation(final List<ScanResult> wifiList, final List<Cell> cellsList) {
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

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
			@SuppressLint("DefaultLocale")
            @Override
            protected Location doInBackground(LocationQueryParams... params) {
                if (params == null) {
                    throw new IllegalArgumentException("Wifi list was null");
                }

                if (prefs.getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.CATALOG_NONE).equals(Preferences.CATALOG_NONE)) {
                    throw new IllegalArgumentException("No catalog database was specified");
                }

                List<ScanResult> wifiListRaw = ((LocationQueryParams) params[0]).wifiList;
                HashMap<String, ScanResult> wifiList = new HashMap<String, ScanResult>();

                if (wifiListRaw != null) {
                	// Generates a list of wifis from scan results
                	for (ScanResult r : wifiListRaw) {
                		/*
                		 * Any filtering of scan results can be done here. Examples include:
                		 * empty or bogus BSSIDs, SSIDs with "_nomap" suffix, blacklisted wifis
                		 */
                		if (r.BSSID == null)
                			Log.w(TAG, "skipping wifi with empty BSSID");
                		else if (r.SSID.endsWith("_nomap")) {
                			// BSSID with _nomap suffix, user does not want it to be mapped or used for geolocation
                		} else
                			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                				// determine age (elapsedRealtime is in milliseconds, timestamp is in microseconds)
                				long age = SystemClock.elapsedRealtime() - (r.timestamp / 1000);
                				if (age >= 5000)
                					Log.w(TAG, String.format("wifi %s is stale (%d ms), using it anyway", r.BSSID, age));
                			}
                			// wifi is OK to use for geolocation, add it to list
                			wifiList.put(r.BSSID.replace(":", "").toUpperCase(), r);
                	}
                	Log.i(TAG, "Using " + wifiList.size() + " wifis for geolocation");
                } else
                	Log.i(TAG, "No wifis supplied for geolocation");


                String[] wifiQueryArgs = wifiList.keySet().toArray(new String[0]);
                HashMap<String, Location> wifiLocations = new HashMap<String, Location>();
                Location result = null;

                if (wifiQueryArgs.length < 1) {
                    Log.i(TAG, "Query contained no bssids");
                    state = EMPTY_WIFIS_QUERY;
                }

                if (state != EMPTY_WIFIS_QUERY) {
                    Log.d(TAG, "Trying wifi mode");
                    String whereClause = "";
                    for (String k : wifiQueryArgs) {
                        if (whereClause.length() > 1) {
                            whereClause += " OR ";
                        }
                        whereClause += " bssid = ?";
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
                    
                    String[] wifiResults = wifiLocations.keySet().toArray(new String[0]);
                    
                    if (wifiResults.length == 0) {
                        state = WIFIS_NOT_FOUND;
                        Log.i(TAG, "No known wifis found");
                    } else if (wifiResults.length == 1) {
                    	// We have just one location, pass it
                    	result = wifiLocations.get(wifiResults[0]);
                    	// FIXME DEFAULT_WIFI_ACCURACY is way too optimistic IMHO
                    	result.setAccuracy(DEFAULT_WIFI_ACCURACY);
                        Bundle b = new Bundle();
                        b.putString("source", "wifis");
                        b.putStringArrayList("bssids", new ArrayList<String>(Arrays.asList(wifiQueryArgs)));
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
                    	for (int i = 0; i < wifiResults.length; i++) {
                    		float rxdist = getWifiRxDist(wifiList.get(wifiResults[i]).level);
                    		// TODO evaluate distance from cells as well
                    		for (int j = i + 1; j < wifiResults.length; j++) {
                    			float[] distResults = new float[1];
                    			Location.distanceBetween(wifiLocations.get(wifiResults[i]).getLatitude(),
                    					wifiLocations.get(wifiResults[i]).getLongitude(),
                    					wifiLocations.get(wifiResults[j]).getLatitude(),
                    					wifiLocations.get(wifiResults[j]).getLongitude(),
                    					distResults);
                    			/*
                    			 * TODO instead of using raw distance, subtract the distance between the
                    			 * device and each transmitter from it (if device-transmitter distance
                    			 * is not known, assume a typical value). If the result is negative,
                    			 * assume zero instead.
                    			 */
                    			// subtract distance between device and each transmitter to get "disagreement"
                    			distResults[0] -= rxdist + getWifiRxDist(wifiList.get(wifiResults[j]).level);
                    			
                    			// apply penalty only if disagreement is greater than zero
                    			if (distResults[0] > 0) {
                    				// take the square of the distance
                    				distResults[0] *= distResults[0];

                    				// add to the penalty count for the locations of both wifis
                    				wifiLocations.get(wifiResults[i]).setAccuracy(wifiLocations.get(wifiResults[i]).getAccuracy() + distResults[0]);
                    				wifiLocations.get(wifiResults[j]).setAccuracy(wifiLocations.get(wifiResults[j]).getAccuracy() + distResults[0]);
                    			}
                    		}
                    		wifiLocations.get(wifiResults[i]).setAccuracy(wifiLocations.get(wifiResults[i]).getAccuracy() / (wifiResults.length - 1));
                    		// correct distance from transmitter to a realistic value
                    		rxdist /= 10;
                    		// add square of distance from transmitter (additional source of error)
                      		wifiLocations.get(wifiResults[i]).setAccuracy(wifiLocations.get(wifiResults[i]).getAccuracy() + rxdist * rxdist);
                      	                    		
                    		if (i == 0)
                    			result = wifiLocations.get(wifiResults[i]);
                    		else {
                    			float k = result.getAccuracy() / (result.getAccuracy() + wifiLocations.get(wifiResults[i]).getAccuracy());
                    			result.setLatitude((1 - k) * result.getLatitude() + k * wifiLocations.get(wifiResults[i]).getLatitude());
                    			result.setLongitude((1 - k) * result.getLongitude() + k * wifiLocations.get(wifiResults[i]).getLongitude());
                    			result.setAccuracy((1 - k) * result.getAccuracy());
                    		}
                    	}
                    	
                    	// finally, set actual accuracy (square root of the interim value)
                    	result.setAccuracy((float) Math.sqrt(result.getAccuracy()));
                        Bundle b = new Bundle();
                        b.putString("source", "wifis");
                        b.putStringArrayList("bssids", new ArrayList<String>(Arrays.asList(wifiQueryArgs)));
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
    
    /**
     * @brief Obtains a wifi receiver's maximum distance from the transmitter based on signal strength.
     * 
     * Distance is calculated based on the assumption that the signal level is -100 dBm at a distance of
     * 1000 m, and that the signal level will increase by 6 dBm as the distance is halved. This model
     * does not consider additional signal degradation caused by obstacles, thus real distances will
     * almost always be lower than the result of this function. This "worst-case" approach is
     * intentional and consumers should apply any corrections they deem appropriate.
     * 
     * @param rxlev Received signal strength (RSSI) in dBm
     * 
     * @return Upper boundary for the distance between transmitter and receiver in meters
     */
    private static float getWifiRxDist(int rxlev) {
    	final int refRxlev = -100;
    	final float refDist = 1000.0f;
    	float factor = (float) Math.pow(2, 6 / (refRxlev - rxlev));
    	return refDist * factor;
    }

    private static class LocationQueryParams {
    	List<ScanResult> wifiList;
        List<Cell> cellsList;

        LocationQueryParams(List<ScanResult> wifiList, List<Cell> cellsList) {
            this.wifiList = wifiList;
            this.cellsList = cellsList;
        }
    }
}