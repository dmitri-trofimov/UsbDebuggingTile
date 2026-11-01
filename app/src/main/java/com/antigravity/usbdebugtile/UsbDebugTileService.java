package com.antigravity.usbdebugtile;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

public class UsbDebugTileService extends TileService {

    private static final String TAG = "UsbDebugTileService";
    private ContentObserver adbObserver;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();

        if (adbObserver == null) {
            adbObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    updateTileState();
                }
            };
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                    false,
                    adbObserver
            );
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (adbObserver != null) {
            getContentResolver().unregisterContentObserver(adbObserver);
            adbObserver = null;
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "onClick triggered! Checking permission...");

        boolean hasPermission = hasSecureSettingsPermission(this);

        if (!hasPermission) {
            Log.d(TAG, "Permission missing. Launching MainActivity with permission modal...");
            openMainActivity(true);
            updateTileState();
            return;
        }

        boolean isAdbOn = isAdbEnabled(this);
        boolean targetState = !isAdbOn;

        try {
            Settings.Global.putInt(
                    getContentResolver(),
                    Settings.Global.ADB_ENABLED,
                    targetState ? 1 : 0
            );
            Toast.makeText(
                    this,
                    targetState ? "USB Debugging Enabled" : "USB Debugging Disabled",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while toggling ADB", e);
            openMainActivity(true);
        }

        updateTileState();
    }

    private void openMainActivity(boolean showPermissionModal) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (showPermissionModal) {
            intent.putExtra("SHOW_PERMISSION_MODAL", true);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to startActivityAndCollapse", e);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    public static boolean hasSecureSettingsPermission(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isAdbEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.ADB_ENABLED,
                0
        ) == 1;
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean hasPermission = hasSecureSettingsPermission(this);

        if (!hasPermission) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel(getString(R.string.tile_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("Setup required");
            }
        } else {
            boolean isAdbOn = isAdbEnabled(this);
            tile.setState(isAdbOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(null);
            }
        }
        tile.updateTile();
    }
}
