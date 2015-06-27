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

package org.openbmap.unifiedNlp.utils;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileHelpers {

    private final static String TAG = FileHelpers.class.getSimpleName();

    /**
     * Moves file to specified folder
     *
     * @param file
     * @param folder
     * @return new file name
     */
    public static String moveToFolder(String file, String folder) {
        // file path contains external cache dir, so we have to move..
        File source = new File(file);
        File destination = new File(folder + File.separator + source.getName());
        Log.i(TAG, file + " stored in temp folder. Moving to " + destination.getAbsolutePath());

        try {
            moveFile(source, destination);
        } catch (IOException e) {
            Log.e(TAG, "I/O error while moving file");
        }
        return destination.getAbsolutePath();
    }

    /**
     * Copies file to destination.
     * This was needed to copy file from temp folder to SD card. A simple renameTo fails..
     * see http://stackoverflow.com/questions/4770004/how-to-move-rename-file-from-internal-app-storage-to-external-storage-on-android
     *
     * @param src Source file
     * @param dst Destination file
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
            outChannel.close();
        }
    }

    /**
     * Moves file from source to destination
     *
     * @param src Source file
     * @param dst Destination file
     * @throws IOException
     */
    public static void moveFile(File src, File dst) throws IOException {
        copyFile(src, dst);
        src.delete();
    }
}
