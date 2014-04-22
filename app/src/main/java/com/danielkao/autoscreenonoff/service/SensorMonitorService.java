package com.danielkao.autoscreenonoff.service;

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
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.view.OrientationEventListener;
import android.widget.Toast;
import com.danielkao.autoscreenonoff.*;
import com.danielkao.autoscreenonoff.provider.ToggleAutoScreenOnOffAppWidgetProvider;
import com.danielkao.autoscreenonoff.receiver.TurnOffReceiver;
import com.danielkao.autoscreenonoff.ui.MainActivity;
import com.danielkao.autoscreenonoff.util.CV;

public class SensorMonitorService extends Service implements SensorEventListener {
	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

	private SensorManager mSensorManager;
	private PowerManager powerManager;
	private Sensor mProximity;
    private Sensor mLight;
    private Sensor mMagnet;
    private int lightLux;
    private int baselineLightLux;
    private boolean proxClose;
    private boolean lightRegistered = false;
    boolean magnetMode;
    int magnetStrength;
    int magPollFreq;
    int magThreshold;
    final static int proxPollFreq = 100;
    OrientationEventListener mOrientationListener;

	private boolean mIsRegistered;

	private WakeLock partialLock, screenLock;

	DevicePolicyManager deviceManager;
	ComponentName mDeviceAdmin;

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
            if (CV.getPrefAutoOnoff(this)){
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
                    togglePreference();
                }

                updateWidgetCharging(false);

                if (CV.getPrefAutoOnoff(this) == false) {
                    unregisterSensor();
                } else {
                    registerSensor();
                }
                break;
            }
            case CV.SERVICEACTION_TURNON:
            {
                CV.logi("onStartCommand: turnon");
                if(!isRegistered()){
                    registerSensor();
                }
                break;
            }
            case CV.SERVICEACTION_TURNOFF:
            {
                CV.logi("onStartCommand: turnoff");
                if(isRegistered())
                    unregisterSensor();
                if(!CV.getPrefAutoOnoff(this)){
                }
                return START_NOT_STICKY;
            }
            case CV.SERVICEACTION_MODE_SLEEP:
            {
                if(CV.getPrefAutoOnoff(this)==false)
                    return START_STICKY;

                CV.logv("service:mode sleep action");
                boolean bSleepModeStart = intent.getBooleanExtra(CV.SLEEP_MODE_START, false);
                CV.logv("Sleep Mode:%b",bSleepModeStart);

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
        magnetMode = sp.getBoolean(CV.PREF_MAGNET_MODE, true);
        CV.logv("Magnet Mode " + magnetMode);

		powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);

		partialLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"autoscreenonoff partiallock");
		screenLock = powerManager.newWakeLock(
				PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.SCREEN_BRIGHT_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, "autoscreenonoff fulllock");
	}

	@Override
	public void onDestroy() {
		CV.logi("onDestroy");
        if(partialLock.isHeld())
            partialLock.release();
        if(screenLock.isHeld()){
            screenLock.release();
        }
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
        magThreshold = CV.getPrefMagThreshold(getBaseContext());

        if(magnetMode){
            if(powerManager.isScreenOn())
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
        if (powerManager.isScreenOn() && !bForeground) {
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
            if(magnetStrength < magThreshold && powerManager.isScreenOn()){
                turnOff();
            }else if(!proxClose){
                turnOn();
            }
        }
        else if(type == Sensor.TYPE_PROXIMITY){
            int cmDist = (int)event.values[0];
            System.out.println(cmDist);
            if(cmDist != 0){
                //turnOn();

                proxClose = false;
                if(!powerManager.isScreenOn()){
                    mSensorManager.registerListener(this, mMagnet, magPollFreq);
                }
            }else{
                proxClose = true;
                if(!powerManager.isScreenOn()){
                    mSensorManager.unregisterListener(this, mMagnet);
                }
            }
        }
	}



    //<editor-fold desc="time out handler">

    private void turnOn(){
        if (!screenLock.isHeld()) {
            screenLock.acquire();
        }
        screenLock.release();

        if(magnetMode){
            //mSensorManager.unregisterListener(this);
            mSensorManager.registerListener(this, mMagnet, magPollFreq);
        }
        else if(!lightRegistered){
            mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
            CV.logi("light sensor enabled");

            lightRegistered = true;
        }


    }

    private void turnOff(){
        CV.logv("sensor: turn off thread");
        if(screenLock.isHeld())
            screenLock.release();
        deviceManager.lockNow();
        //playCloseSound();
        mSensorManager.unregisterListener(this, mMagnet);
        //mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);

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

    private void resetHandler(){
        CV.logv("reset Handler");
        handler.removeMessages(CALLBACK_EXISTS);
        handler.removeCallbacks(runnableTurnOn);
        handler.removeCallbacks(runnableTurnOff);
    }

    private void togglePreference() {
        CV.logv("togglePreference");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean IsAutoOn = sp.getBoolean(CV.PREF_AUTO_ON, false);
        Editor editor = sp.edit();
        editor.putBoolean(CV.PREF_AUTO_ON, !IsAutoOn);
        editor.commit();

    }

    private void playCloseSound(){
        if(CV.getPrefPlayCloseSound(this)){
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            float vol = 1.0f;
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, vol);
        }
    }
}
