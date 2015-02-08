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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.openbmap.unifiedNlp.R;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * Preferences activity.
 */
public class SettingsActivity extends PreferenceActivity {

	private static final String TAG = SettingsActivity.class.getSimpleName();

	private DownloadManager mDownloadManager;

	private BroadcastReceiver mReceiver =  null; 

	@SuppressLint("NewApi")
	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		initWifiCatalogFolderControl();

		// with versions >= GINGERBREAD use download manager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			initDownloadManager();
		}

		initWifiCatalogDownloadControl();
		//initActiveWifiCatalogControl(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_FOLDER, Preferences.VAL_DATA_FOLDER));

		initGpsSystemSettingsControl();
	}

	/**
	 * Initializes wifi catalog source preference
	 */
	@SuppressLint("NewApi")
	private void initWifiCatalogDownloadControl() {
		Preference pref = findPreference(Preferences.KEY_DOWNLOAD_WIFI_CATALOG);

		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				Log.v(TAG, "Downloading wifi catalog");
				
				// try to create directory		
				File folder = new File(Environment.getExternalStorageDirectory().getPath()
						+ PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(Preferences.KEY_DATA_FOLDER, Preferences.VAL_DATA_FOLDER));

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
					File target = new File(folder.getAbsolutePath() + File.separator + Preferences.WIFI_CATALOG_FILE);
					if (target.exists()) {
						Log.i(TAG, "Catalog file already exists. Overwriting..");
						target.delete();
					}

					try {
						// try to download to target. If target isn't below Environment.getExternalStorageDirectory(),
						// e.g. on second SD card a security exception is thrown
						Request request = new Request(Uri.parse(Preferences.WIFI_CATALOG_DOWNLOAD_URL));
						request.setDestinationUri(Uri.fromFile(target));
						long catalogDownloadId = mDownloadManager.enqueue(request);
					} catch (SecurityException sec) {
						// download to temp dir and try to move to target later
						Log.w(TAG, "Security exception, can't write to " + target + ", using " + SettingsActivity.this.getExternalCacheDir() 
								+ File.separator + Preferences.WIFI_CATALOG_FILE);
						File tempFile = new File(SettingsActivity.this.getExternalCacheDir() + File.separator + Preferences.WIFI_CATALOG_FILE);
						Request request = new Request(
								Uri.parse(Preferences.WIFI_CATALOG_DOWNLOAD_URL));
						request.setDestinationUri(Uri.fromFile(tempFile));
						mDownloadManager.enqueue(request);
					}
				} else {
					Toast.makeText(preference.getContext(), R.string.error_saving_file, Toast.LENGTH_SHORT).show();
				}
				return true;
			}
		});
	}

	/**
	 * Initialises download manager for GINGERBREAD and newer
	 */
	@SuppressLint("NewApi")
	private void initDownloadManager() {

		mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

		mReceiver = new BroadcastReceiver() {
			@SuppressLint("NewApi")
			@Override
			public void onReceive(final Context context, final Intent intent) {
				String action = intent.getAction();
				if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
					long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
					Query query = new Query();
					query.setFilterById(downloadId);
					Cursor c = mDownloadManager.query(query);
					if (c.moveToFirst()) {
						int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
						if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
							// we're not checking download id here, that is done in handleDownloads
							String uriString = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
							handleDownloads(uriString);
						}
					}
				}
			}
		};

		registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
	}

	/**
	 * Selects downloaded file either as wifi catalog / active map (based on file extension).
	 * @param file
	 */
	public final void handleDownloads(String file) {
		// get current file extension
		String[] filenameArray = file.split("\\.");
		String extension = "." + filenameArray[filenameArray.length - 1];

		// TODO verify on newer Android versions (>4.2)
		// replace prefix file:// in filename string
		file = file.replace("file://", "");

		if (extension.equals(Preferences.WIFI_CATALOG_FILE_EXTENSION)) {
			String catalogFolder = PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_DATA_FOLDER, Preferences.VAL_DATA_FOLDER);
			if (file.indexOf(SettingsActivity.this.getExternalCacheDir().getPath()) > -1 ) {
				// file has been downloaded to cache folder, so move..
				file = moveToFolder(file, catalogFolder); 
			}

			//initActiveWifiCatalogControl(catalogFolder);
			// handling wifi catalog files
			activateWifiCatalog(file);
		}
	}

	/**
	 * Initializes gps system preference.
	 * OnPreferenceClick system gps settings are displayed.
	 */
	private void initGpsSystemSettingsControl() {
		Preference pref = findPreference(Preferences.KEY_GPS_OSSETTINGS);
		pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(final Preference preference) {
				startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
				return true;
			}
		});
	}

	/**
	 * Initializes data directory preference.
	 * @return EditTextPreference with data directory.
	 */
	private EditTextPreference initWifiCatalogFolderControl() {
		EditTextPreference pref = (EditTextPreference) findPreference(Preferences.KEY_DATA_FOLDER);
		//pref.setSummary(PreferenceManager.getDefaultSharedPreferences(this).getString(Preferences.KEY_WIFI_CATALOG_FOLDER, Preferences.VAL_WIFI_CATALOG_FOLDER));
		pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				String pathName = "";

				// Ensure there is always a leading slash
				if (!((String) newValue).startsWith(File.separator)) {
					pathName = File.separator + (String) newValue;
				} else {
					pathName = (String) newValue;
				}

				// try to create directory
				File folder = new File(Environment.getExternalStorageDirectory() + pathName);
				boolean success = true;
				if (!folder.exists()) {
					success = folder.mkdirs();
				}
				if (!success) {
					Toast.makeText(getBaseContext(), R.string.error_create_folder + pathName, Toast.LENGTH_LONG).show();
					return false;
				}

				// Set summary with the directory value
				//preference.setSummary((String) pathName);

				// Re-populate available catalogs
				//initActiveWifiCatalogControl(pathName); 

				return true;
			}
		});
		return pref;
	}

	/**
	 * Populates the wifi catalog list preference by scanning catalog folder.
	 * @param catalogFolder Root folder for WIFI_CATALOG_SUBDIR
	 */
	/*
	private void initActiveWifiCatalogControl(final String catalogFolder) {

		String[] entries;
		String[] values;

		// Check for presence of database directory
		File folder = new File(Environment.getExternalStorageDirectory().getPath() +
				catalogFolder);

		if (folder.exists() && folder.canRead()) {
			// List each map file
			String[] dbFiles = folder.list(new FilenameFilter() {
				@Override
				public boolean accept(final File dir, final String filename) {
					return filename.endsWith(
							org.openbmap.Preferences.WIFI_CATALOG_FILE_EXTENSION);
				}
			});

			// Create array of values for each map file + one for not selected
			entries = new String[dbFiles.length + 1];
			values = new String[dbFiles.length + 1];

			// Create default / none entry
			entries[0] = getResources().getString(R.string.prefs_none);
			values[0] = org.openbmap.Preferences.VAL_WIFI_CATALOG_NONE;

			for (int i = 0; i < dbFiles.length; i++) {
				entries[i + 1] = dbFiles[i].substring(0, dbFiles[i].length() - org.openbmap.Preferences.WIFI_CATALOG_FILE_EXTENSION.length());
				values[i + 1] = dbFiles[i];
			}
		} else {
			// No wifi catalog found, populate values with just the default entry.
			entries = new String[] {getResources().getString(R.string.prefs_none)};
			values = new String[] {org.openbmap.Preferences.VAL_WIFI_CATALOG_NONE};
		}

		ListPreference lf = (ListPreference) findPreference(org.openbmap.Preferences.KEY_WIFI_CATALOG_FILE);
		lf.setEntries(entries);
		lf.setEntryValues(values);
	}
*/
	
	/**
	 * Changes catalog preference item to given filename.
	 * Helper method to activate wifi catalog following successful download
	 * @param absoluteFile absolute filename (including path)
	 */
	private void activateWifiCatalog(final String absoluteFile) {
		ListPreference lf = (ListPreference) findPreference(org.openbmap.unifiedNlp.Preferences.KEY_WIFI_CATALOG_FILE);

		// get filename
		String[] filenameArray = absoluteFile.split("\\/");
		String file = filenameArray[filenameArray.length - 1];

		CharSequence[] values = lf.getEntryValues();
		for (int i = 0; i < values.length; i++) {
			if (file.equals(values[i].toString())) {
				lf.setValueIndex(i);
			}
		}
	}

	/**
	 * Moves file to specified folder
	 * @param file
	 * @param folder
	 * @return new file name
	 */
	private String moveToFolder(String file, String folder) {
		// file path contains external cache dir, so we have to move..
		File source = new File(file);
		File destination = new File(Environment.getExternalStorageDirectory() + folder + File.separator + source.getName());
		Log.i(TAG, file + " stored in temp folder. Moving to " + destination.getAbsolutePath());

		try {
			moveFile(source, destination);
		}
		catch (IOException e) {
			Log.e(TAG, "I/O error while moving file");
		}
		return  destination.getAbsolutePath();
	}

	/**
	 * Moves file from source to destination
	 * @param src
	 * @param dst
	 * @throws IOException
	 */
	public static void moveFile(File src, File dst) throws IOException
	{
		copyFile(src, dst);
		src.delete();
	}

	/**
	 * Copies file to destination.
	 * This was needed to copy file from temp folder to SD card. A simple renameTo fails..
	 * see http://stackoverflow.com/questions/4770004/how-to-move-rename-file-from-internal-app-storage-to-external-storage-on-android
	 * @param src
	 * @param dst
	 * @throws IOException
	 */
	public static void copyFile(File src, File dst) throws IOException {
		FileChannel inChannel = new FileInputStream(src).getChannel();
		FileChannel outChannel = new FileOutputStream(dst).getChannel();
		try {
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} finally {
			if (inChannel != null) {
				inChannel.close();
			}

			if (outChannel != null) {
				outChannel.close();
			}
		}
	}

}
