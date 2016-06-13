package com.wassup789.android.facebooknotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BroadcastReceive extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent startBackgroundService = new Intent(context, BackgroundService.class);
        context.startService(startBackgroundService);
    }
}