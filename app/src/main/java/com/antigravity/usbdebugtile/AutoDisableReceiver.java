package com.antigravity.usbdebugtile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AutoDisableReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoDisableReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AutoDisableManager.ACTION_AUTO_DISABLE.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "Auto-disable alarm fired from AlarmManager!");
        AutoDisableManager.executeAutoDisable(context);
    }
}
