package com.danielkao.autoscreenonoff.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

public final class CV {
	static final boolean debug = false;

	public static final String TAG = "SensorMonitor";
    public static final String PREF_CHARGING_ON = "prefChargingOn";
	public static final String PREF_AUTO_ON = "prefAutoOn";
    public static final String PREF_DISABLE_IN_LANDSCAPE= "prefDisableInLandscape";
    public static final String PREF_TIMEOUT_LOCK = "prefTimeout";
    public static final String PREF_TIMEOUT_UNLOCK = "prefTimeoutUnlock";
    public static final String PREF_VIEWED_VERSION_CODE = "prefViewedVersionCode";
    public static final String PREF_NO_PARTIAL_LOCK = "prefNoPartialLock";
    public static final String PREF_PLAY_CLOSE_SOUND = "prefPlayCloseSound";

    public static final String PREF_MAGNET_THRESHOLD = "magThreshHold";
    public static final String PREF_MAGNET_MODE = "prefMagnetMode";
    public static final String PREF_POLL_FREQ = "prefPollFreq";

    //
    public static final String SERVICEACTION = "serviceaction";
	public static final int SERVICEACTION_TOGGLE = 0;
    public static final int SERVICEACTION_TURNON = 1;
    public static final int SERVICEACTION_TURNOFF = 2;
    public static final int SERVICEACTION_UPDATE_DISABLE_IN_LANDSCAPE = 4;
    public static final int SERVICEACTION_MODE_SLEEP = 5;
    public static final int SERVICEACTION_SCREENOFF = 6;
    public static final int SERVICEACTION_PARTIALLOCK_TOGGLE = 8;

    public static String CLOSE_AFTER="close_after";

    public static String SLEEP_MODE_START = "sleep_mode_start";
    //
    //
	public static final String SERVICE_INTENT_ACTION = "com.danielkao.autoscreenonoff.serviceaction";
    public static final String UPDATE_WIDGET_ACTION = "com.danielkao.autoscreenonoff.updatewidget";
    //
    public static final String SERVICETYPE = "servicetype";
    public static final String SERVICETYPE_SETTING = "setting";
    public static final String SERVICETYPE_WIDGET = "widget";

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static void logv(Object...argv){
		if(!debug)
			return;
		
		if(argv.length == 1)
			Log.v(TAG, (String) argv[0]);
		else
		{
			Object [] slicedObj = Arrays.copyOfRange(argv, 1, argv.length);
			Log.v(TAG, String.format((String) argv[0], (Object[]) slicedObj));
		}
	}

    public static void logi(Object...argv){
        if(!debug)
            return;

        if(argv.length == 1)
            Log.i(TAG, (String) argv[0]);
        else
        {
            Object [] slicedObj = Arrays.copyOfRange(argv, 1, argv.length);
            Log.i(TAG,String.format((String) argv[0], (Object[])slicedObj));
        }
    }

    public static int getPrefPollFreq(Context context){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String freq = sp.getString(PREF_POLL_FREQ, "");
        if(freq.equals("")){
            return 100;
        }
        return Integer.parseInt(freq);
    }

    public static int getPrefMagThreshold(Context context){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String threshold = sp.getString(PREF_MAGNET_THRESHOLD, "150");
        if(threshold.equals("")){
            return -150;
        }
        return Integer.parseInt(threshold) * -1;
    }

    public static boolean getPrefAutoOnoff(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(PREF_AUTO_ON, false);
    }

    public static boolean getPrefNoPartialLock(Context context){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b  = sp.getBoolean(PREF_NO_PARTIAL_LOCK, false);
        CV.logv("prefShowNotification: %b", b);
        return b;
    }

    public static boolean getPrefPlayCloseSound(Context context){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b  = sp.getBoolean(PREF_PLAY_CLOSE_SOUND, false);
        CV.logv("prefPlayCloseSound: %b", b);
        return b;
    }

    //return milliseconds
    public static int getPrefTimeoutLock(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        int i  = Integer.parseInt(sp.getString(PREF_TIMEOUT_LOCK, "0"));
        CV.logv("prefTimeout lock: %d", i);
        return i;
    }

    public static int getPrefTimeoutUnlock(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        int i  = Integer.parseInt(sp.getString(PREF_TIMEOUT_UNLOCK, "-1"));
        CV.logv("prefTimeout unlock: %d", i);

        // if the value is -1, means user want it to be the same as lock timeout value
        if(i==-1)
        {
            i = getPrefTimeoutLock(context);
        }
        return i;
    }

    /*public static boolean isPlugged(Context context){
        Intent intentBat = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return (intentBat.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0);
    }

    public static boolean isInSleepTime(Context context){
        String pattern = "HH:mm";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        try{
            Date timeStart = sdf.parse(getPrefSleepStart(context));
            Date timeStop = sdf.parse(getPrefSleepStop(context));
            Calendar now = Calendar.getInstance();
            Date timeNow = sdf.parse(now.get(Calendar.HOUR_OF_DAY)+":"+now.get(Calendar.MINUTE));

            // start < stop
            if(timeStart.compareTo(timeStop)<0){
                if(timeStart.before(timeNow) && timeStop.after(timeNow))
                    return true;
                else
                    return false;

            }else{
                if(timeStop.before(timeNow) && timeStart.after(timeNow))
                    return false;
                else
                    return true;
            }

        } catch (ParseException e){
            e.printStackTrace();
        }

        return false;
    }*/
}
