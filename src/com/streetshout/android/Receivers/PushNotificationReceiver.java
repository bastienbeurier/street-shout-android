package com.streetshout.android.receivers;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.streetshout.android.activities.ExploreActivity;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushManager;

public class PushNotificationReceiver extends BroadcastReceiver {
    public static String TAG = "PushNotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // look for others off of PushManager, such as notification received
        if (action.equals(PushManager.ACTION_NOTIFICATION_OPENED)) {
            handleOpen(context, intent);
        }
    }

    private void handleOpen(Context context, Intent intent)  {
        if(intent.getStringExtra("shout") != null) {
            Application app	= (Application) UAirship.shared().getApplicationContext();
            Intent start = new Intent(app, ExploreActivity.class);
            start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            start.putExtra("notificationShout", intent.getStringExtra("shout"));
            app.startActivity(start);
        }
    }
}
