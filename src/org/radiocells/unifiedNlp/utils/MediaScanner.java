/*
	Radiobeacon - Openbmap wifi and cell logger
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

package org.radiocells.unifiedNlp.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;

import java.io.File;

/**
 * Rescans given folder to refresh MTP cache
 * Otherwise files won't show up when connected to desktop pc
 * with Android > HONEYCOMB
 *
 * @link http://code.google.com/p/android/issues/detail?id=38282
 */
public class MediaScanner implements MediaScannerConnectionClient {

    private final MediaScannerConnection mScanner;
    private final File mFolder;

    public MediaScanner(final Context context, final File folder) {
        mFolder = folder;
        mScanner = new MediaScannerConnection(context, this);
        mScanner.connect();
    }

    /**
     * Scans folder for sqlite files, so they become visible
     */
    @Override
    public final void onMediaScannerConnected() {
        final File[] files = mFolder.listFiles();
        if (files != null)
            for (final File file : files) {
                mScanner.scanFile(file.getAbsolutePath(), null);
            }
    }

    @Override
    public final void onScanCompleted(final String path, final Uri uri) {
        mScanner.disconnect();
    }

}
