package com.danielkao.autoscreenonoff.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.danielkao.autoscreenonoff.util.CV;

/**
 * Created by plateau on 2013/06/03.
 */
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {

        CV.logv("boot receiver");

        // auto pref is on
        if(CV.getPrefAutoOnoff(context)){
            CV.logv("start service by boot receiver");
            // send intent to service
            Intent i = new Intent(CV.SERVICE_INTENT_ACTION);
            i.putExtra(CV.SERVICEACTION, CV.SERVICEACTION_TOGGLE);
            i.putExtra(CV.SERVICETYPE, CV.SERVICETYPE_SETTING);
            context.startService(i);
        }

    }
}
