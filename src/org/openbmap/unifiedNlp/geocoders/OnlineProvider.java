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

import android.content.Context;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbmap.unifiedNlp.models.Cell;
import org.openbmap.unifiedNlp.services.JSONParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OnlineProvider extends AbstractProvider implements ILocationProvider {

    private static final String TAG = OnlineProvider.class.getName();

    /**
     * Geolocation service
     */
    private static final String REQUEST_URL = "https://%s.radiocells.org/geolocate";

    /**
     * Query extra debug information from webservice
     */
    private final boolean mDebug;

    /**
     * Example wifi query JSON
     */
    // {"wifiAccessPoints":[{"macAddress":"000000000000","signalStrength":-54}]}

    /**
     * Example cell query JSON
     */
    // {"cellTowers": [{"cellId": 21532831, "locationAreaCode": 2862, "mobileCountryCode": 214, "mobileNetworkCode": 7}]}

    /**
     * Example JSON reply
     */
    // {"accuracy":30,"location":{"lng":10.088244781346,"lat":52.567062375353}}

    /**
     * Callback function on results available
     */
    private ILocationCallback mListener;

    private ArrayList<String> mWifiQuery;
    private ArrayList<String> mCellQuery;

    public OnlineProvider(final Context context, final ILocationCallback listener, boolean debug) {
        this.mListener = listener;
        mDebug = debug;
        setLastFix(System.currentTimeMillis());
    }

    /**
     * Queries location for list of wifis
     */
    @SuppressWarnings("unchecked")
    @Override
    public void getLocation(List<ScanResult> wifisList, List<Cell> cellsList) {
        ArrayList<String> wifis = new ArrayList<>();

        if (wifisList != null) {
            // Generates a list of wifis from scan results
            for (ScanResult r : wifisList) {
                if ((r.BSSID != null) & !(r.SSID.endsWith("_nomap"))) {
                    wifis.add(r.BSSID);
                }
            }
            Log.i(TAG, "Using " + wifis.size() + " wifis for geolocation");
        } else
            Log.i(TAG, "No wifis supplied for geolocation");
        
        new AsyncTask<Object, Void, JSONObject>() {

            @Override
            protected JSONObject doInBackground(Object... params) {
                if (params == null) {
                    throw new IllegalArgumentException("Wifi list was null");
                }
                mWifiQuery = (ArrayList<String>) params[0];
                mCellQuery = new ArrayList<>();
                for (Cell temp : (List<Cell>) params[1]) {
                    mCellQuery.add(temp.toString());
                }

                Random r = new Random();
                int idx = r.nextInt(3);

                final String balancer = String.format(REQUEST_URL, new String[]{"a","b","c"}[idx]);
                if (mDebug) {
                    Log.v(TAG, "Using balancer " + balancer);
                }
                return loadJSON(balancer, (ArrayList<String>) params[0], (List<Cell>) params[1]);
            }

            @Override
            protected void onPostExecute(JSONObject jsonData) {
                if (jsonData == null) {
                    Log.e(TAG, "JSON data was null");
                    return;
                }

                try {
                    Log.i(TAG, "JSON response: " + jsonData.toString());

                    if (jsonData.has("resultType") && !jsonData.getString("resultType").equals("error")) {
                        String source = jsonData.getString("source");
                        JSONObject location = jsonData.getJSONObject("location");
                        Double lat = location.getDouble("lat");
                        Double lon = location.getDouble("lng");
                        Long acc = jsonData.getLong("accuracy");
                        Location result = new Location(TAG);
                        result.setLatitude(lat);
                        result.setLongitude(lon);
                        result.setAccuracy(acc);
                        result.setTime(System.currentTimeMillis());

                        Bundle b = new Bundle();
                        b.putString("source", source);
                        b.putStringArrayList("bssids", mWifiQuery);
                        b.putStringArrayList("cells", mCellQuery);
                        result.setExtras(b);

                        if (plausibleLocationUpdate(result)) {
                            setLastLocation(result);
                            setLastFix(System.currentTimeMillis());
                            mListener.onLocationReceived(result);
                        } else {
                            Log.i(TAG, "Strange location, ignoring");
                        }
                    } else {
                        Log.w(TAG, "Server returned error, maybe not found / bad query?");
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON:" + e.getMessage());
                }
            }

            public JSONObject loadJSON(String url, ArrayList<String> wifiParams, List<Cell> cellParams) {
                // Creating JSON Parser instance
                JSONParser jParser = new JSONParser();
                JSONObject params = buildParams(wifiParams, cellParams);
                return jParser.getJSONFromUrl(url, params);
            }

            /**
             * Builds a JSON array with cell and wifi query
             * @param wifis ArrayList containing bssids
             * @param cells
             * @return JSON object
             */
            public JSONObject buildParams(ArrayList<String> wifis, List<Cell> cells) {
                JSONObject root = new JSONObject();
                try {
                    // add wifi objects
                    JSONArray jsonArray = new JSONArray();
                    if (mDebug) {
                        JSONObject object = new JSONObject();
                        object.put("debug", "1");
                        jsonArray.put(object);
                    }

                    for (String s : wifis) {
                        JSONObject object = new JSONObject();
                        object.put("macAddress", s);
                        object.put("signalStrength", "-54");
                        jsonArray.put(object);
                    }
                    if (jsonArray.length() > 0) {
                        root.put("wifiAccessPoints", jsonArray);
                    }

                    // add cell objects
                    jsonArray = new JSONArray();
                    for (Cell s : cells) {
                        JSONObject object = new JSONObject();
                        object.put("cellId", s.cellId);
                        object.put("locationAreaCode", s.area);
                        object.put("mobileCountryCode", s.mcc);
                        object.put("mobileNetworkCode", s.mnc);
                        jsonArray.put(object);
                    }
                    if (jsonArray.length() > 0) {
                        root.put("cellTowers", jsonArray);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.v(TAG, "Query param: " + root.toString());
                return root;
            }
        }.execute(wifis, cellsList);
    }
}