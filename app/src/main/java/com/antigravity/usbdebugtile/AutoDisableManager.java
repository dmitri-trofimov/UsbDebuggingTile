package com.antigravity.usbdebugtile;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class AutoDisableManager {

    private static final String TAG = "AutoDisableManager";
    public static final String PREFS_NAME = "usb_debug_prefs";
    public static final String KEY_AUTO_DISABLE_ENABLED = "key_auto_disable_enabled";
    public static final String KEY_TIMEOUT_MINUTES = "key_timeout_minutes";
    public static final String KEY_TRIGGER_REALTIME = "key_trigger_realtime";

    public static final String ACTION_AUTO_DISABLE = "com.antigravity.usbdebugtile.action.AUTO_DISABLE";
    public static final String ACTION_RE_ENABLE = "com.antigravity.usbdebugtile.action.RE_ENABLE";
    public static final String CHANNEL_ID_SECURITY = "channel_security_alerts";
    public static final int NOTIFICATION_ID_AUTO_DISABLED = 1001;

    public static final int DEFAULT_TIMEOUT_MINUTES = 10;
    public static final int[] TIMEOUT_OPTIONS = {1, 5, 10, 15, 30, 60};

    public static int getTimeoutForIndex(int index) {
        if (index < 0) index = 0;
        if (index >= TIMEOUT_OPTIONS.length) index = TIMEOUT_OPTIONS.length - 1;
        return TIMEOUT_OPTIONS[index];
    }

    public static int getIndexForTimeout(int minutes) {
        for (int i = 0; i < TIMEOUT_OPTIONS.length; i++) {
            if (TIMEOUT_OPTIONS[i] == minutes) return i;
        }
        return 2; // Default 10 min
    }

    public static String formatTimeout(int minutes) {
        if (minutes == 60) return "1 hour";
        return minutes + " min";
    }

    public static String formatTimeoutShort(int minutes) {
        if (minutes == 60) return "1h";
        return minutes + "m";
    }

    public static boolean isAutoDisableEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_DISABLE_ENABLED, false);
    }

    public static void setAutoDisableEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_DISABLE_ENABLED, enabled).apply();
        updateAutoDisableState(context);
    }

    public static int getTimeoutMinutes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES);
    }

    public static void setTimeoutMinutes(Context context, int minutes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_TIMEOUT_MINUTES, minutes).apply();
        // If cable is currently disconnected and ADB is active, restart countdown with new duration
        if (isAutoDisableEnabled(context) && UsbDebugTileService.isAdbEnabled(context) && !isUsbConnected(context)) {
            cancelTimeout(context);
            scheduleTimeout(context, minutes * 60L * 1000L);
        }
    }

    /**
     * Checks whether the USB cable is physically connected to a host/charger.
     * Uses the kernel's sticky USB_STATE broadcast first, then BatteryManager fallback.
     */
    public static boolean isUsbConnected(Context context) {
        try {
            Intent usbIntent = context.registerReceiver(null, new IntentFilter("android.hardware.usb.action.USB_STATE"));
            if (usbIntent != null && usbIntent.getExtras() != null) {
                return usbIntent.getBooleanExtra("connected", false);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read USB_STATE", e);
        }

        try {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                return plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_AC;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read BATTERY_CHANGED", e);
        }

        return false;
    }

    /**
     * Core state machine: evaluates whether to start a countdown or cancel any pending timeout.
     * Prevents resetting active countdowns if multiple broadcasts arrive while unplugged.
     */
    public static void updateAutoDisableState(Context context) {
        boolean enabled = isAutoDisableEnabled(context);
        boolean isAdbOn = UsbDebugTileService.isAdbEnabled(context);
        boolean isConnected = isUsbConnected(context);

        Log.d(TAG, "updateAutoDisableState: enabled=" + enabled + ", adb=" + isAdbOn + ", usbConnected=" + isConnected);

        if (!enabled || !isAdbOn) {
            cancelTimeout(context);
            return;
        }

        if (isConnected) {
            // Cable is connected and ADB is active -> developer working normally, cancel pending timeout
            cancelTimeout(context);
        } else {
            // Cable is DISCONNECTED and ADB is active
            int remainingSec = getRemainingSeconds(context);
            if (remainingSec == 0) {
                // Countdown already reached 0! Disable ADB immediately
                executeAutoDisable(context);
            } else if (remainingSec < 0) {
                // No countdown currently running, start a fresh one!
                int timeoutMinutes = getTimeoutMinutes(context);
                long delayMillis = timeoutMinutes * 60L * 1000L;
                scheduleTimeout(context, delayMillis);
            } else {
                // Countdown is ALREADY running (e.g. 58s remaining), preserve existing timer
                Log.d(TAG, "Countdown already active (" + remainingSec + "s remaining). Preserving timer.");
            }
        }
    }

    public static void scheduleTimeout(Context context, long delayMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long triggerElapsed = SystemClock.elapsedRealtime() + delayMillis;

        // Persist expected trigger time so UI can render a live countdown
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_TRIGGER_REALTIME, triggerElapsed).apply();

        Intent intent = new Intent(context, AutoDisableReceiver.class);
        intent.setAction(ACTION_AUTO_DISABLE);
        intent.setPackage(context.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            // setExactAndAllowWhileIdle works seamlessly with USE_EXACT_ALARM on Android 13+
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pendingIntent);
            Log.i(TAG, "Scheduled EXACT auto-disable alarm for " + (delayMillis / 1000) + " seconds from now");
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm permission missing, falling back to setAndAllowWhileIdle", e);
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pendingIntent);
        }
    }

    public static void cancelTimeout(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, AutoDisableReceiver.class);
            intent.setAction(ACTION_AUTO_DISABLE);
            intent.setPackage(context.getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_TRIGGER_REALTIME).apply();
        Log.d(TAG, "Cancelled any pending auto-disable alarm");
    }

    /**
     * Executes the actual disable action with full safety verification.
     * Can be invoked directly from AutoDisableReceiver or MainActivity when countdown hits 0.
     */
    public static synchronized void executeAutoDisable(Context context) {
        boolean enabled = isAutoDisableEnabled(context);
        boolean isConnected = isUsbConnected(context);
        boolean isAdbOn = UsbDebugTileService.isAdbEnabled(context);

        Log.d(TAG, "executeAutoDisable: enabled=" + enabled + ", connected=" + isConnected + ", adb=" + isAdbOn);

        if (!enabled) return;
        if (isConnected) {
            cancelTimeout(context);
            return;
        }
        if (!isAdbOn) {
            cancelTimeout(context);
            return;
        }

        if (!UsbDebugTileService.hasSecureSettingsPermission(context)) {
            Log.e(TAG, "Cannot auto-disable ADB: WRITE_SECURE_SETTINGS permission missing!");
            return;
        }

        try {
            // Flip ADB off at system level
            Settings.Global.putInt(
                    context.getContentResolver(),
                    Settings.Global.ADB_ENABLED,
                    0
            );
            Log.i(TAG, "SUCCESS: USB Debugging automatically disabled!");

            // Update Quick Settings Tile
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TileService.requestListeningState(
                        context,
                        new ComponentName(context, UsbDebugTileService.class)
                );
            }

            cancelTimeout(context);
            showAutoDisabledNotification(context);

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while disabling ADB automatically", e);
        }
    }

    /**
     * Returns remaining seconds until auto-disable:
     *  > 0 : active countdown
     *  == 0: countdown has expired
     *  < 0 : no countdown currently scheduled
     */
    public static int getRemainingSeconds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long triggerElapsed = prefs.getLong(KEY_TRIGGER_REALTIME, 0);
        if (triggerElapsed <= 0) return -1;

        long now = SystemClock.elapsedRealtime();
        long diff = triggerElapsed - now;
        if (diff <= 0) return 0;
        return (int) (diff / 1000);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_SECURITY,
                    "USB Security Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifies when USB debugging is automatically disabled for security");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static void showAutoDisabledNotification(Context context) {
        createNotificationChannel(context);

        // Body tap -> open MainActivity
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action 1: Re-enable ADB (Requires fingerprint / PIN unlock on lock screen)
        Intent reEnableIntent = new Intent(context, AutoDisableReceiver.class);
        reEnableIntent.setAction(ACTION_RE_ENABLE);
        reEnableIntent.setPackage(context.getPackageName());
        PendingIntent pendingReEnable = PendingIntent.getBroadcast(
                context,
                1,
                reEnableIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action reEnableAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_qs_adb,
                "Re-enable",
                pendingReEnable
        ).setAuthenticationRequired(true).build();

        // Action 2: Settings (Opens companion app, requires unlock)
        Intent settingsIntent = new Intent(context, MainActivity.class);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingSettings = PendingIntent.getActivity(
                context,
                2,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action settingsAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_timer,
                "Settings",
                pendingSettings
        ).setAuthenticationRequired(true).build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SECURITY)
                .setSmallIcon(R.drawable.ic_qs_adb)
                .setContentTitle("USB Debugging Auto-Disabled")
                .setContentText("Physical USB port secured after disconnect timeout.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Physical USB port secured after disconnect timeout."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .addAction(reEnableAction)
                .addAction(settingsAction)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AUTO_DISABLED, builder.build());
        } catch (SecurityException ignored) {
            // Android 13 POST_NOTIFICATIONS permission not granted
        }
    }
}
