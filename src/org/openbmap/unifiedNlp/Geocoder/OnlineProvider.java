package org.openbmap.unifiedNlp.Geocoder;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbmap.unifiedNlp.services.JSONParser;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

public class OnlineProvider extends AbstractProvider implements ILocationProvider {

	private static final String TAG = OnlineProvider.class.getName();

	/**
	 * Geolocation service
	 */
	private static final String REQUEST_URL = "http://www.radiocells.org/geolocation/geolocate";

	/**
	 * Example query
	 */
	// //{"wifiAccessPoints":[{"macAddress":"000000000000","signalStrength":-54}]}
	
	/**
	 * Example reply
	 */
	// {"accuracy":30,"location":{"lng":10.088244781346,"lat":52.567062375353}}
	

	/**
	 * Callback function on results available
	 */
	private ILocationCallback mListener;
	
	public OnlineProvider(final Context ctx, final ILocationCallback listener) {
		this.mListener = listener;
	}
	
	/**
	 * Queries location for list of wifis
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void getLocation(ArrayList<String> wifiList) {
		new AsyncTask<ArrayList<String>, Void, JSONObject>() {

			@Override
			protected JSONObject doInBackground(ArrayList<String>... params) {
				if (params == null) {
					throw new IllegalArgumentException("Wifi list was null");
				}
				return loadJSON(REQUEST_URL, params[0]);
			} 

			@Override
			protected void onPostExecute(JSONObject jsonData) {
				if (jsonData == null) {
					Log.e(TAG, "JSON data was null");
					return;
				}

				try {
					Log.i(TAG, "JSON response: " +  jsonData.toString());
					JSONObject location = jsonData.getJSONObject("location");
					Double lat = location.getDouble("lat");
					Double lon = location.getDouble("lng");
					Long acc = jsonData.getLong("accuracy");
					Location result = new Location("org.openbmap.nlp");
					result.setLatitude(lat);
					result.setLongitude(lon);
					result.setAccuracy(acc);
					
					if (plausibleLocationUpdate(result)){
						setLastLocation(result);
						setLastFix(System.currentTimeMillis());
						mListener.onLocationReceived(result);
					}
				} catch (JSONException e) {
					Log.e(TAG, "Error parsing JSON:" + e.getMessage());
				}
			}

			public JSONObject loadJSON(String url, ArrayList<String> params2) {
				// Creating JSON Parser instance
				JSONParser jParser = new JSONParser();
				JSONObject params = buildParams(params2);
				JSONObject reply = jParser.getJSONFromUrl(url, params);
				return reply;
			}

			public JSONObject buildParams(ArrayList<String> params2) {
				JSONObject root = new JSONObject();
				try {
					JSONArray jsonArray = new JSONArray();

					for (String s : params2) {
						JSONObject object = new JSONObject();
						object.put("macAddress", s);
						object.put("signalStrength", "-54");
						jsonArray.put(object);
					}

					root.put("wifiAccessPoints", jsonArray);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				return root;
			}

		}.execute(wifiList);
	}
}