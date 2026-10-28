package cc.huoyan.retail.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cc.huoyan.retail.home.HomeActivity;

public class StartUpBroadCastReceiver extends BroadcastReceiver {
    private static final String ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            Intent thisIntent = new Intent(context, HomeActivity.class);
            thisIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(thisIntent);
        }
    }
}
