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
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.microg.nlp.api.LocationBackendService;
import org.openbmap.unifiedNlp.Geocoder.ILocationCallback;
import org.openbmap.unifiedNlp.Geocoder.ILocationProvider;
import org.openbmap.unifiedNlp.Geocoder.OfflineProvider;
import org.openbmap.unifiedNlp.Geocoder.OnlineProvider;
import org.openbmap.unifiedNlp.Preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * If true, scan results are broadcasted for diagnostic app
     */
    private boolean mDebug;

    private Location last;

    /**
     * Phone state listeners to receive cell updates
     */
    private TelephonyManager mTelephonyManager;
    private PhoneStateListener mPhoneListener;

    /**
     * Wifi listeners to receive wifi updates
     */
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
        mTelephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);

        Log.d(TAG, "[Config] Debug Mode: " + PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DEBUG_MESSAGES, Preferences.VAL_DEBUG_MESSAGES));
        mDebug = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DEBUG_MESSAGES, Preferences.VAL_DEBUG_MESSAGES);

        Log.d(TAG, "[Config] Operation Mode: " + PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.VAL_OPERATION_MODE));
        mOnlineMode = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.VAL_OPERATION_MODE).equals("online");
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
                mGeocoder = new OnlineProvider(this, this, mDebug);
            } else {
                Log.i(TAG, "Using offline geocoder");
                mGeocoder = new OfflineProvider(this, this);
            }
        }

        if (wifiSupported()) {
            //Log.i(TAG, "Scanning wifis");
            scanning = wifiManager.startScan();

            /**
             * Processes scan results and sends query to geocoding service
             */if (this.mWifiScanResults == null) {
                this.mWifiScanResults = new WifiScanCallback() {

                    public void onWifiResultsAvailable() {
                        //Log.d(TAG, "Wifi results are available now.");

                        if (scanning) {
                            //Log.i(TAG, "Wifi scan results arrived..");
                            List<ScanResult> scans = wifiManager.getScanResults();
                            ArrayList<String> wifis = new ArrayList<String>();

                            if (scans != null) {
                                // Generates a list of wifis from scan results
                                for (ScanResult r : scans) {
                                    if ((r.BSSID != null) & !(r.SSID.endsWith("_nomap"))) {
                                        wifis.add(r.BSSID);
                                    }
                                }
                                Log.i(TAG, "Using " + wifis.size() + " wifis for geolocation");
                            } else {
                                // @see http://code.google.com/p/android/issues/detail?id=19078
                                Log.e(TAG, "WifiManager.getScanResults returned null");
                            }

                            if (System.currentTimeMillis() - queryTime > REFRESH_INTERVAL) {
                                Log.d(TAG, "Scanning wifis & cells");
                                queryTime = System.currentTimeMillis();
                                List<Cell> cells = getCells();
                                if (mGeocoder != null) {
                                    mGeocoder.getLocation(wifis, cells);
                                } else {
                                    Log.e(TAG, "Geocoder is null!");
                                }
                            } else {
                                Log.v(TAG, "Too frequent requests.. Skipping geolocation update..");
                            }
                        }
                        scanning = false;
                    }
                };
            }
        } else {
            Log.d(TAG, "Scanning cells only");
            if (System.currentTimeMillis() - queryTime > REFRESH_INTERVAL) {
                Log.d(TAG, "Scanning wifis & cells");
                queryTime = System.currentTimeMillis();
                List<Cell> cells = getCells();
                if (mGeocoder != null) {
                    mGeocoder.getLocation(new ArrayList<String>(), cells);
                } else {
                    Log.e(TAG, "Geocoder is null!");
                }
            } else {
                Log.v(TAG, "Too frequent requests.. Skipping geolocation update..");
            }
        }

        return null;
    }

    private List<Cell> getCells() {
        List<Cell> cells = new ArrayList<Cell>();

        List<CellInfo> cellsRawList = mTelephonyManager.getAllCellInfo();
        if (cellsRawList != null) {
            Log.d(TAG, "Found " + cellsRawList.size() + " cells");
        } else {
            Log.d(TAG, "No cell available (getAllCellInfo returned null)");
        }

        String operator = mTelephonyManager.getNetworkOperator();
        int mnc;
        int mcc;

        // getNetworkOperator() may return empty string, probably due to dropped connection
        if (operator != null && operator.length() > 3) {
            mcc = Integer.valueOf(operator.substring(0, 3));
            mnc = Integer.valueOf(operator.substring(3));
        } else {
            Log.e(TAG, "Error retrieving network operator, skipping cell");
            mcc = 0;
            mnc = 0;
        }

        if (cellsRawList != null) {
            for (CellInfo c : cellsRawList) {
                Cell cell = new Cell();
                if (c instanceof CellInfoGsm) {
                    Log.v(TAG, "GSM cell found");
                    cell.cellId = ((CellInfoGsm) c).getCellIdentity().getCid();
                    cell.area = ((CellInfoGsm) c).getCellIdentity().getLac();
                    //cell.mcc = ((CellInfoGsm)c).getCellIdentity().getMcc();
                    //cell.mnc = ((CellInfoGsm)c).getCellIdentity().getMnc();
                    cell.mcc = mcc;
                    cell.mnc = mnc;
                    cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                } else if (c instanceof CellInfoCdma) {
                    /*
                    object.put("cellId", ((CellInfoCdma)s).getCellIdentity().getBasestationId());
                    object.put("locationAreaCode", ((CellInfoCdma)s).getCellIdentity().getLac());
                    object.put("mobileCountryCode", ((CellInfoCdma)s).getCellIdentity().get());
                    object.put("mobileNetworkCode", ((CellInfoCdma)s).getCellIdentity().getMnc());*/
                    Log.wtf(TAG, "Using of CDMA cells for NLP not yet implemented");
                } else if (c instanceof CellInfoLte) {
                    Log.v(TAG, "LTE cell found");
                    cell.cellId = ((CellInfoLte) c).getCellIdentity().getCi();
                    cell.area = ((CellInfoLte) c).getCellIdentity().getTac();
                    //cell.mcc = ((CellInfoLte)c).getCellIdentity().getMcc();
                    //cell.mnc = ((CellInfoLte)c).getCellIdentity().getMnc();
                    cell.mcc = mcc;
                    cell.mnc = mnc;
                    cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                } else if (c instanceof CellInfoWcdma) {
                    Log.v(TAG, "CellInfoWcdma cell found");
                    cell.cellId = ((CellInfoWcdma) c).getCellIdentity().getCid();
                    cell.area = ((CellInfoWcdma) c).getCellIdentity().getLac();
                    //cell.mcc = ((CellInfoWcdma)c).getCellIdentity().getMcc();
                    //cell.mnc = ((CellInfoWcdma)c).getCellIdentity().getMnc();
                    cell.mcc = mcc;
                    cell.mnc = mnc;
                    cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                }
                cells.add(cell);
            }
        }
        return cells;
    }

    public boolean wifiSupported(){
        return ((wifiManager != null) && (wifiManager.isWifiEnabled() || ((Build.VERSION.SDK_INT >= 18) &&
                wifiManager.isScanAlwaysAvailable())));
    }
    @Override
    public Location onLocationReceived(Location location) {
        if (mDebug) {
            Log.d(TAG, "[UnifiedNlp Results]: " + location.getExtras().toString());

            if (last != null) {
                Log.d(TAG, "[UnifiedNlp Results]: Est. Speed " + Math.round(location.distanceTo(last) / (location.getTime() - last.getTime() / 1000 / 60)) + " km/h");
            }
        } else {
            Log.v(TAG, "Ignoring debug infos");
            location.setExtras(null);
        }
        report(location);

        last = location;
        return location;
    }

    @SuppressLint("InlinedApi")
    public static Map<Integer, String> TECHNOLOGY_MAP() {
        final Map<Integer, String> result = new HashMap<Integer, String>();
        result.put(TelephonyManager.NETWORK_TYPE_UNKNOWN, "NA");
        // GPRS shall be mapped to "GSM"
        result.put(TelephonyManager.NETWORK_TYPE_GPRS, "GSM");
        result.put(TelephonyManager.NETWORK_TYPE_EDGE, "EDGE");
        result.put(TelephonyManager.NETWORK_TYPE_UMTS, "UMTS");
        result.put(TelephonyManager.NETWORK_TYPE_CDMA, "CDMA");
        result.put(TelephonyManager.NETWORK_TYPE_EVDO_0, "EDVO_0");
        result.put(TelephonyManager.NETWORK_TYPE_EVDO_A, "EDVO_A");
        result.put(TelephonyManager.NETWORK_TYPE_1xRTT, "1xRTT");

        result.put(TelephonyManager.NETWORK_TYPE_HSDPA, "HSDPA");
        result.put(TelephonyManager.NETWORK_TYPE_HSUPA, "HSUPA");
        result.put(TelephonyManager.NETWORK_TYPE_HSPA, "HSPA");
        result.put(TelephonyManager.NETWORK_TYPE_IDEN, "IDEN");

        // add new network types not available in all revisions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            result.put(TelephonyManager.NETWORK_TYPE_EVDO_B, "EDV0_B");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            result.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");
            result.put(TelephonyManager.NETWORK_TYPE_EHRPD, "eHRPD");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            result.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPA+");
        }

        return Collections.unmodifiableMap(result);

    }
}