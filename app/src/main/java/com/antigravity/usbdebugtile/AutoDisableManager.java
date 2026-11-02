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
import android.hardware.usb.UsbManager;
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
    public static final String CHANNEL_ID_SECURITY = "channel_security_alerts";
    public static final int NOTIFICATION_ID_AUTO_DISABLED = 1001;

    public static final int DEFAULT_TIMEOUT_MINUTES = 10;
    public static final int[] TIMEOUT_OPTIONS = {1, 5, 10, 15, 30};

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
        updateAutoDisableState(context);
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
            // Cable is connected and ADB is active -> user is developing normally, cancel pending timeout
            cancelTimeout(context);
        } else {
            // Cable is DISCONNECTED and ADB is active -> schedule or refresh timeout alarm!
            int timeoutMinutes = getTimeoutMinutes(context);
            long delayMillis = timeoutMinutes * 60L * 1000L;
            scheduleTimeout(context, delayMillis);
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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pendingIntent);
            }
            Log.d(TAG, "Scheduled auto-disable alarm for " + (delayMillis / 1000) + " seconds from now");
        } catch (SecurityException e) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pendingIntent);
        }
    }

    public static void cancelTimeout(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(context, AutoDisableReceiver.class);
            intent.setAction(ACTION_AUTO_DISABLE);
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
     * Returns remaining seconds until auto-disable, or -1 if not currently counting down.
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

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SECURITY)
                .setSmallIcon(R.drawable.ic_qs_adb)
                .setContentTitle("USB Debugging Auto-Disabled")
                .setContentText("Physical USB port secured after disconnect timeout.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AUTO_DISABLED, builder.build());
        } catch (SecurityException ignored) {
            // Android 13 POST_NOTIFICATIONS permission not granted
        }
    }
}
