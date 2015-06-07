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
import android.util.Log;

import org.openbmap.unifiedNlp.services.Cell;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractProvider implements ILocationProvider {

	private static final String TAG = AbstractProvider.class.getSimpleName();
	private Location mLastLocation;
	private Long mLastFix;

	public abstract void getLocation(ArrayList<String> wifiList, List<Cell> cellsList);

	/**
	 * Returns last location update
	 * @return
	 */
	public Location getLastLocation() {
		return mLastLocation;
	}

	/**
	 * Saves last location
	 * @param location
	 */
	public void setLastLocation(Location location) {
		mLastLocation = location;
	}

	/**
	 * Set time of last location update
	 * @param millis
	 */
	public void setLastFix(Long millis) {
		mLastFix = millis;
	}

	/**
	 * Returns false if estimated speed is above 300 km/h
	 * @param newLoc
	 * @return
	 */
	public boolean plausibleLocationUpdate(Location newLoc) {
		if (mLastLocation == null) {
			return true;
		}

		if (newLoc.getLatitude() == 0 && newLoc.getLongitude() == 0) {
			return false;
		}

		// on recent signal, check for reasonable speed
		if (System.currentTimeMillis() - mLastFix < 10000) {
			Float kmAway = newLoc.distanceTo(mLastLocation)/1000.0f;
			Float speed = kmAway / ((System.currentTimeMillis()-mLastFix)/1000);

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
