package com.antigravity.usbdebugtile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UsbReceiver extends BroadcastReceiver {

    private static final String TAG = "UsbReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "Received hardware broadcast: " + action);

        // Re-evaluate countdown timer state on any USB/power event
        AutoDisableManager.updateAutoDisableState(context);
    }
}
