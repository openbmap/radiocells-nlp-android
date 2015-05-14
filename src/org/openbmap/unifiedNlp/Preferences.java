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

import android.content.Context;

/**
 * Stores settings keys and default values.
 * See preferences.xml for layout, strings-preferences.xml for text.
 */
public final class Preferences {
	// Property names
	public static final String KEY_GPS_OSSETTINGS = "gps.ossettings";
	public static final String KEY_GPS_LOGGING_INTERVAL = "gps.interval";
	public static final String KEY_GPS_SAVE_COMPLETE_TRACK = "gps.save_track";
    public static final String KEY_VERSION_INFO = "version";
    public static final String KEY_DATA_FOLDER = "data.dir";
	public static final String KEY_OPERATION_MODE = "mode";

	/**
	 * Wifi catalog download button
	 */
	public static final String KEY_DOWNLOAD_WIFI_CATALOG = "data.download_wifi_catalog";
	
	/**
     * Selected wifi catalog file
     */
    public static final String KEY_WIFI_CATALOG_FILE = "data.ref_database";

    /**
     * Wifi catalog version
     */
    public static final String KEY_WIFI_CATALOG_VERSION = "data.ref_database_version";

	/**
	 * Broadcast debug messages?
	 */
	public static final String KEY_DEBUG_MESSAGES = "debug.messages";

	/**
	 * Openbmap user name
	 */
	//public static final String KEY_CREDENTIALS_USER = "credentials.user";
	
	/**
	 * Openbmap password
	 */
	//public static final String KEY_CREDENTIALS_PASSWORD = "credentials.password";
	
	/*
	 * Default values following ..
	 */
	
	public static final boolean VAL_DEBUG_MESSAGES = false;

	public static final String VAL_OPERATION_MODE = "offline";

    public static final String OPERATION_MODE_OFFLINE = "offline";

    public static final String OPERATION_MODE_ONLINE = "online";

    /**
	 * Root folder for all additional data
	 * Deprecated, use getExternalFilesDir(null).getAbsolutePath() instead
	 * see https://github.com/wish7code/org.openbmap.unifiedNlpProvider/issues/10
	 */
	//public static final String VAL_DATA_FOLDER = "/org.openbmap.unifiednlp";

	/**
	 * Default reference database filename
	 */
	public static final String VAL_WIFI_CATALOG_FILE = "openbmap.sqlite";

    /**
     * Reference database not set
     */
	public static final String WIFI_CATALOG_NONE = "none";


     /**
     * Reference database version not set
     */
    public static final String WIFI_CATALOG_VERSION_NONE = "not yet downloaded";
	
	/**
	 * File extension for wifi catalog
	 */
	public static final String WIFI_CATALOG_FILE_EXTENSION = ".sqlite";
	
	/**
	 * URL, where wifi catalog with openbmap's preprocessed wifi positions can be downloaded
	 */
	public static final String	WIFI_CATALOG_DOWNLOAD_URL = "http://www.radiocells.org/static/openbmap.sqlite";
	
	/**
	 * Filename catalog database
	 */
	public static final String	WIFI_CATALOG_FILE = "openbmap.sqlite";

    /**
     * Version information (increase on new version)
     */
    public static final String VERSION = "0.1.3";
	/**
	 * Private dummy constructor
	 */
	private Preferences() {
	
	}
}