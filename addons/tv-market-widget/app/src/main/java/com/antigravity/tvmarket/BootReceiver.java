package com.antigravity.tvmarket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent update = new Intent(MarketWidgetService.ACTION_UPDATE_WIDGET);
        update.putExtra("fetch_now", true);
        context.sendBroadcast(update);
    }
}

