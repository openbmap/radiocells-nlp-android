package org.openbmap.unifiedNlp.Geocoder;

import java.util.ArrayList;

import android.location.Location;
import android.util.Log;

public abstract class AbstractProvider implements ILocationProvider {

	private static final String TAG = AbstractProvider.class.getSimpleName();
	private Location mLastLocation;
	private Long mLastFix;

	public abstract void getLocation(ArrayList<String> wifiList);

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
