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
package org.openbmap.unifiedNlp;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StrictMode;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.json.JSONObject;
import org.openbmap.unifiedNlp.utils.DirectoryChooserDialog;
import org.openbmap.unifiedNlp.utils.FileHelpers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Preferences activity.
 */
public class SettingsActivity extends PreferenceActivity {

    private static final String TAG = SettingsActivity.class.getSimpleName();

    private DownloadManager mDownloadManager;

    private BroadcastReceiver mReceiver = null;

    private boolean mIsDownloading;
    private WakeLock mWakeLock;
    private WifiLock mWifiLock;

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        initFolderChooser();

        // with versions >= GINGERBREAD use download manager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            initDownloadManager();
        }

        initCatalogDownloadButton();
        initCatalogChooser(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_FOLDER, this.getExternalFilesDir(null).getAbsolutePath()));

        initOperationModeChooser();
        initSourceChooser();

        Log.d(TAG, "Selected wifi catalog: " + getSelectedCatalog());
        if (getSelectedCatalog().equals(Preferences.CATALOG_NONE)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.question_download_catalog));
            builder.setTitle(getString(R.string.offline_catalog_n_a));
            // Add the buttons
            builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    downloadCatalog();
                    Toast.makeText(SettingsActivity.this, R.string.download_started, Toast.LENGTH_LONG).show();
                }
            });
            builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Preference pref = findPreference(Preferences.KEY_OPERATION_MODE);
                    pref.getEditor().putString(Preferences.KEY_OPERATION_MODE, Preferences.OPERATION_MODE_ONLINE).apply();
                    Toast.makeText(SettingsActivity.this, getString(R.string.online_mode), Toast.LENGTH_LONG).show();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        }

        Preference pref = findPreference(Preferences.KEY_VERSION_INFO);

        String version = "n/a";
        try {
            version = getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        pref.setSummary(version + " (" + readBuildInfo() + ")");
    }

    private String getCatalogVersion() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OFFLINE_CATALOG_VERSION, Preferences.CATALOG_VERSION_NONE);
    }

    private void setCatalogVersion(String version) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(Preferences.KEY_OFFLINE_CATALOG_VERSION, version).commit();
    }

    private String getSelectedCatalog() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OFFLINE_CATALOG_FILE, Preferences.CATALOG_NONE);
    }

    private String getSelectedOperationMode() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_OPERATION_MODE, Preferences.OPERATION_MODE_OFFLINE);
    }

    @Override
    protected final void onDestroy() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        if (mWifiLock != null && mWifiLock.isHeld()) {
            mWifiLock.release();
        }

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    private String getServerVersion(){
        Log.i(TAG, "Checking server version");
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
        HttpPost httppost = new HttpPost(Preferences.CATALOG_VERSION_URL);
        httppost.setHeader("Content-type", "application/json");

        InputStream inputStream = null;
        String result = null;
        try {
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            inputStream = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
            StringBuilder sb = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line + "\n");
            }
            JSONObject jObj = new JSONObject(sb.toString());
            return jObj.getString("version");
        } catch (Exception e) {
            Log.e(TAG, "Error getting server version");
        } finally {
            try{
                if (inputStream != null) {inputStream.close();}
            } catch (Exception squish) {
                return Preferences.SERVER_CATALOG_VERSION_NONE;
            }
        }
        return Preferences.SERVER_CATALOG_VERSION_NONE;
    }

    /**
     * Compares local and server wifi catalog versions
     * @param local Local version
     * @return true, if server has a newer version
     */
    private boolean isNewerVersionAvailable(String local){
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date currentDate = sdf.parse(local);
            Date versionDate = sdf.parse(getServerVersion());
            if (versionDate != null && currentDate != null && versionDate.after(currentDate)) {
                Log.i(TAG, "Newer version available");
                return  true;
            } else {
                Log.d(TAG, String.format("Already have the latest version ({0}) installed", local));
                return false;
            }
        } catch  (ParseException e) {
            Log.e(TAG, "Error parsing version");
            return false;
        }
    }

    /**
     * Initializes wifi catalog source preference
     */
    private void initCatalogDownloadButton() {
        Preference pref = findPreference(Preferences.KEY_DOWNLOAD_OFFLINE_CATALOG);
        pref.setSummary(getString(R.string.update_wifi_catalog_summary) + "\n\nLocal version: " + getCatalogVersion());
        pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                Log.v(TAG, "Downloading offline catalog");
                if (getCatalogVersion().equals(Preferences.CATALOG_VERSION_NONE) || isNewerVersionAvailable(getCatalogVersion())) {
                    Toast.makeText(SettingsActivity.this, R.string.download_started, Toast.LENGTH_LONG).show();

                    setCatalogVersion(Preferences.CATALOG_VERSION_NONE);
                    Preference pref = findPreference(Preferences.KEY_DOWNLOAD_OFFLINE_CATALOG);
                    pref.setSummary(getString(R.string.update_wifi_catalog_summary) + "\n\n" + getString(R.string.locale_version) + getCatalogVersion());

                    downloadCatalog();
                } else {
                    Toast.makeText(SettingsActivity.this, R.string.up_to_date_database, Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
    }

    /**
     * Starts catalog download
     */
    private void downloadCatalog() {
        if (mIsDownloading) {
            Toast.makeText(this, R.string.already_downloading, Toast.LENGTH_LONG).show();
            return;
        }

        if (mWakeLock == null || !mWakeLock.isHeld()) {
            Log.i(TAG, "Acquiring wakelock");
            PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "DownloadWakelock");
            mWakeLock.acquire();
        }

        if (mWifiLock == null || !mWifiLock.isHeld()) {
            Log.i(TAG, "Acquiring wifilock");
            WifiManager wm = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
            mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL , "DownloadWifiLock");
            mWifiLock.acquire();
        }

        // clean up bad download location from versions < 0.1.4
        File badFolder = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "org.openbmap.unifiednlp");
        File redundant = new File(badFolder, "openbmap.sqlite");
        if (badFolder.exists() && redundant.exists()) {
            Log.d(TAG, "Deleting bad downloads from 0.1.3:" + redundant.getAbsolutePath());
            redundant.delete();
            badFolder.delete();
        }

        // try to create directory
        File folder = new File(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_FOLDER, this.getExternalFilesDir(null).getAbsolutePath()));
        boolean folderAccessible = false;
        if (folder.exists() && folder.canWrite()) {
            Log.i(TAG, "Good, folder accessible: " + folder);
            folderAccessible = true;
        } else {
            Log.w(TAG, "Folder doesn't exits: " + folder);
        }

        if (!folder.exists()) {
            Log.i(TAG, "Creating folder " + folder);
            folderAccessible = folder.mkdirs();
        }
        if (folderAccessible) {
            File target = new File(folder.getAbsolutePath() + File.separator + Preferences.CATALOG_FILE);
            if (target.exists()) {
                Log.i(TAG, "Catalog file already exists. Overwriting..");
                target.delete();
            }

            try {
                Log.i(TAG, "Saving " + Preferences.CATALOG_DOWNLOAD_URL + " in " + target);
                // try to download to target. If target isn't below Environment.getExternalStorageDirectory(),
                // e.g. on second SD card a security exception is thrown
                Request request = new Request(Uri.parse(Preferences.CATALOG_DOWNLOAD_URL));
                request.setDestinationUri(Uri.fromFile(target));
                request.allowScanningByMediaScanner();
                long catalogDownloadId = mDownloadManager.enqueue(request);
                mIsDownloading = true;
            } catch (SecurityException sec) {
                // download to temp dir and try to move to target later
                Log.w(TAG, "Security exception, can't write to " + target + ", using " + this.getExternalCacheDir()
                        + File.separator + Preferences.CATALOG_FILE);
                File tempFile = new File(this.getExternalCacheDir() + File.separator + Preferences.CATALOG_FILE);
                Request request = new Request(Uri.parse(Preferences.CATALOG_DOWNLOAD_URL));
                request.setDestinationUri(Uri.fromFile(tempFile));
                request.allowScanningByMediaScanner();
                mDownloadManager.enqueue(request);
                mIsDownloading = true;
            }
        } else {
            Toast.makeText(this, R.string.error_saving_file, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Initialises download manager for GINGERBREAD and newer
     */
    private void initDownloadManager() {
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    if (mWakeLock != null && mWakeLock.isHeld()) {
                        Log.i(TAG, "Releasing wakelock");
                        mWakeLock.release();
                    }
                    if (mWifiLock != null && mWifiLock.isHeld()) {
                        Log.i(TAG, "Releasing wifilock");
                        mWifiLock.release();
                    }

                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    Query query = new Query();
                    query.setFilterById(downloadId);
                    Cursor c = mDownloadManager.query(query);
                    if (c.moveToFirst()) {
                        mIsDownloading = false;
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            // we're not checking download id here, that is done in handleDownloads
                            String uriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                            handleDownloads(uriString);
                        } else if (DownloadManager.STATUS_FAILED == c.getInt(columnIndex)) {
                            int columnReason = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                            int reason = c.getInt(columnReason);
                            Log.e(TAG, "Download failed: " + reason);
                            Toast.makeText(SettingsActivity.this, getString(R.string.download_failed) + " " + reason, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        };

        registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    /**
     * Selects downloaded file either as wifi catalog
     *
     * @param file
     */
    public final void handleDownloads(String file) {
        // get current file extension
        String[] filenameArray = file.split("\\.");
        String extension = "." + filenameArray[filenameArray.length - 1];

        // TODO verify on newer Android versions (>4.2)
        // replace prefix file:// in filename string
        file = file.replace("file://", "");

        if (extension.equals(Preferences.CATALOG_FILE_EXTENSION)) {
            String catalogFolder = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_FOLDER, this.getExternalFilesDir(null).getAbsolutePath());
            if (file.indexOf(SettingsActivity.this.getExternalCacheDir().getPath()) > -1) {
                // file has been downloaded to cache folder, so move..
                file = FileHelpers.moveToFolder(file, catalogFolder);
            }

            //initActiveWifiCatalogControl(catalogFolder);
            // handling wifi catalog files
            activateCatalog(file);
        }
    }

    /**
     * Initializes data directory preference.
     *
     * @return EditTextPreference with data directory.
     */
    private void initFolderChooser() {
        Preference button = (Preference) findPreference("data.dir");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private String mChosenDir = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getString(Preferences.KEY_DATA_FOLDER,
                    SettingsActivity.this.getExternalFilesDir(null).getAbsolutePath());

            private boolean mNewFolderEnabled = false;

            @Override
            public boolean onPreferenceClick(Preference arg0) {

                // Create DirectoryChooserDialog and register a callback
                DirectoryChooserDialog directoryChooserDialog =
                        new DirectoryChooserDialog(SettingsActivity.this, new DirectoryChooserDialog.ChosenDirectoryListener() {
                            @Override
                            public void onChosenDir(String chosenDir) {
                                mChosenDir = chosenDir;

                                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                                settings.edit().putString("data.dir", chosenDir).commit();

                                Toast.makeText(SettingsActivity.this, chosenDir, Toast.LENGTH_LONG).show();
                            }
                        });
                // Toggle new folder button enabling
                directoryChooserDialog.setNewFolderEnabled(mNewFolderEnabled);
                // Load directory chooser dialog for initial 'mChosenDir' directory.
                // The registered callback will be called upon final directory selection.
                directoryChooserDialog.chooseDirectory(mChosenDir);
                mNewFolderEnabled = !mNewFolderEnabled;
                return true;
            }
        });
    }

    /**
     * Populates the wifi catalog list preference by scanning catalog folder.
     *
     * @param catalogFolder Root folder for WIFI_CATALOG_SUBDIR
     */
    private void initCatalogChooser(final String catalogFolder) {

        String[] entries;
        String[] values;

        // Check for presence of database directory
        File folder = new File(catalogFolder);

        if (folder.exists() && folder.canRead()) {
            // List each map file
            String[] dbFiles = folder.list(new FilenameFilter() {
                @Override
                public boolean accept(final File dir, final String filename) {
                    return filename.endsWith(Preferences.CATALOG_FILE_EXTENSION);
                }
            });

            // Create array of values for each map file + one for not selected
            entries = new String[dbFiles.length + 1];
            values = new String[dbFiles.length + 1];

            // Create default / none entry
            entries[0] = getResources().getString(R.string.prefs_none);
            values[0] = Preferences.CATALOG_NONE;

            for (int i = 0; i < dbFiles.length; i++) {
                entries[i + 1] = dbFiles[i].substring(0, dbFiles[i].length() - Preferences.CATALOG_FILE_EXTENSION.length());
                values[i + 1] = dbFiles[i];
            }
        } else {
            // No wifi catalog found, populate values with just the default entry.
            entries = new String[]{getResources().getString(R.string.prefs_none)};
            values = new String[]{Preferences.CATALOG_NONE};
        }

        ListPreference lf = (ListPreference) findPreference(Preferences.KEY_OFFLINE_CATALOG_FILE);
        lf.setEntries(entries);
        lf.setEntryValues(values);
    }

    /**
     * Lets user select operation mode (online/offline)
     */
    private void initOperationModeChooser() {
        String[] entries = getResources().getStringArray(R.array.operation_mode_entries);
        String[] values = getResources().getStringArray(R.array.operation_mode_values);

        ListPreference lf = (ListPreference) findPreference(Preferences.KEY_OPERATION_MODE);
        lf.setEntries(entries);
        lf.setEntryValues(values);

        lf.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                String listValue = (String) newValue;
                Log.d(TAG, "New operation mode :" + listValue);
                if (listValue.equals(Preferences.OPERATION_MODE_OFFLINE) && getSelectedCatalog().equals(Preferences.CATALOG_NONE)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
                    builder.setMessage(getString(R.string.question_download_catalog));
                    builder.setTitle(getString(R.string.offline_catalog_n_a));
                    // Add the buttons
                    builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            downloadCatalog();
                            Toast.makeText(SettingsActivity.this, getString(R.string.download_started), Toast.LENGTH_LONG).show();
                        }
                    });
                    builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Toast.makeText(SettingsActivity.this, getString(R.string.warning_catalog_missing), Toast.LENGTH_LONG).show();
                        }
                    });

                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                return true;
            }
        });
    }

    /**
     * Lets user select source for geolocation (wifis, cells, combined)
     */
    private void initSourceChooser() {
        String[] entries = getResources().getStringArray(R.array.source_entries);
        String[] values = getResources().getStringArray(R.array.source_values);

        ListPreference lf = (ListPreference) findPreference(Preferences.KEY_SOURCE);
        lf.setEntries(entries);
        lf.setEntryValues(values);
    }

    /**
     * Changes catalog preference item to given filename.
     * Helper method to activate wifi catalog following successful download
     *
     * @param absoluteFile absolute filename (including path)
     */
    private void activateCatalog(final String absoluteFile) {

        ListPreference lf = (ListPreference) findPreference(Preferences.KEY_OFFLINE_CATALOG_FILE);
        // get filename
        String[] filenameArray = absoluteFile.split("\\/");
        String file = filenameArray[filenameArray.length - 1];

        CharSequence[] values = lf.getEntryValues();
        for (int i = 0; i < values.length; i++) {
            if (file.equals(values[i].toString())) {
                lf.setValueIndex(i);
            }
        }

        if (getSelectedOperationMode().equals(Preferences.OPERATION_MODE_ONLINE)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
            builder.setMessage(getString(R.string.question_activate_offline_mode));
            builder.setTitle(getString(R.string.download_completed));
            // Add the buttons
            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ListPreference pref = (ListPreference) findPreference(Preferences.KEY_OPERATION_MODE);
                    pref.setValue(Preferences.OPERATION_MODE_OFFLINE);
                    Toast.makeText(SettingsActivity.this, getString(R.string.using_offline_mode), Toast.LENGTH_LONG).show();
                }
            });
            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    //Toast.makeText(SettingsActivity.this, "Keep on using online  mode", Toast.LENGTH_LONG).show();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        }

        String version = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        setCatalogVersion(version);
        Preference pref = findPreference(Preferences.KEY_DOWNLOAD_OFFLINE_CATALOG);
        pref.setSummary(getString(R.string.update_wifi_catalog_summary) + "\n\n" + getString(R.string.local_version) + getCatalogVersion());
    }

    /**
     * Read build information from ressources
     *
     * @return build version
     */
    private final String readBuildInfo() {
        final InputStream buildInStream = getResources().openRawResource(R.raw.build);
        final ByteArrayOutputStream buildOutStream = new ByteArrayOutputStream();

        int i;

        try {
            i = buildInStream.read();
            while (i != -1) {
                buildOutStream.write(i);
                i = buildInStream.read();
            }

            buildInStream.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        return buildOutStream.toString();
        // use buildOutStream.toString() to get the data
    }

}
