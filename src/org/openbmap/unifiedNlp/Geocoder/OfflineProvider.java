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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.openbmap.unifiedNlp.Preferences;
import org.openbmap.unifiedNlp.services.Cell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class OfflineProvider extends AbstractProvider implements ILocationProvider {

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
				+ File.separator + prefs.getString(Preferences.KEY_WIFI_CATALOG_FILE, Preferences.VAL_WIFI_CATALOG_FILE);
		mCatalog = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void getLocation(ArrayList<String> wifiList, List<Cell> cellsList) {
		new AsyncTask<ArrayList<String>, Void, Location>() {

			@SuppressLint("DefaultLocale")
			@Override
			protected Location doInBackground(ArrayList<String>... params) {
				if (params == null) {
					throw new IllegalArgumentException("Wifi list was null");
				}

				if (prefs.getString(Preferences.KEY_WIFI_CATALOG_FILE, Preferences.WIFI_CATALOG_NONE).equals(Preferences.WIFI_CATALOG_NONE)) {
					throw new IllegalArgumentException("No catalog database was specified");
				}

				String whereClause = "";
				String[] whereArgs = params[0].toArray(new String[0]);
				
				if (whereArgs.length < 1) {
					Log.w(TAG, "Query contained no bssids, skipping update");
					return null;
				}
				
				for (String k: params[0]) {
					if (whereClause.length() > 1 ) { whereClause += " OR ";}
					whereClause += " bssid = ?";
				}
				for (int index = 0; index < whereArgs.length; index++){
					whereArgs[index] = whereArgs[index].replace(":", "").toUpperCase() ;
					//Log.i(TAG, "Sanitzed where "+ whereArgs[index]);
				}
				final String sql = "SELECT AVG(latitude), AVG(longitude) FROM wifi_zone WHERE " + whereClause;
				//Log.d(TAG, sql);

				Cursor c = mCatalog.rawQuery(sql, whereArgs);

				c.moveToFirst();
				if (!c.isAfterLast()) {
					Location result = new Location(TAG);
					result.setLatitude(c.getDouble(0));
					result.setLongitude(c.getDouble(1));
					result.setAccuracy(30);
					result.setTime(System.currentTimeMillis());
					Bundle b = new Bundle();
					b.putString("source", "wifis");
					b.putStringArrayList("bssids", params[0]);
					result.setExtras(b);
					c.close();
					return result;
				}
				c.close();
				return null;
			} 

			@Override
			protected void onPostExecute(Location result) {
				if (result == null) {
					Log.e(TAG, "Location was null");
					return;
				}
				Log.d(TAG, "Location received " +  result.toString());

				if (plausibleLocationUpdate(result)){
					setLastLocation(result);
					setLastFix(System.currentTimeMillis());
					mListener.onLocationReceived(result);
				}
			}
		}.execute(wifiList);
	}
}