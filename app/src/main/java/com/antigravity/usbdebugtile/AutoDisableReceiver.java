package com.antigravity.usbdebugtile;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

public class AutoDisableReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoDisableReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (AutoDisableManager.ACTION_AUTO_DISABLE.equals(action)) {
            Log.i(TAG, "Auto-disable alarm fired from AlarmManager!");
            AutoDisableManager.executeAutoDisable(context);
            return;
        }

        if (AutoDisableManager.ACTION_RE_ENABLE.equals(action)) {
            Log.i(TAG, "Re-enable action received from notification!");

            // 1. Dismiss security alert notification
            try {
                NotificationManagerCompat.from(context).cancel(AutoDisableManager.NOTIFICATION_ID_AUTO_DISABLED);
            } catch (Exception ignored) {
            }

            // 2. Verify permission
            if (!UsbDebugTileService.hasSecureSettingsPermission(context)) {
                Log.e(TAG, "Cannot re-enable ADB: WRITE_SECURE_SETTINGS permission missing!");
                return;
            }

            // 3. Re-enable USB Debugging
            try {
                Settings.Global.putInt(
                        context.getContentResolver(),
                        Settings.Global.ADB_ENABLED,
                        1
                );
                Log.i(TAG, "SUCCESS: USB Debugging re-enabled from notification action!");

                // 4. Update Quick Settings Tile
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    TileService.requestListeningState(
                            context,
                            new ComponentName(context, UsbDebugTileService.class)
                    );
                }

                // 5. Update state machine: if still disconnected, re-arms countdown timer
                AutoDisableManager.updateAutoDisableState(context);

            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while re-enabling ADB from notification", e);
            }
        }
    }
}
