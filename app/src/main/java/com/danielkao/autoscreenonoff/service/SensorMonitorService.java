package com.danielkao.autoscreenonoff.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.view.OrientationEventListener;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.danielkao.autoscreenonoff.*;
import com.danielkao.autoscreenonoff.provider.ToggleAutoScreenOnOffAppWidgetProvider;
import com.danielkao.autoscreenonoff.receiver.TurnOffReceiver;
import com.danielkao.autoscreenonoff.ui.AutoScreenOnOffPreferenceActivity;
import com.danielkao.autoscreenonoff.ui.MainActivity;
import com.danielkao.autoscreenonoff.ui.TimePreference;
import com.danielkao.autoscreenonoff.util.CV;

import java.lang.reflect.Method;
import java.util.Calendar;

public class SensorMonitorService extends Service implements SensorEventListener {
	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

	private SensorManager mSensorManager;
	private PowerManager mPowerManager;
	private Sensor mProximity;
    private Sensor mLight;
    private Sensor mMagnet;
    private int lightLux;
    private int baselineLightLux;
    private boolean lightRegistered = false;
    boolean magnetMode;
    int magnetStrength;
    int magPollFreq;
    final static int proxPollFreq = 500000;
    OrientationEventListener mOrientationListener;

	private boolean mIsRegistered;

	private WakeLock partialLock, screenLock;

	DevicePolicyManager deviceManager;
	ComponentName mDeviceAdmin;

    private int mRotationAngle = 360;

    //handle timeout function
    private int CALLBACK_EXISTS=0;
    //private Timer timer;
    private Handler handler = new Handler();

    // for notification logic
    private boolean bForeground = false;
	private boolean isActiveAdmin() {
		return deviceManager.isAdminActive(mDeviceAdmin);
	}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        CV.logi("onStartCommand");
        // being restarted
        if (intent == null) {
            CV.logi("onStartCommand: no intent");
            // start monitoring when
            // 1. autoOn is on
            // 2. charging is on and is plugged in
            if (CV.getPrefAutoOnoff(this)){
                // before registering, need to check whether it's in sleeping time period
                // if so, do nothing
                //if(CV.getPrefSleeping(this) && CV.isInSleepTime(this))
                //    return START_NOT_STICKY;
                registerSensor();
            }else if(CV.getPrefChargingOn(this)&&CV.isPlugged(this)){
                registerSensor();
            }

            return START_STICKY;
        }

        int action = intent.getIntExtra(CV.SERVICEACTION, -1);

        switch(action){
            case CV.SERVICEACTION_SCREENOFF:
            {
                CV.logi("onStartCommand: screenoff");
                // grant device management
                if(!isActiveAdmin()){
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.putExtra(CV.CLOSE_AFTER,true);
                    this.startActivity(i);
                }
                else{
                    deviceManager.lockNow();
                }
                return START_NOT_STICKY;
            }
            // from widget or setting
            case CV.SERVICEACTION_TOGGLE:
            {
                CV.logi("onStartCommand: toggle");

                String servicetype = intent.getStringExtra(CV.SERVICETYPE);
                // it's from widget or notification, need to do the toggle first
                if(servicetype!=null && !servicetype.equals(CV.SERVICETYPE_SETTING)){
                    // in charging state and pref charging on is turned on
                    if(CV.isPlugged(this)&&CV.getPrefChargingOn(this)){
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                        Editor editor = sp.edit();
                        editor.putBoolean(CV.PREF_CHARGING_ON, false);
                        editor.commit();
                    }else{
                        togglePreference();
                    }
                }

                updateWidgetCharging(false);
                //updateNotification();

                if (CV.getPrefAutoOnoff(this) == false) {
                    unregisterSensor();
                } else {
                    // before registering, need to check whether it's in sleeping time period
                    // if so, do nothing
                    if(CV.getPrefSleeping(this) && CV.isInSleepTime(this))
                        return START_NOT_STICKY;
                    registerSensor();
                }
                break;
            }
            case CV.SERVICEACTION_TURNON:
            {
                CV.logi("onStartCommand: turnon");
                // from charging receiver
                if(!isRegistered()){
                    registerSensor();

                    updateWidgetCharging(CV.isPlugged(this));
                    //updateNotification();
                }
                break;
            }
            case CV.SERVICEACTION_TURNOFF:
            {
                CV.logi("onStartCommand: turnoff");
                // from charging receiver
                if(isRegistered())
                    unregisterSensor();
                if(!CV.getPrefAutoOnoff(this)){
                    updateWidgetCharging(false);
                    //updateNotification();
                }
                return START_NOT_STICKY;
            }
            case CV.SERVICEACTION_UPDATE_DISABLE_IN_LANDSCAPE:
            {
                //if(CV.getPrefAutoOnoff(this) ||
                //        (CV.getPrefChargingOn(this)&& isPlugged())){
                if(mIsRegistered){
                    if(CV.getPrefDisableInLandscape(this) == true){
                        //registerOrientationChange();
                    }else{
                        //unregisterOrientationChange();
                    }
                }
                break;
            }
            case CV.SERVICEACTION_MODE_SLEEP:
            {
                if(CV.getPrefAutoOnoff(this)==false)
                    return START_STICKY;

                CV.logv("service:mode sleep action");
                boolean bSleepModeStart = intent.getBooleanExtra(CV.SLEEP_MODE_START, false);
                CV.logv("Sleep Mode:%b",bSleepModeStart);

                //if(CV.isInSleepTime(this)){
                if(bSleepModeStart){
                    CV.logi("sleep mode starts: turn off sensor");
                    unregisterSensor();
                }
                else{
                    CV.logi("sleep mode stops: turn on sensor");
                    registerSensor();
                }

                break;
            }
            case CV.SERVICEACTION_PARTIALLOCK_TOGGLE:
            {
                // no need to use partial lock
                if(CV.getPrefNoPartialLock(this)  && partialLock.isHeld()){
                    partialLock.release();
                // need partial lock. make sure the sensor is registered.
                }else if (!CV.getPrefNoPartialLock(this) && isRegistered()){
                    partialLock.acquire();
                }

                break;
            }
            case CV.SERVICEACTION_SET_SCHEDULE:
            {
                //setSchedule();
                break;
            }
            case CV.SERVICEACTION_CANCEL_SCHEDULE:
            {
                //cancelSchedule();
                break;
            }
            default:
                CV.logi("onStartCommand: others");
        }

		return START_STICKY;
	}

    /**
     * send broadcast to update appWidget UI
     * @param b whether the Charging Icon should be shown
     */
    private void updateWidgetCharging(boolean b) {
        Intent i = new Intent(this, ToggleAutoScreenOnOffAppWidgetProvider.class);
        i.setAction(CV.UPDATE_WIDGET_ACTION);
        i.putExtra(CV.PREF_CHARGING_ON, b);
        this.sendBroadcast(i);
    }

    //
	// life cycle
	//
	public SensorMonitorService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		deviceManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		mDeviceAdmin = new ComponentName(this, TurnOffReceiver.class);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        magnetMode = sp.getBoolean(CV.PREF_MAGNET_MODE, false);
        CV.logv("Magnet Mode " + magnetMode);

		mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);

		partialLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"autoscreenonoff partiallock");
		screenLock = mPowerManager.newWakeLock(
				PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.FULL_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, "autoscreenonoff fulllock");
	}

	@Override
	public void onDestroy() {
		CV.logi("onDestroy");
        if(mIsRegistered)
		    unregisterSensor();
		super.onDestroy();
	}

	// to return service class
	public class LocalBinder extends Binder {
		public SensorMonitorService getService() {
			return SensorMonitorService.this;
		}
	}

    //<editor-fold desc="sensor registration">
    //
	// pubilc API for client
	//
	public void registerSensor() {
		CV.logi("registerSensor");
		if (mIsRegistered) {
			return;
		}

		//open main activity if app doesn't have admin rights
		if(!isActiveAdmin()){
			Intent i = new Intent(this, MainActivity.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			this.startActivity(i);
		}

        magPollFreq = CV.getPrefPollFreq(getBaseContext());

        if(magnetMode){
            if(mPowerManager.isScreenOn())
                mSensorManager.registerListener(this, mMagnet, magPollFreq);
            mSensorManager.registerListener(this, mProximity, proxPollFreq);
        }else{
            mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
            lightRegistered = true;
        }

		mIsRegistered = true;

        // partial lock exists, and need partial lock
		if (partialLock != null && !CV.getPrefNoPartialLock(this))
			partialLock.acquire();

        // show hint text if the screen is on
        if (mPowerManager.isScreenOn() && !bForeground) {
            String s = getString(R.string.turn_autoscreen_on);
            Toast.makeText(SensorMonitorService.this, s, Toast.LENGTH_SHORT).show();
        }
	}

	public void unregisterSensor() {
		CV.logi("unregisterSensor");
		if (mIsRegistered) {
			mSensorManager.unregisterListener(this);
            lightRegistered = false;
            if(!bForeground)
            {
                String s = getString(R.string.turn_autoscreen_off);
			    Toast.makeText(SensorMonitorService.this, s, Toast.LENGTH_SHORT).show();
            }
		}

		if (partialLock != null && partialLock.isHeld())
			partialLock.release();
		mIsRegistered = false;

        // do not close service if the notification is shown
        if(!bForeground)
		    stopSelf();
	}

	public boolean isRegistered() {
		return mIsRegistered;
	}
    //</editor-fold>

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		CV.logv("onAccuracyChanged:%d", accuracy);
	}

	@Override
	public final void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if(type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED){
            magnetStrength = (int)event.values[2];
            //CV.logv((int)event.values[0] + " " + (int)event.values[1] + " " + magnetStrength);
            //System.out.println((int)event.values[0] + " " + (int)event.values[1] + " " + magnetStrength);
            if(magnetStrength < -200 && mPowerManager.isScreenOn()){
                turnOff();
            }
        }
        else if(type == Sensor.TYPE_LIGHT){
            lightLux = (int)event.values[0];
            System.out.println("lux" + lightLux);
        }
        else if(type == Sensor.TYPE_PROXIMITY){
            int cmDist = (int)event.values[0];
            if(magnetMode){
                if(cmDist != 0){
                    turnOn();
                }
            }else{
                // Do something with this sensor value.
                CV.logv("onSensorChanged proximity:%f", cmDist);
                if (isActiveAdmin()) {
                    // reset handler if there's already one
                    if(handler.hasMessages(CALLBACK_EXISTS)){
                        CV.logv("timer is on; exit");
                        resetHandler();
                        return;
                    }

                    // value == 0; should turn screen off
                    if (cmDist == 0) {
                        if (mPowerManager.isScreenOn()) {
                            if(false){
                                return;
                            }
                            else{
                                long timeout = (long) CV.getPrefTimeoutLock(this);
                                if(timeout == 0)
                                    turnOff();
                                else{
                                    handler.postDelayed(runnableTurnOff, timeout);
                                }
                            }
                        }
                    }
                    // should turn on
                    else {
                        baselineLightLux = lightLux;
                        if (!mPowerManager.isScreenOn()) {
                            long timeout = (long) CV.getPrefTimeoutUnlock(this);
                            if(timeout==0){
                                turnOn();
                            } else
                                handler.postDelayed(runnableTurnOn, timeout);
                        }
                    }
                }
            }
        }
	}

	private void togglePreference() {
        CV.logv("togglePreference");
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		boolean IsAutoOn = sp.getBoolean(CV.PREF_AUTO_ON, false);
		Editor editor = sp.edit();
		editor.putBoolean(CV.PREF_AUTO_ON, !IsAutoOn);
        // if original value is false, it's meant to turn on pref, then we should make sure which-charging is off
        if(!IsAutoOn)
            editor.putBoolean(CV.PREF_CHARGING_ON, false);
		editor.commit();

	}

    //<editor-fold desc="time out handler">
    private void resetHandler(){
        CV.logv("reset Handler");
        handler.removeMessages(CALLBACK_EXISTS);
        handler.removeCallbacks(runnableTurnOn);
        handler.removeCallbacks(runnableTurnOff);
    }

    private void turnOn(){
        if(magnetMode){
            //mSensorManager.unregisterListener(this);
            mSensorManager.registerListener(this, mMagnet, magPollFreq);
        }
        else if(!lightRegistered){
            mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
            CV.logi("light sensor enabled");

            lightRegistered = true;
        }

        if (!screenLock.isHeld()) {
            screenLock.acquire();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        //Thread.sleep(1000);
                        // try to fix phonepad and galaxy note's issue
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(screenLock.isHeld())
                        screenLock.release();
                }
            }).start();
        }
    }

    private void turnOff(){
        CV.logv("sensor: turn off thread");
        if(screenLock.isHeld())
            screenLock.release();
        deviceManager.lockNow();
        //playCloseSound();
        if(magnetMode){
            mSensorManager.unregisterListener(this, mMagnet);
            //mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
        else if(lightRegistered){
            mSensorManager.unregisterListener(this, mLight);
            CV.logi("Light sensor disabled");

            lightRegistered = false;
        }

        boolean b  = CV.getPrefPlayCloseSound(this);
        if(b){
            Vibrator vibe = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            vibe.vibrate(40);
        }

    }


    private Runnable runnableTurnOff = new Runnable() {
        @Override
        public void run() {
            //System.out.println(baselineLightLux + " " + lightLux);
            if(magnetMode){
                turnOff();
                resetHandler();
                return;
            }else{
                if(baselineLightLux < 40 || (lightLux < baselineLightLux/2)){
                    turnOff();
                    resetHandler();
                    return;
                }
                resetHandler();
                handler.postDelayed(runnableTurnOff, 1000);
            }
        }
    };

    private Runnable runnableTurnOn = new Runnable() {
        @Override
        public void run() {
            CV.logi("sensor: turn on thread");
            turnOn();
            resetHandler();
        }
    };
    //</editor-fold>

    private void playCloseSound(){
        if(CV.getPrefPlayCloseSound(this)){
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            float vol = 1.0f;
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, vol);
        }
    }
}
