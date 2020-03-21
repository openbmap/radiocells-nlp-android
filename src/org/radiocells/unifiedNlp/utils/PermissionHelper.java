/*
 * Copyright © 2013–2016 Michael von Glasow.
 *
 * This file is part of LSRN Tools.
 *
 * LSRN Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSRN Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSRN Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.radiocells.unifiedNlp.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;


/**
 * Provides helper methods to request permissions from components other than Activities.
 */
public class PermissionHelper {
    private static final String TAG = PermissionHelper.class.getSimpleName();


    /**
     * Requests permissions to be granted to this application.
     * <p>
     * This method is a wrapper around
     * {@link android.support.v4.app.ActivityCompat#requestPermissions(android.app.Activity, String[], int)}
     * which works in a similar way, except it can be called from non-activity contexts. When called, it
     * displays a notification with a customizable title and text. When the user taps the notification, an
     * activity is launched in which the user is prompted to allow or deny the request.
     * <p>
     * After the user has made a choice,
     * {@link android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback#onRequestPermissionsResult(int, String[], int[])}
     * is called, reporting whether the permissions were granted or not.
     *
     * @param context           The context from which the request was made. The context supplied must implement
     *                          {@link android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback} and will receive the
     *                          result of the operation.
     * @param permissions       The requested permissions
     * @param requestCode       Application specific request code to match with a result reported to
     *                          {@link android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback#onRequestPermissionsResult(int, String[], int[])}
     * @param notificationTitle The title for the notification
     * @param notificationText  The text for the notification
     * @param notificationIcon  Resource identifier for the notification icon
     */
    public static <T extends Context & ActivityCompat.OnRequestPermissionsResultCallback>
    void requestPermissions(final T context, String[] permissions, int requestCode, String notificationTitle, String notificationText, int notificationIcon) {
        ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                String[] outPermissions = resultData.getStringArray("permissions");
                int[] grantResults = resultData.getIntArray("grantResults");
                context.onRequestPermissionsResult(resultCode, outPermissions, grantResults);
            }
        };

        Intent permIntent = new Intent(context, PermissionRequestActivity.class);
        permIntent.putExtra("resultReceiver", resultReceiver);
        permIntent.putExtra("permissions", permissions);
        permIntent.putExtra("requestCode", requestCode);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(permIntent);

        PendingIntent permPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(notificationIcon)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setOngoing(true)
                //.setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setWhen(0)
                .setContentIntent(permPendingIntent)
                .setStyle(null);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "channel_id";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "radiocells.org location service",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channelId);
        }

        notificationManager.notify(requestCode, builder.build());
    }


    /**
     * A blank {@link Activity} on top of which permission request dialogs can be displayed
     */
    public static class PermissionRequestActivity extends AppCompatActivity {
        ResultReceiver resultReceiver;
        String[] permissions;
        int requestCode;

        /**
         * Called when the user has made a choice in the permission dialog.
         * <p>
         * This method wraps the responses in a {@link Bundle} and passes it to the {@link ResultReceiver}
         * specified in the {@link Intent} that started the activity, then closes the activity.
         */
        @Override
        public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
            Bundle resultData = new Bundle();
            resultData.putStringArray("permissions", permissions);
            resultData.putIntArray("grantResults", grantResults);
            resultReceiver.send(requestCode, resultData);
            finish();
        }


        /**
         * Called when the activity is started.
         * <p>
         * This method obtains several extras from the {@link Intent} that started the activity: the request
         * code, the requested permissions and the {@link ResultReceiver} which will receive the results.
         * After that, it issues the permission request.
         */
        @Override
        protected void onStart() {
            super.onStart();

            resultReceiver = this.getIntent().getParcelableExtra("\"resultReceiver\"");
            permissions = this.getIntent().getStringArrayExtra("permissions");
            requestCode = this.getIntent().getIntExtra("requestCode", 0);

            ActivityCompat.requestPermissions(this, permissions, requestCode);
        }
    }
}
