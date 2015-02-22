package org.openbmap.unifiedNlp.Geocoder;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbmap.unifiedNlp.services.Cell;
import org.openbmap.unifiedNlp.services.JSONParser;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.SignalStrength;
import android.util.Log;

public class OnlineProvider extends AbstractProvider implements ILocationProvider {

	private static final String TAG = OnlineProvider.class.getName();

	/**
	 * Geolocation service
	 */
	private static final String REQUEST_URL = "http://www.radiocells.org/geolocation/geolocate";

	/**
	 * Example wifi query
	 */
	// {"wifiAccessPoints":[{"macAddress":"000000000000","signalStrength":-54}]}

	/**
	 * Example cell query
	 */
	// {"cellTowers": [{"cellId": 21532831, "locationAreaCode": 2862, "mobileCountryCode": 214, "mobileNetworkCode": 7}]}

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
	public void getLocation(ArrayList<String> wifisList, List<Cell> cellsList) {
		new AsyncTask<Object, Void, JSONObject>() {

			@Override
			protected JSONObject doInBackground(Object... params) {
				if (params == null) {
					throw new IllegalArgumentException("Wifi list was null");
				}
				return loadJSON(REQUEST_URL, (ArrayList<String>)params[0], (List<Cell>)params[1]);
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

			public JSONObject loadJSON(String url, ArrayList<String> wifiParams, List<Cell> cellParams) {
				// Creating JSON Parser instance
				JSONParser jParser = new JSONParser();
				JSONObject params = buildParams(wifiParams, cellParams);
				JSONObject reply = jParser.getJSONFromUrl(url, params);
				return reply;
			}

			/**
			 * Builds a JSON array with cell and wifi query
			 * @param wifis ArrayList containing bssids
			 * @param cells 
			 * @return
			 */
			public JSONObject buildParams(ArrayList<String> wifis, List<Cell> cells) {
				JSONObject root = new JSONObject();
				try {
					// add wifi objects
					JSONArray jsonArray = new JSONArray();
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
						object.put("locationAreaCode",  s.lac);
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
				Log.d(TAG, "Query param: " + root.toString());
				return root;
			}
		}.execute(wifisList, cellsList);
	}
}