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

import android.location.Location;
import android.net.wifi.ScanResult;
import android.util.Log;

import org.openbmap.unifiedNlp.services.Cell;

import java.util.List;

public abstract class AbstractProvider implements ILocationProvider {

    private static final String TAG = AbstractProvider.class.getSimpleName();
    private Location mLastLocation;
    private Long mLastFix;

    public abstract void getLocation(List<ScanResult> wifiList, List<Cell> cellsList);

    /**
     * Returns last location update
     * @return last location
     */
    public Location getLastLocation() {
        return mLastLocation;
    }

    /**
     * Saves last location
     * @param location last location
     */
    public void setLastLocation(Location location) {
        mLastLocation = location;
    }

    /**
     * Set time of last location update
     * @param millis system time millis
     */
    public void setLastFix(Long millis) {
        mLastFix = millis;
    }

    /**
     * Checks whether location is plausible.
     * Location is considered unplausible if estimated speed is above 300 km/h
     * or location is null
     * @param location Location to test
     * @return true if location seems plausible
     */
    public boolean plausibleLocationUpdate(Location location) {

        if (location == null) {
            return false;
        }

        if (mLastLocation == null) {
            return true;
        }

        if (location.getLatitude() == 0 && location.getLongitude() == 0) {
            Log.wtf(TAG, "WTF, lat=0, lon=0? Welcome to the gulf of Guinea!");
            return false;
        }

        // on recent signal, check for reasonable speed
        if (System.currentTimeMillis() - mLastFix < 10000) {
            Float kmAway = location.distanceTo(mLastLocation) / 1000.0f;
            Float speed = kmAway / ((System.currentTimeMillis() - mLastFix) / 1000);

            if (speed >= 300.0) {
                Log.wtf(TAG, "WTF, are you traveling by plane? Estimated speed " + speed);
            }
            return speed < 300.0;
        } else {
            Log.v(TAG, "Can't validate location update, last signal too old");
            return true;
        }
    }
}
