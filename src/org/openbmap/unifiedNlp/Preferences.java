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

/**
 * Stores settings keys and default values.
 * See preferences.xml for layout, strings-preferences.xml for text.
 */
public final class Preferences {
    public static final String KEY_VERSION_INFO = "version";
    public static final String KEY_DATA_FOLDER = "data.dir";

    /**
     * Operation mode: query online webservice / offline database
     */
    public static final String KEY_OPERATION_MODE = "mode";

    public static final String KEY_WAKE_UP_STRATEGY = "wake.up.strategy";
    
    public static final String KEY_SOURCE = "source";

    /**
     * Selected catalog file
     */
    public static final String KEY_OFFLINE_CATALOG_FILE = "data.ref_database";

    /**
     * Broadcast debug messages?
     */
    public static final String KEY_DEBUG_MESSAGES = "debug.messages";
    public static final String KEY_DEBUG_FILE = "debug.log.file";
    public static final String KEY_DEBUG_TO_FILE = "debug.to.file";
    public static final String KEY_DEBUG_FILE_LASTING_HOURS = "debug.file.lasting.hours";

	/*
     * Default values following ..
	 */

    public static final boolean VAL_DEBUG_MESSAGES = false;

    public static final String OPERATION_MODE_OFFLINE = "offline";

    public static final String OPERATION_MODE_ONLINE = "online";

    /**
     * Default mode operation offline might lead to crashes when no catalog file is available.
     * Otherwise it prevents bad surprises for the users (aka consuming online volume without explicit prior confirmation)
     */
    public static final String VAL_OPERATION_MODE = OPERATION_MODE_OFFLINE;

    public static final String SOURCE_CELLS  = "cells";

    public static final String SOURCE_WIFIS = "wifis";

    public static final String SOURCE_COMBINED = "combined";

    public static final String VAL_SOURCE = SOURCE_COMBINED;

    /**
     * Root folder for all additional data
     * Deprecated, use getExternalFilesDir(null).getAbsolutePath() instead
     * see https://github.com/wish7code/org.openbmap.unifiedNlpProvider/issues/10
     */
    //public static final String VAL_DATA_FOLDER = "/org.openbmap.unifiednlp";

    /**
     * Default catalog filename
     */
    public static final String VAL_CATALOG_FILE = "openbmap.sqlite";

    /**
     * Reference database not set
     */
    public static final String CATALOG_NONE = "none";

    /**
     * Dummy response, if server doesn't provide version info
     */
    public static final String SERVER_CATALOG_VERSION_NONE = "unknown";

    /**
     * File extension for wifi catalog
     */
    public static final String CATALOG_FILE_EXTENSION = ".sqlite";

    /**
     * URL, where wifi/cell catalog with openbmap's preprocessed wifi positions can be downloaded
     */
    public static final String PLANET_DOWNLOAD_URL = "https://radiocells.org/openbmap/static/openbmap.sqlite";

    /**
     * URL to check for newer catalog files
     */
    public static final String PLANET_VERSION_URL = "https://radiocells.org/default/database_version.json";

    /**
     * Server host name excluding final slash
     */
    public static final String SERVER_BASE = "https://radiocells.org";

    // 'Button id' catalog download preference
    public static final String KEY_CATALOGS_DIALOG = "catalogs_dialog";

    /**
     * Private dummy constructor
     */
    private Preferences() {

    }
}