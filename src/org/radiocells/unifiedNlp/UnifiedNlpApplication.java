package org.radiocells.unifiedNlp;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class UnifiedNlpApplication extends Application implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = UnifiedNlpApplication.class.getSimpleName();

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        boolean isGranted = false;
        for (int i = 0; i < grantResults.length; i++)
            if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && (grantResults[i] == PackageManager.PERMISSION_GRANTED))
                isGranted = true;
        if (!isGranted) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
        }
    }

}
