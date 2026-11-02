package com.antigravity.usbdebugtile;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;

public class AutoDisableReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoDisableReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AutoDisableManager.ACTION_AUTO_DISABLE.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "Auto-disable alarm triggered! Evaluating conditions...");

        boolean isEnabled = AutoDisableManager.isAutoDisableEnabled(context);
        if (!isEnabled) {
            Log.d(TAG, "Feature is disabled in settings. Aborting.");
            return;
        }

        boolean isConnected = AutoDisableManager.isUsbConnected(context);
        if (isConnected) {
            Log.d(TAG, "USB cable was reconnected before timeout elapsed. Aborting auto-disable.");
            AutoDisableManager.cancelTimeout(context);
            return;
        }

        boolean isAdbOn = UsbDebugTileService.isAdbEnabled(context);
        if (!isAdbOn) {
            Log.d(TAG, "ADB is already disabled. Nothing to do.");
            AutoDisableManager.cancelTimeout(context);
            return;
        }

        if (!UsbDebugTileService.hasSecureSettingsPermission(context)) {
            Log.e(TAG, "Cannot auto-disable ADB: WRITE_SECURE_SETTINGS permission missing!");
            return;
        }

        try {
            // Disable USB Debugging!
            Settings.Global.putInt(
                    context.getContentResolver(),
                    Settings.Global.ADB_ENABLED,
                    0
            );
            Log.i(TAG, "SUCCESS: USB Debugging automatically disabled due to cable disconnect timeout!");

            // Update Quick Settings Tile
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TileService.requestListeningState(
                        context,
                        new ComponentName(context, UsbDebugTileService.class)
                );
            }

            // Clean up trigger time in prefs
            AutoDisableManager.cancelTimeout(context);

            // Notify user
            AutoDisableManager.showAutoDisabledNotification(context);

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while disabling ADB automatically", e);
        }
    }
}
