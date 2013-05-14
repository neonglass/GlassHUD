package com.appliedanalog.glass.hud;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.appliedanalog.glass.hud.sources.CompassSource;
import com.appliedanalog.glass.hud.sources.GSensorSource;
import com.google.glass.location.GlassLocationManager;
import com.google.glass.timeline.TimelineHelper;
import com.google.glass.timeline.TimelineProvider;
import com.google.glass.util.SettingsSecure;
import com.google.googlex.glass.common.proto.TimelineItem;

public class GlassHUDService extends Service implements SensorEventListener{
	static final String TAG = "GlassHUDService";
	final int TIMELINE_UPDATE_INTERVAL = 250; //in ms
	
	GlassHUDService me;
    
	//States
	SensorManager sensorManager;
	Sensor magSensor;
	Sensor accelSensor;
    PowerManager.WakeLock wakelock;
    SensorConverter senseConverter;
    boolean enabled = false;
	
    //Internal sensors representations
    CompassSource compass;
    GSensorSource gsensor;
	
	@Override
	public void onCreate(){
		super.onCreate();
		me = this;
		refreshPreferences();
		
		//Then sensors
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        GlassLocationManager.init(this);
		
		compass = new CompassSource();
		gsensor = new GSensorSource();
		senseConverter = new SensorConverter();
	}
	
	//handle incoming messages
	@Override
	public int onStartCommand(Intent intent, int flags, int startid){
		super.onStartCommand(intent, flags, startid);
		
		if(intent == null) return START_STICKY;
		refreshPreferences();
		
		final String pkg = intent.getStringExtra("pkg");
		switch(intent.getIntExtra("op", -1)){
		
		}
				
		return START_STICKY;
	}
	
	//Activity Binding Functionality	
	public class HUDBinder extends Binder{
		public boolean running(){
			return enabled;
		}
		
		public void startup(){
			startHUD();
		}
		
		public void shutdown(){
			stopHUD();
		}
		
		public boolean wakeLockObtained(){
			return (wakelock != null);
		}
		
		public void getWakeLock(){
	    	PowerManager powman = (PowerManager)getSystemService(Context.POWER_SERVICE);
	    	wakelock = powman.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "HUD");
	    	wakelock.acquire();
		}
		
		public void releaseWakeLock(){
	    	wakelock.release();
	    	wakelock = null;
		}
	}
	
	HUDBinder vBinder = new HUDBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return vBinder;
	}
	
	private void refreshPreferences(){
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		//nothing yet.
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		stopHUD();
		if(wakelock != null){
			wakelock.release();
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor == magSensor){
			compass.magReading(event.values);
			senseConverter.sensorReading(compass.getTitle(), compass.getValue1(), compass.getValue2());
		}else if(event.sensor == accelSensor){
			gsensor.accelReading(event.values);
			senseConverter.sensorReading(gsensor.getTitle(), gsensor.getValue1(), gsensor.getValue2());
		}
	}
	
	void startHUD(){
		Log.v(TAG, "STARTING HUD SERVICE");
		if(enabled) return;
		sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//startup bluetooth updates
		
		timelineUpdater = new TimelineUpdater();
		timelineUpdater.start();
		enabled = true;
	}
	
	void stopHUD(){
		Log.v(TAG, "STOPPING HUD SERVICE");
		if(!enabled) return;
		sensorManager.unregisterListener(this);
		//kill bluetooth updates
		
		timelineUpdater.stopUpdates();
		timelineUpdater = null;
		enabled = false;
	}
	
	TimelineItem baseItem = null;
	int counter = 0;
	class TimelineUpdater extends Thread{
		boolean running = false;
		
		@Override
		public void run(){
			running = true;
	    	ContentResolver cr = getContentResolver();
	    	final TimelineHelper tlHelper = new TimelineHelper();
	    	if(baseItem == null){
		    	TimelineItem.Builder ntib = tlHelper.createTimelineItemBuilder(me, new SettingsSecure(cr));
		    	ntib.setTitle("Test card");
		    	ntib.setHtml("<html>Hi</html>");
		    	baseItem = ntib.build();
		    	ContentValues vals = TimelineHelper.toContentValues(baseItem);
		    	cr.insert(TimelineProvider.TIMELINE_URI, vals);
	    	}
			while(running){
				Log.v(TAG, "Updating HUD card [" + (counter++) + "]");
        		TimelineHelper.Update updater = new TimelineHelper.Update(){
    				@Override
    				public TimelineItem onExecute() {
    		    		TimelineItem.Builder builder = TimelineItem.newBuilder(baseItem);
    		    		builder.setHtml(senseConverter.getHtml());
    		    		return tlHelper.updateTimelineItem(me, builder.build(), null, true, false);
    				}
        		};
        		TimelineHelper.atomicUpdateTimelineItem(updater);
        		baseItem = updater.getItem();
        		try{ Thread.sleep(TIMELINE_UPDATE_INTERVAL); }catch(Exception e){}
			}
		}
		
		public void stopUpdates(){
			running = false;
		}
	}
	TimelineUpdater timelineUpdater = null;
}
