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
        boolean isLocationGranted = false;
        boolean isPhoneStateGranted = false;
        for (int i = 0; i < grantResults.length; i++)
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q))
                    isLocationGranted = true;
                else if (permissions[i].equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION) && (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q))
                    isLocationGranted = true;
                else if (permissions[i].equals(Manifest.permission.READ_PHONE_STATE))
                isPhoneStateGranted = true;
            }
        if (!isLocationGranted) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted");
        }
        if (!isPhoneStateGranted) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted");
        }
    }

}
