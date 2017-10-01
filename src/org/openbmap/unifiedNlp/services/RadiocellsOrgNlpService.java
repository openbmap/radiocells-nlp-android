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
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.microg.nlp.api.LocationBackendService;
import org.openbmap.unifiedNlp.Preferences;
import org.openbmap.unifiedNlp.geocoders.ILocationCallback;
import org.openbmap.unifiedNlp.geocoders.ILocationProvider;
import org.openbmap.unifiedNlp.geocoders.OfflineProvider;
import org.openbmap.unifiedNlp.geocoders.OnlineProvider;
import org.openbmap.unifiedNlp.models.Cell;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openbmap.unifiedNlp.utils.LogToFile;

import static org.openbmap.unifiedNlp.utils.LogToFile.appendLog;

@SuppressLint("NewApi")
public class RadiocellsOrgNlpService extends LocationBackendService implements ILocationCallback {
    private static final String TAG = RadiocellsOrgNlpService.class.getName();

    /**
     * Minimum interval between two queries
     * Please obey to this limit, you might kill the server otherwise
     */
    protected static final long ONLINE_REFRESH_INTERVAL = 5000;

    protected static final long OFFLINE_REFRESH_INTERVAL = 2000;

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
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private AlarmManager alarmManager;
    
    WifiLock mWifiLock;

    /**
     * Wifi listeners to receive wifi updates
     */
    private WifiManager wifiManager;

    private boolean scanning;
    
    private Calendar nextScanningAllowedFrom;

    private ILocationProvider mGeocoder;

    /**
     * Time of last geolocation request (millis)
     */
    private long lastFix;

    private WifiScanCallback mWifiScanResults;

    /**
     * Receives location updates as well as wifi scan result updates
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
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

    @SuppressLint("InlinedApi")
    public static Map<Integer, String> TECHNOLOGY_MAP() {
        final Map<Integer, String> result = new HashMap<>();
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
        result.put(TelephonyManager.NETWORK_TYPE_EVDO_B, "EDV0_B");
        result.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");
        result.put(TelephonyManager.NETWORK_TYPE_EHRPD, "eHRPD");
        result.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPA+");

        return Collections.unmodifiableMap(result);

    }

    @Override
    protected void onOpen() {
        Log.i(TAG, "Opening " + TAG);
        
        LogToFile.logFilePathname = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DEBUG_FILE,"");
        LogToFile.logToFileEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DEBUG_TO_FILE, false);
        LogToFile.logFileHoursOfLasting = Integer.valueOf(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DEBUG_FILE_LASTING_HOURS, "24"));
        
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "SCAN_LOCK");
        if(!mWifiLock.isHeld()){
            mWifiLock.acquire();
        }

        registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mTelephonyManager = ((TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE));
        powerManager = ((PowerManager) this.getSystemService(Context.POWER_SERVICE));
        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        
        Log.d(TAG, "[Config] Debug Mode: " + PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DEBUG_MESSAGES, Preferences.VAL_DEBUG_MESSAGES));
        mDebug = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Preferences.KEY_DEBUG_MESSAGES, Preferences.VAL_DEBUG_MESSAGES);

        Log.d(TAG, "[Config] Operation Mode: " + PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.VAL_OPERATION_MODE));
        mOnlineMode = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.VAL_OPERATION_MODE).equals("online");
    }
    
    @Override
    protected void onClose() {
        if (mWifiLock != null) {
            if (mWifiLock.isHeld()) {
                mWifiLock.release();
                mWifiLock = null;
            }
        }

        unregisterReceiver(mReceiver);        
        wifiManager = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        int ret = super.onStartCommand(intent, flags, startId);
        
        if (intent == null) {
            return ret;
        }
        
        if (null != intent.getAction()) switch (intent.getAction()) {
            case "org.openbmap.unifiedNlp.LOCATION_UPDATE_CELLS_ONLY":
                appendLog(TAG, "LOCATION_UPDATE_CELLS_ONLY:nextScanningAllowedFrom:" + nextScanningAllowedFrom);
                if (nextScanningAllowedFrom == null) {
                    return ret;
                }   
                nextScanningAllowedFrom = null;
                getLocationFromWifisAndCells(null);
                break;
            
            default:
                break;
        }
        return ret;
    }
        
    private PendingIntent getIntentToGetCellsOnly() {
        Intent intent = new Intent(getBaseContext(), RadiocellsOrgNlpService.class);
        intent.setAction("org.openbmap.unifiedNlp.LOCATION_UPDATE_CELLS_ONLY");
        return PendingIntent.getService(getBaseContext(),
                                              0,
                                              intent,
                                              PendingIntent.FLAG_CANCEL_CURRENT);
    }
    
    @Override
    protected Location update() {
        appendLog(TAG, "call update()");
        Calendar now = Calendar.getInstance();
                
        if ((nextScanningAllowedFrom != null) && (nextScanningAllowedFrom.after(now))) {
            Log.v(TAG, "Another scan is taking place");
            appendLog(TAG, "update():Another scan is taking place");
            return null;
        }
        appendLog(TAG, "update():mGeocoder:" + mGeocoder);
        if (mGeocoder == null) {
            if (mOnlineMode) {
                Log.i(TAG, "Using online geocoder");
                mGeocoder = new OnlineProvider(this, this, mDebug);
            } else {
                Log.i(TAG, "Using offline geocoder");
                mGeocoder = new OfflineProvider(this, this);
            }
        }
        appendLog(TAG, "update():isWifiSupported():" + isWifiSupported() + ":isWifisSourceSelected():" + isWifisSourceSelected() + ":" + isCellsSourceSelected());
        if (isWifiSupported() && isWifisSourceSelected()) {
            //Log.i(TAG, "Scanning wifis");
            appendLog(TAG, "update():nextScanningAllowedFrom:" + nextScanningAllowedFrom);
            if(nextScanningAllowedFrom == null) {
                scanning = wifiManager.startScan();
                nextScanningAllowedFrom = Calendar.getInstance();
                nextScanningAllowedFrom.add(Calendar.MINUTE, 1);    
            }
            final PendingIntent intentToCancel = getIntentToGetCellsOnly();
            wakeUp();
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                         SystemClock.elapsedRealtime() + 8000,
                                         intentToCancel);
            appendLog(TAG, "update():alarm set");
            /**
             * Processes scan results and sends query to geocoding service
             */
            if (mWifiScanResults == null) {
                mWifiScanResults = new WifiScanCallback() {

                    @Override
                    public void onWifiResultsAvailable() {
                        //Log.d(TAG, "Wifi results are available now.");
                        nextScanningAllowedFrom = null;
                        //timerHandler.removeCallbacks(timerRunnable);
                        intentToCancel.cancel();
                        alarmManager.cancel(intentToCancel);
                        if (scanning) {
                        	// TODO pass wifi signal strength to geocoder
                            //Log.i(TAG, "Wifi scan results arrived..");
                            List<ScanResult> scans = wifiManager.getScanResults();
                            
                            if (scans == null)
                                // @see http://code.google.com/p/android/issues/detail?id=19078
                            	Log.e(TAG, "WifiManager.getScanResults returned null");

                            getLocationFromWifisAndCells(scans);
                        }
                        scanning = false;
                    }
                };
            }
        } else if (isCellsSourceSelected()) {
            Log.d(TAG, "Scanning cells only");
            getLocationFromWifisAndCells(null);
        } else {
            Log.e(TAG, "Neigther cells nor wifis as source selected? Com'on..");
            appendLog(TAG, "update():Neigther cells nor wifis as source selected? Com'on..");
        }

        return null;
    }

    private void getLocationFromWifisAndCells(List<ScanResult> scans) {
        final long passed = System.currentTimeMillis() - lastFix;
        final boolean ok_online = (mOnlineMode && (passed > ONLINE_REFRESH_INTERVAL) || lastFix == 0);
        final boolean ok_offline = (!mOnlineMode && (passed > OFFLINE_REFRESH_INTERVAL) || lastFix == 0);
        appendLog(TAG, "getLocationFromWifisAndCells():ok_online:" + ok_online + ":ok_offline:" + ok_offline);
        if (ok_online || ok_offline) {
            Log.d(TAG, "Scanning wifis & cells");
            lastFix = System.currentTimeMillis();

            List<Cell> cells = new ArrayList<>() ;
            // if in combined mode also query cell information, otherwise pass empty list
            if (isCellsSourceSelected()) {
                cells = getCells();
            }
            appendLog(TAG, "getLocationFromWifisAndCells():mGeocoder:" + mGeocoder);
            if (mGeocoder != null) {
                mGeocoder.getLocation(scans, cells);
            } else {
                Log.e(TAG, "Geocoder is null!");
            }
        } else {
            Log.v(TAG, "Too frequent requests.. Skipping geolocation update..");
        }
    }
    
    /**
     * Checks whether user has selected wifis as geolocation source in settings
     * @return true if source wifis or combined has been chosen
     */
    private boolean isWifisSourceSelected() {
        final String source = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_SOURCE, Preferences.VAL_SOURCE);
        return (source.equals(Preferences.SOURCE_WIFIS)) || (source.equals(Preferences.SOURCE_COMBINED));
    }

    /**
     * Checks whether user has selected wifis as geolocation source in settings
     * @return true if source cells or combined has been chosen
     */
    private boolean isCellsSourceSelected() {
        final String source = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_SOURCE, Preferences.VAL_SOURCE);
        return (source.equals(Preferences.SOURCE_CELLS)) || (source.equals(Preferences.SOURCE_COMBINED));
    }

    private void wakeUp() {
        appendLog(TAG, "powerManager:" + powerManager);
        
        String wakeUpStrategy = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_WAKE_UP_STRATEGY, "nowakeup");

        appendLog(TAG, "wakeLock:wakeUpStrategy:" + wakeUpStrategy);
        
        if ("nowakeup".equals(wakeUpStrategy)) {
            return;
        }

        int powerLockID;
        
        if ("wakeupfull".equals(wakeUpStrategy)) {
            powerLockID = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
        } else {
            powerLockID = PowerManager.PARTIAL_WAKE_LOCK;
        }
        
        appendLog(TAG, "wakeLock:powerLockID:" + powerLockID);
        
        boolean isInUse;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            isInUse = powerManager.isInteractive();
        } else {
            isInUse = powerManager.isScreenOn();
        }
        
        if (!isInUse) {
            wakeLock = powerManager.newWakeLock(powerLockID, TAG);
            appendLog(TAG, "wakeLock:" + wakeLock + ":" + wakeLock.isHeld());
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }            
            appendLog(TAG, "wakeLock acquired");
        }
    }
    
    /**
     * Returns cells phone is currently connected to
     * @return list of cells
     */
    private List<Cell> getCells() {
        List<Cell> cells = new ArrayList<>();

        wakeUp();
        
        String operator = mTelephonyManager.getNetworkOperator();
        String mnc;
        int mcc;

        // getNetworkOperator() may return empty string, probably due to dropped connection
        if (operator != null && operator.length() > 3) {
            mcc = Integer.valueOf(operator.substring(0, 3));
            mnc = operator.substring(3);
        } else {
            Log.e(TAG, "Error retrieving network operator, skipping cell");
            mcc = 0;
            mnc = "";
        }
                        
        CellLocation cellLocation = mTelephonyManager.getCellLocation();
        
        appendLog(TAG, "getCells():cellLocation:" + cellLocation);
        
        if (cellLocation != null) {
        	if (cellLocation instanceof GsmCellLocation) {
        		Cell cell = new Cell();
        		cell.cellId = ((GsmCellLocation) cellLocation).getCid();
        		cell.area = ((GsmCellLocation) cellLocation).getLac();
        		cell.mcc = mcc;
        		cell.mnc = mnc;
        		cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                        appendLog(TAG, String.format("GsmCellLocation for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, ((GsmCellLocation) cellLocation).getPsc()));
        		Log.d(TAG, String.format("GsmCellLocation for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, ((GsmCellLocation) cellLocation).getPsc()));
        		cells.add(cell);
        	} else if (cellLocation instanceof CdmaCellLocation) {
                    appendLog(TAG, "getCells():cellLocation - CdmaCellLocation: Using CDMA cells for NLP is not yet implemented");
                    Log.w(TAG, "CdmaCellLocation: Using CDMA cells for NLP is not yet implemented");
        	} else {
                    appendLog(TAG, "getCells():cellLocation - Got a CellLocation of an unknown class");
                    Log.wtf(TAG, "Got a CellLocation of an unknown class");
                }
        } else {
            Log.d(TAG, "getCellLocation returned null");
        }
        
        List<NeighboringCellInfo> neighboringCells = mTelephonyManager.getNeighboringCellInfo();
        appendLog(TAG, "getCells():neighboringCells:" + neighboringCells);
        if (neighboringCells != null) {
            appendLog(TAG, "getCells():neighboringCells.size:" + neighboringCells.size());
            Log.d(TAG, "getNeighboringCellInfo found " + neighboringCells.size() + " cells");
        } else {
            Log.d(TAG, "getNeighboringCellInfo returned null");
        }
                
        if (neighboringCells != null) {
            for (NeighboringCellInfo c : neighboringCells) {
                Cell cell = new Cell();
                cell.cellId = c.getCid();
                cell.area = c.getLac();
        		cell.mcc = mcc;
        		cell.mnc = mnc;
        		cell.technology = TECHNOLOGY_MAP().get(c.getNetworkType());
                appendLog(TAG, String.format("getCells():NeighboringCellInfo for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, c.getPsc()));
                Log.d(TAG, String.format("NeighboringCellInfo for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, c.getPsc()));
                cells.add(cell);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        	List<CellInfo> cellsRawList = mTelephonyManager.getAllCellInfo();
                appendLog(TAG, "getCells():getAllCellInfo:cellsRawList:" + cellsRawList);
        	if (cellsRawList != null) {
                    appendLog(TAG, "getCells():cellsRawList.size:" + cellsRawList.size());
                    Log.d(TAG, "getAllCellInfo found " + cellsRawList.size() + " cells");
        	} else {
                    Log.d(TAG, "getAllCellInfo returned null");
        	}

        	if ((cellsRawList != null) && !cellsRawList.isEmpty()) {
                    processCellInfoList(cellsRawList, cells);
        	}
    	} else {
    		Log.d(TAG, "getAllCellInfo is not available (requires API 17)");
        }
        
        if (wakeLock !=  null) {
            try {
                appendLog(TAG, "getCells():releasing wakeLock");
                wakeLock.release();
            } catch (Throwable th) {
                // ignoring this exception, probably wakeLock was already released
            }
        }
        
        appendLog(TAG, "getCells():return cells.size: " + cells.size());
        return cells;
    }
        
    private void processCellInfoList(List<CellInfo> cellInfoList, List<Cell> cells) {
        for (CellInfo c : cellInfoList) {
            Cell cell = new Cell();
            if (c instanceof CellInfoGsm) {
                    //Log.v(TAG, "GSM cell found");
                    cell.cellId = ((CellInfoGsm) c).getCellIdentity().getCid();
                    cell.area = ((CellInfoGsm) c).getCellIdentity().getLac();
                    cell.mcc = ((CellInfoGsm)c).getCellIdentity().getMcc();
                    cell.mnc = String.valueOf(((CellInfoGsm)c).getCellIdentity().getMnc());
                    cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                    appendLog(TAG, String.format("CellInfoGsm for %d|%s|%d|%d|%s", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology));
                    Log.d(TAG, String.format("CellInfoGsm for %d|%s|%d|%d|%s", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology));
            } else if (c instanceof CellInfoCdma) {
                    /*
                    object.put("cellId", ((CellInfoCdma)s).getCellIdentity().getBasestationId());
                    object.put("locationAreaCode", ((CellInfoCdma)s).getCellIdentity().getLac());
                    object.put("mobileCountryCode", ((CellInfoCdma)s).getCellIdentity().get());
                    object.put("mobileNetworkCode", ((CellInfoCdma)s).getCellIdentity().getMnc());*/
                    appendLog(TAG, ":Using of CDMA cells for NLP not yet implemented");
                    Log.wtf(TAG, "Using of CDMA cells for NLP not yet implemented");
            } else if (c instanceof CellInfoLte) {
                    //Log.v(TAG, "LTE cell found");
                    cell.cellId = ((CellInfoLte) c).getCellIdentity().getCi();
                    cell.area = ((CellInfoLte) c).getCellIdentity().getTac();
                    cell.mcc = ((CellInfoLte)c).getCellIdentity().getMcc();
                    cell.mnc = String.valueOf(((CellInfoLte)c).getCellIdentity().getMnc());
                    cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                    appendLog(TAG, String.format("CellInfoLte for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, ((CellInfoLte)c).getCellIdentity().getPci()));
                    Log.d(TAG, String.format("CellInfoLte for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, ((CellInfoLte)c).getCellIdentity().getPci()));
            } else if (c instanceof CellInfoWcdma) {
                    //Log.v(TAG, "CellInfoWcdma cell found");
                    cell.cellId = ((CellInfoWcdma) c).getCellIdentity().getCid();
                    cell.area = ((CellInfoWcdma) c).getCellIdentity().getLac();
                    cell.mcc = ((CellInfoWcdma)c).getCellIdentity().getMcc();
                    cell.mnc = String.valueOf(((CellInfoWcdma)c).getCellIdentity().getMnc());
                    cell.technology = TECHNOLOGY_MAP().get(mTelephonyManager.getNetworkType());
                    appendLog(TAG, String.format("CellInfoWcdma for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, ((CellInfoWcdma) c).getCellIdentity().getPsc()));
                    Log.d(TAG, String.format("CellInfoWcdma for %d|%s|%d|%d|%s|%d", cell.mcc, cell.mnc, cell.area, cell.cellId, cell.technology, ((CellInfoWcdma) c).getCellIdentity().getPsc()));
            } else {
                appendLog(TAG, "CellInfo of unexpected type: " + c);
            }
            cells.add(cell);
        }
    }
    
    /**
     * Checks whether we can scan wifis
     * @return true if wifi is enabled or background scanning allowed
     */
    public boolean isWifiSupported() {
        return ((wifiManager != null) && (wifiManager.isWifiEnabled() || ((Build.VERSION.SDK_INT >= 18) &&
                wifiManager.isScanAlwaysAvailable())));
    }

    /**
     * Callback when new location is available
     * @param location
     * @return
     */
    @Override
    public Location onLocationReceived(Location location) {
        if (location == null) {
            Log.i(TAG, "Location was null, ignoring");
            appendLog(TAG, " Location is null");
            report(null);
            return null;
        }

        if (mDebug) {
            Log.d(TAG, "[UnifiedNlp Results]: " + location.getExtras().toString());

            if (last != null) {
                Log.d(TAG, "[UnifiedNlp Results]: Est. Speed " + Math.round(location.distanceTo(last) / (location.getTime() - last.getTime() / 1000 / 60)) + " km/h");
            }
        }
        
        report(location);

        last = location;
        return location;
    }
}
