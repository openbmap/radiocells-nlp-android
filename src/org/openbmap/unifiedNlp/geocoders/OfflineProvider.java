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
package org.openbmap.unifiedNlp.geocoders;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
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

import org.openbmap.unifiedNlp.utils.SsidBlackList;
import org.openbmap.unifiedNlp.Preferences;
import org.openbmap.unifiedNlp.models.Cell;

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
    // Assumed ratio between maximum and typical range
    public static final int TYPICAL_RANGE_FACTOR = 7;
    public static final String BLACKLIST_SUBDIR = "blacklists";
    public static final String DEFAULT_SSID_BLOCK_FILE = "default_ssid.xml";
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

    /**
     * Blacklist to filter out well-known mobile SSIDs.
     */
    private SsidBlackList mSsidBlackList;

    private Context context;

    public OfflineProvider(final Context context, final ILocationCallback listener) {
        final String mBlacklistPath = context.getExternalFilesDir(null).getAbsolutePath() + File.separator + BLACKLIST_SUBDIR;
        mListener = listener;
        this.context = context;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.VAL_CATALOG_FILE).equals(Preferences.CATALOG_NONE)) {
            Log.e(TAG, "Critical error: you chose offline provider, but didn't specify a offline catalog!");
        }
        // Open catalog database
        Log.d(TAG, String.format("Using blacklist in %s", mBlacklistPath));
        String path = prefs.getString(Preferences.KEY_DATA_FOLDER, context.getExternalFilesDir(null).getAbsolutePath())
                + File.separator + prefs.getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.VAL_CATALOG_FILE);

        try {
            mCatalog = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteCantOpenDatabaseException e) {
            Log.e(TAG, "Error opening database");
        }
        mSsidBlackList = new SsidBlackList();
        mSsidBlackList.openFile(mBlacklistPath + File.separator + DEFAULT_SSID_BLOCK_FILE, null);
        setLastFix(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void getLocation(final List<ScanResult> wifiList, final List<Cell> cellsList) {
        LocationQueryParams params = new LocationQueryParams(wifiList, cellsList);

        new AsyncTask<LocationQueryParams, Void, Location>() {
            private static final int WIFIS_MASK = 0x0f;        // mask for wifi flags
            private static final int CELLS_MASK = 0xf0;        // mask for cell flags
            private static final int EMPTY_WIFIS_QUERY = 0x01; // no wifis were passed
            private static final int EMPTY_CELLS_QUERY = 0x10; // no cells were passed
            private static final int WIFIS_NOT_FOUND = 0x02;   // none of the wifis in the list was found in the database
            private static final int CELLS_NOT_FOUND = 0x20;   // none of the cells in the list was found in the database
            private static final int WIFIS_MATCH = 0x04;       // matching wifis were found
            private static final int CELLS_MATCH = 0x40;       // matching cells were found
            private static final int WIFI_DATABASE_NA = 0x08; // the database contains no wifi data
            private static final int CELLS_DATABASE_NA = 0x80; // the database contains no cell data

            private int state;

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
            @SuppressLint("DefaultLocale")
            @Override
            protected Location doInBackground(LocationQueryParams... params) {
                long now = SystemClock.elapsedRealtime();
                if (params == null) {
                    throw new IllegalArgumentException("Wifi list was null");
                }

                if (prefs.getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.CATALOG_NONE).equals(Preferences.CATALOG_NONE)) {
                    throw new IllegalArgumentException("No catalog database was specified");
                }

                List<ScanResult> wifiListRaw = params[0].wifiList;
                List<Cell> cellsListRaw = params[0].cellsList;
                HashMap<String, ScanResult> wifiList = new HashMap<>();
                List<Cell> cellsList = new ArrayList<>();
                HashMap<String, Location> locations = new HashMap<>();
                String[] resultIds = new String[0];
                ArrayList<String> cellResults = new ArrayList<>();
                Location result = null;

                if (wifiListRaw != null) {
                    // Generates a list of wifis from scan results
                    for (ScanResult r : wifiListRaw) {
                        long age = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                            // determine age (elapsedRealtime is in milliseconds, timestamp is in microseconds)
                            age = now - (r.timestamp / 1000);
                        /*
                         * Any filtering of scan results can be done here. Examples include:
                		 * empty or bogus BSSIDs, SSIDs with "_nomap" suffix, blacklisted wifis
                		 */
                        if (r.BSSID == null)
                            Log.w(TAG, "skipping wifi with empty BSSID");
                        else if (r.SSID.endsWith("_nomap")) {
                            // BSSID with _nomap suffix, user does not want it to be mapped or used for geolocation
                        } else if (mSsidBlackList.contains(r.SSID)) {
                            Log.w(TAG, String.format("SSID '%s' is blacklisted, skipping", r.SSID));
                        } else {
                            if (age >= 2000)
                                Log.w(TAG, String.format("wifi %s is stale (%d ms), using it anyway", r.BSSID, age));
                            // wifi is OK to use for geolocation, add it to list
                            wifiList.put(r.BSSID.replace(":", "").toUpperCase(), r);
                        }
                    }
                    Log.i(TAG, "Using " + wifiList.size() + " wifis for geolocation");
                } else {
                    Log.i(TAG, "No wifis supplied for geolocation");
                }

                String[] wifiQueryArgs = wifiList.keySet().toArray(new String[0]);

                if (cellsListRaw != null) {
                    for (Cell r : cellsListRaw) {
                        Log.d(TAG, "Evaluating " + r.toString());
                		/*
                		 * Filtering of cells happens here. This is typically the case for neighboring
                		 * cells in UMTS or LTE networks, which are only identified by their PSC or PCI
                		 * (and are thus useless for lookup). Other filters can be added, such as
                		 * filtering out cells with bogus data or comparing against a blacklist of
                		 * "cells on wheels" (whose location can change).
                		 */
                        if ((r.mcc <= 0) || (r.area <= 0) || (r.cellId <= 0)
                                || (r.mcc == Integer.MAX_VALUE) || ("".equals(r.mnc)) || (r.area == Integer.MAX_VALUE) || (r.cellId == Integer.MAX_VALUE)) {
                            Log.i(TAG, String.format("Cell %s has incomplete data, skipping", r.toString()));
                        } else {
                            cellsList.add(r);
                        }
                    }
                }

                state &= ~WIFIS_MASK & ~CELLS_MASK;
                if (wifiQueryArgs.length < 1) {
                    Log.i(TAG, "Query contained no bssids");
                    state |= EMPTY_WIFIS_QUERY;
                }

                if (cellsList.isEmpty()) {
                    Log.w(TAG, "Query contained no cell infos");
                    state |= EMPTY_CELLS_QUERY;
                }

                if ((state & (EMPTY_WIFIS_QUERY | EMPTY_CELLS_QUERY)) != (EMPTY_WIFIS_QUERY | EMPTY_CELLS_QUERY)) {
                    Cursor c;

                    if ((state & EMPTY_WIFIS_QUERY) == 0) {
                        Log.d(TAG, "Looking up wifis");
                        if (!hasWifiTables()) {
                            Log.w(TAG, "Wifi tables not available. Check your database");
                            state |= WIFI_DATABASE_NA;
                        }
                        String whereClause = "";
                        for (String k : wifiQueryArgs) {
                            if (whereClause.length() > 1) {
                                whereClause += " OR ";
                            }
                            whereClause += " bssid = ?";
                        }
                        final String wifiSql = "SELECT latitude, longitude, bssid FROM wifi_zone WHERE " + whereClause;
                        //Log.d(TAG, sql);
                        try {
                            c = mCatalog.rawQuery(wifiSql, wifiQueryArgs);
                            boolean zero = c.getCount() == 0;
                            Log.i(TAG, String.format("Found %d known wifis", c.getCount()));
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
                                locations.put(c.getString(2), location);
                                state |= WIFIS_MATCH;
                            }
                            c.close();
                            
                            if(zero) {
                                c = mCatalog.rawQuery("SELECT count(0) FROM wifi_zone",
                                                    new String[0]);
                                c.moveToFirst();
                                c.close();
                            }
                            
                        } catch (SQLiteException e) {
                            Log.e(TAG, "SQLiteException! Update your database!");
                            state |= WIFI_DATABASE_NA;
                        }
                        if ((state & WIFIS_MATCH) != WIFIS_MATCH) {
                            state |= WIFIS_NOT_FOUND;
                        }
                    }

                    if ((state & EMPTY_CELLS_QUERY) == 0) {
                        Log.d(TAG, "Looking up cells");
                        if (!hasCellTables()) {
                            Log.w(TAG, "Cell tables not available. Check your database");
                            state |= CELLS_DATABASE_NA;
                        }

                        String whereClause = "";
                        List<String> cellQueryArgs = new ArrayList<>();
                        for (Cell k : cellsList) {
                            if (whereClause.length() > 1) {
                                whereClause += " OR ";
                            }
                            Log.d(TAG, "Using " + k.toString());
                            whereClause += " (cid = ? AND mcc = ? AND mnc = ? AND area = ?)";
                            cellQueryArgs.add(String.valueOf(k.cellId));
                            cellQueryArgs.add(String.valueOf(k.mcc));
                            cellQueryArgs.add(k.mnc);
                            cellQueryArgs.add(String.valueOf(k.area));
                        }
                        // Ignore the cell technology for the time being, using cell technology causes problems when cell supports different protocols, e.g.
                        // UMTS and HSUPA and HSUPA+
                        // final String cellSql = "SELECT AVG(latitude), AVG(longitude) FROM cell_zone WHERE cid = ? AND mcc = ? AND mnc = ? AND area = ? and technology = ?";
                        String cellSql = "SELECT AVG(latitude), AVG(longitude), mcc, mnc, area, cid FROM cell_zone WHERE " + whereClause + " GROUP BY mcc, mnc, area, cid";
                        try {
                            c = mCatalog.rawQuery(cellSql, cellQueryArgs.toArray(new String[0]));
                            Log.i(TAG, String.format("Found %d known cells", c.getCount()));
                            if (c.getCount() == 0) {
                                c.close();
                                
                                whereClause = "";
                                cellQueryArgs = new ArrayList<>();
                                for (Cell k : cellsList) {
                                    if (whereClause.length() > 1) {
                                        whereClause += " OR ";
                                    }
                                    Log.d(TAG, "Using " + k.toString());
                                    whereClause += " (cid = ? AND mcc = ? AND mnc = ? AND area = ?)";
                                    cellQueryArgs.add(String.valueOf(k.cellId));
                                    cellQueryArgs.add(String.valueOf(k.mcc));
                                    cellQueryArgs.add(String.valueOf(Integer.valueOf(k.mnc)));
                                    cellQueryArgs.add(String.valueOf(k.area));
                                }
                                cellSql = "SELECT AVG(latitude), AVG(longitude), mcc, mnc, area, cid FROM cell_zone WHERE " + whereClause + " GROUP BY mcc, mnc, area, cid";
                                c = mCatalog.rawQuery(cellSql, cellQueryArgs.toArray(new String[0]));
                                Log.i(TAG, String.format("Found %d known cells", c.getCount()));
                            }
                            
                            for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                                Location location = new Location(TAG);
                                location.setLatitude(c.getDouble(0));
                                location.setLongitude(c.getDouble(1));
                                location.setAccuracy(0); // or DEFAULT_CELL_ACCURACY?
                                location.setTime(System.currentTimeMillis());
                                Bundle b = new Bundle();
                                b.putString("source", "cells");
                                b.putString("cell", c.getInt(2) + "|" + c.getInt(3) + "|" + c.getInt(4) + "|" + c.getInt(5));
                                location.setExtras(b);
                                locations.put(c.getInt(2) + "|" + c.getInt(3) + "|" + c.getInt(4) + "|" + c.getInt(5), location);
                                cellResults.add(c.getInt(2) + "|" + c.getInt(3) + "|" + c.getInt(4) + "|" + c.getInt(5));
                                state |= CELLS_MATCH;
                            }
                            c.close();
                                                        
                            if ((state & CELLS_MATCH) != CELLS_MATCH) {
                                state |= CELLS_NOT_FOUND;
                            }
                        } catch (SQLiteException e) {
                            Log.e(TAG, "SQLiteException! Update your database!");
                            state |= CELLS_DATABASE_NA;
                        }
                    }

                    resultIds = locations.keySet().toArray(new String[0]);

                    if (resultIds.length == 0) {
                        return null;
                    } else if (resultIds.length == 1) {
                        // We have just one location, pass it
                        result = locations.get(resultIds[0]);
                        if (resultIds[0].contains("|"))
                            // the only result is a cell, assume default
                            result.setAccuracy(DEFAULT_CELL_ACCURACY);
                        else
                            // the only result is a wifi, estimate accuracy based on RSSI
                            result.setAccuracy(getWifiRxDist(wifiList.get(resultIds[0]).level) / 10);
                        return result;
                    } else {
                    	/*
                    	 * Penalize outliers (which may be happen if a transmitter has moved and the
                    	 * database still has the old location, or a mix of old and new location): Walk
                    	 * through the array, calculating distances between each possible pair of
                    	 * locations, subtracting the presumed distance from the receiver, and storing
                    	 * the mean square of any positive distance. This is the presumed variance (i.e.
                    	 * standard deviation, or accuracy, squared).
                    	 * 
                    	 * This process does not distinguish between cells and wifis, other than by
                    	 * determining ranges and distances in a different way. This is intentional, as
                    	 * even cell towers can move (or may just have an inaccurate position in the
                    	 * database).
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
                    	 * For now we are not considering the accuracy of the transmitter positions
                    	 * themselves, as we don't have these values (this would require an additional
                    	 * column in the wifi catalog).
                    	 */
                        for (int i = 0; i < resultIds.length; i++) {
                            // for now, assume static speed (20 m/s = 72 km/h)
                            // TODO get a better speed estimate
                            final float speed = 20.0f;

                            // RSSI-based distance
                            float rxdist =
                                    (wifiList.get(resultIds[i]) == null) ?
                                            DEFAULT_CELL_ACCURACY * TYPICAL_RANGE_FACTOR :
                                            getWifiRxDist(wifiList.get(resultIds[i]).level);

                            // distance penalty for stale wifis (supported only on Jellybean MR1 and higher)
                            // for cells this value is always zero (cell scans are always current)
                            float ageBasedDist = 0.0f;
                            // penalize stale entries (wifis only, supported only on Jellybean MR1 and higher)
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) && !resultIds[i].contains("|")) {
                                // elapsedRealtime is in milliseconds, timestamp is in microseconds
                                ageBasedDist = (now - (wifiList.get(resultIds[i]).timestamp / 1000)) * speed / 1000;
                            }

                            for (int j = i + 1; j < resultIds.length; j++) {
                                float[] distResults = new float[1];
                                float jAgeBasedDist = 0.0f;
                                Location.distanceBetween(locations.get(resultIds[i]).getLatitude(),
                                        locations.get(resultIds[i]).getLongitude(),
                                        locations.get(resultIds[j]).getLatitude(),
                                        locations.get(resultIds[j]).getLongitude(),
                                        distResults);

                                // subtract distance between device and each transmitter to get "disagreement"
                                if (wifiList.get(resultIds[j]) == null)
                                    distResults[0] -= rxdist + DEFAULT_CELL_ACCURACY * TYPICAL_RANGE_FACTOR;
                                else {
                                    distResults[0] -= rxdist + getWifiRxDist(wifiList.get(resultIds[j]).level);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                                        jAgeBasedDist = (now - (wifiList.get(resultIds[j]).timestamp / 1000)) * speed / 1000;
                                }
                    			
                    			/*
                    			 * Consider the distance traveled between the two locations. This avoids
                    			 * penalizing two locations that differ in time.
                    			 */
                                distResults[0] -= Math.abs(ageBasedDist - jAgeBasedDist);

                                // apply penalty only if disagreement is greater than zero
                                if (distResults[0] > 0) {
                                    // take the square of the distance
                                    distResults[0] *= distResults[0];

                                    // add to the penalty count for both locations
                                    locations.get(resultIds[i]).setAccuracy(locations.get(resultIds[i]).getAccuracy() + distResults[0]);
                                    locations.get(resultIds[j]).setAccuracy(locations.get(resultIds[j]).getAccuracy() + distResults[0]);
                                }
                            }
                            locations.get(resultIds[i]).setAccuracy(locations.get(resultIds[i]).getAccuracy() / (resultIds.length - 1));
                            // correct distance from transmitter to a realistic value
                            rxdist /= TYPICAL_RANGE_FACTOR;

                            Log.v(TAG, String.format("%s: disagreement = %.5f, rxdist = %.5f, age = %d ms, ageBasedDist = %.5f",
                                    resultIds[i],
                                    Math.sqrt(locations.get(resultIds[i]).getAccuracy()),
                                    rxdist,
                                    (wifiList.get(resultIds[i]) != null) ? (now - (wifiList.get(resultIds[i]).timestamp / 1000)) : 0,
                                    ageBasedDist));

                            // add additional error sources: RSSI-based (distance from transmitter) and age-based (distance traveled since)
                            locations.get(resultIds[i]).setAccuracy(locations.get(resultIds[i]).getAccuracy() + rxdist * rxdist + ageBasedDist * ageBasedDist);

                            if (i == 0)
                                result = locations.get(resultIds[i]);
                            else {
                                float k = result.getAccuracy() / (result.getAccuracy() + locations.get(resultIds[i]).getAccuracy());
                                result.setLatitude((1 - k) * result.getLatitude() + k * locations.get(resultIds[i]).getLatitude());
                                result.setLongitude((1 - k) * result.getLongitude() + k * locations.get(resultIds[i]).getLongitude());
                                result.setAccuracy((1 - k) * result.getAccuracy());
                            }
                        }

                        // finally, set actual accuracy (square root of the interim value)
                        result.setAccuracy((float) Math.sqrt(result.getAccuracy()));

                        // FIXME what do we want to reflect in results? transmitters we tried to look up, or only those which returned a location?
                        Bundle b = new Bundle();
                        if ((state & (WIFIS_MATCH | CELLS_MATCH)) == (WIFIS_MATCH | CELLS_MATCH)) {
                            b.putString("source", "cells; wifis");
                        } else if ((state & WIFIS_MATCH) != 0) {
                            b.putString("source", "wifis");
                        } else if ((state & CELLS_MATCH) != 0) {
                            b.putString("source", "cells");
                        }
                        if ((state & WIFIS_MATCH) != 0)
                            b.putStringArrayList("bssids", new ArrayList<>(Arrays.asList(wifiQueryArgs)));
                        if ((state & CELLS_MATCH) != 0)
                            b.putStringArrayList("cells", cellResults);
                        result.setExtras(b);
                        return result;
                    }
                } else {
                    return null;
                }
            }

            /**
             * Check whether cell zone table exists
             */
            private boolean hasCellTables() {
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

            /**
             * Check whether wifi zone table exists
             */
            private boolean hasWifiTables() {
                final String sql = "SELECT count(name) FROM sqlite_master WHERE type='table' AND name='wifi_zone'";
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
                } else {
                    Log.i(TAG, "Strange location, ignoring");
                }
            }
        }.execute(params);
    }

    /**
     * @param rxlev Received signal strength (RSSI) in dBm
     * @return Upper boundary for the distance between transmitter and receiver in meters
     * @brief Obtains a wifi receiver's maximum distance from the transmitter based on signal strength.
     * <p/>
     * Distance is calculated based on the assumption that the signal level is -100 dBm at a distance of
     * 1000 m, and that the signal level will increase by 6 dBm as the distance is halved. This model
     * does not consider additional signal degradation caused by obstacles, thus real distances will
     * almost always be lower than the result of this function. This "worst-case" approach is
     * intentional and consumers should apply any corrections they deem appropriate.
     */
    private static float getWifiRxDist(int rxlev) {
        final int refRxlev = -100;
        final float refDist = 1000.0f;
        float factor = (float) Math.pow(2.0f, 6.0f / (refRxlev - rxlev));
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
