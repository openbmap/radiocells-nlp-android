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
package org.openbmap.unifiedNlp.services;

import java.util.ArrayList;
import java.util.List;

import org.microg.nlp.api.LocationBackendService;
import org.openbmap.unifiedNlp.Preferences;
import org.openbmap.unifiedNlp.Geocoder.ILocationCallback;
import org.openbmap.unifiedNlp.Geocoder.ILocationProvider;
import org.openbmap.unifiedNlp.Geocoder.OfflineProvider;
import org.openbmap.unifiedNlp.Geocoder.OnlineProvider;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

@SuppressLint("NewApi")
public class OpenbmapNlpService extends LocationBackendService implements ILocationCallback {
	private static final String TAG = OpenbmapNlpService.class.getName();

	/**
	 * Minimum interval between two queries
	 */
	protected static final long REFRESH_INTERVAL = 2000;

	/**
	 * If true, online geolocation service is used
	 */
	private boolean mOnlineMode;

	private WifiManager wifiManager;

	private boolean scanning;

	private ILocationProvider mGeocoder;

	private boolean running;

	/**
	 * Time of last geolocation request (millis)
	 */
	private long queryTime;

	private WifiScanCallback mWifiScanResults;


	/**
	 * Receives location updates as well as wifi scan result updates
	 */
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			//Log.d(TAG, "Received intent " + intent.getAction());
			// handling wifi scan results
			if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
				//Log.d(TAG, "Wifi manager signals wifi scan results.");
				// scan callback can be null after service has been stopped or another app has requested an update
				if (mWifiScanResults != null) {
					mWifiScanResults.onWifiResultsAvailable();
				} else {
					Log.i(TAG, "Scan Callback is null, skipping message");
				}
			} 
		}
	};

	@Override
	protected void onOpen() {
		Log.i(TAG, "Opening " + TAG);
		wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		Log.d(TAG, "Pref "+ PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.VAL_OPERATION_MODE));
		mOnlineMode =  PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.VAL_OPERATION_MODE).equals("online"); 		
		running = true;
	}

	@Override
	protected void onClose() {
		running = false;
		unregisterReceiver(mReceiver);
		/*
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
		 */
		wifiManager = null;
	}

	@Override
	protected Location update() {
		if (scanning) {
			Log.v(TAG, "Another scan is taking place");
			return null;
		}

		if (mGeocoder == null) {
			if (mOnlineMode) {
				Log.i(TAG, "Using online geocoder");
				mGeocoder = new OnlineProvider(this, this);
			} else {
				Log.i(TAG, "Using offline geocoder");
				mGeocoder = new OfflineProvider(this, this);
			}
		}

		if (wifiManager != null) {
			if (wifiManager.isWifiEnabled() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
					wifiManager.isScanAlwaysAvailable())) {
				//Log.i(TAG, "Scanning wifis");
				scanning = wifiManager.startScan();;

				if (this.mWifiScanResults == null) {
					/**
					 * Processes scan results and sends query to online service
					 */
					this.mWifiScanResults = new WifiScanCallback() {

						@SuppressWarnings("unchecked")
						public void onWifiResultsAvailable() {
							//Log.d(TAG, "Wifi results are available now.");

							if (scanning) {
								//Log.i(TAG, "Wifi scan results arrived..");
								List<ScanResult> scans = wifiManager.getScanResults();
								ArrayList<String> wifiList = new ArrayList<String>();

								if (scans != null) {
									// Generates a list of wifis from scan results
									for (ScanResult r : scans) {
										if (r.BSSID != null) {
											wifiList.add(r.BSSID);
										}
									}
									Log.i(TAG, "Using " + wifiList.size() + " wifis for geolocation");
								} else {
									// @see http://code.google.com/p/android/issues/detail?id=19078
									Log.e(TAG, "WifiManager.getScanResults returned null");
								}


								if (System.currentTimeMillis() - queryTime > REFRESH_INTERVAL) {
									queryTime = System.currentTimeMillis();
									mGeocoder.getLocation(wifiList);
								}
							} else {
								Log.v(TAG, "Too frequent requests.. Skipping geolocation update..");
							}
							scanning = false;
						}
					};
				}
			}
		}
		return null;
	}

	@Override
	public Location onLocationReceived(Location location) {
		report(location);
		return location;
	}
}