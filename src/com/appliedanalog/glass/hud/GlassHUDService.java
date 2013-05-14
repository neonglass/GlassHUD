package com.appliedanalog.glass.hud;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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
	PhoneSensorComm btComm;
    
	//States
	SensorManager sensorManager;
	Sensor magSensor;
	Sensor accelSensor;
    PowerManager.WakeLock wakelock;
    SensorDataSink senseConverter;
    boolean enabled = false;
	
    //Internal sensors representations
    CompassSource compass;
    GSensorSource gsensor;
	
	@Override
	public void onCreate(){
		super.onCreate();
		me = this;
		
		//Then sensors
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        GlassLocationManager.init(this);
		
		compass = new CompassSource();
		gsensor = new GSensorSource();
		senseConverter = new SensorDataSink();
		
		btComm = new PhoneSensorComm(senseConverter);
	}
	
	//handle incoming messages
	@Override
	public int onStartCommand(Intent intent, int flags, int startid){
		super.onStartCommand(intent, flags, startid);
		
		if(intent == null) return START_STICKY;
		
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
		
		public boolean isBtConnected(){
			return btComm.connected();
		}
		
		/**
		 * The client is responsible for unregistering himself by calling this function with null when he goes out of context..
		 * @param stateListener
		 */
		public void setBTListener(PhoneSensorComm.BTStateListener stateListener){
			btComm.setStateListener(stateListener);
		}
	}
	
	HUDBinder vBinder = new HUDBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return vBinder;
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
		btComm.startup();
		timelineUpdater = new TimelineUpdater();
		timelineUpdater.start();
		enabled = true;
	}
	
	void stopHUD(){
		Log.v(TAG, "STOPPING HUD SERVICE");
		if(!enabled) return;
		sensorManager.unregisterListener(this);
		//kill bluetooth updates
		btComm.stop();
		timelineUpdater.stopUpdates();
		timelineUpdater = null;
		enabled = false;
	}
	
	final String HUD_OFF_STRING = "<html><article><section><b>Glass HUD is Off</b></section></article></html>";
	final String SPREF_STORED_TIMELINE_ID = "storedTimelineId";
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
	    		//We store the timeline ID used by the HUD service inside of the shared preferences. We should re-use this timeline
	    		//card if at all possible.
	    		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(me);
	    		String stashedTimelineId = pref.getString(SPREF_STORED_TIMELINE_ID, null);
	    		if(stashedTimelineId != null){
	    			Log.v(TAG, "GlassHUDService: Loading TimelineItem " + stashedTimelineId);
	    			baseItem = tlHelper.queryTimelineItem(cr, stashedTimelineId);
	    		}
	    		if(baseItem == null){ //either the stashing failed or this is the first time the app is being run
	    			Log.v(TAG, "GlassHUDService: no stashed TimelineItem, must rebuild from scratch.");
			    	TimelineItem.Builder ntib = tlHelper.createTimelineItemBuilder(me, new SettingsSecure(cr));
			    	ntib.setTitle("Glass HUD");
			    	ntib.setHtml(HUD_OFF_STRING);
			    	baseItem = ntib.build();
			    	ContentValues vals = TimelineHelper.toContentValues(baseItem);
			    	cr.insert(TimelineProvider.TIMELINE_URI, vals);
			    	
			    	//also insert the new ID into the sharedprefs.
			    	SharedPreferences.Editor editor = pref.edit();
			    	editor.putString(SPREF_STORED_TIMELINE_ID, baseItem.getId());
			    	editor.commit();
	    		}
	    	}
			while(running){
        		TimelineHelper.Update updater = new TimelineHelper.Update(){
    				@Override
    				public TimelineItem onExecute() {
    		    		TimelineItem.Builder builder = TimelineItem.newBuilder(baseItem);
    		    		builder.setHtml(senseConverter.getHtml());
    		    		builder.setIsPinned(true);
    		    		builder.setPinTime(System.currentTimeMillis());
    		    		return tlHelper.updateTimelineItem(me, builder.build(), null, true, false);
    				}
        		};
        		TimelineHelper.atomicUpdateTimelineItem(updater);
        		baseItem = updater.getItem();
        		try{ Thread.sleep(TIMELINE_UPDATE_INTERVAL); }catch(Exception e){}
			}
			
			//Last, but not least, un-pin the card
    		TimelineHelper.Update updater = new TimelineHelper.Update(){
				@Override
				public TimelineItem onExecute() {
		    		TimelineItem.Builder builder = TimelineItem.newBuilder(baseItem);
		    		builder.setHtml(HUD_OFF_STRING);
		    		builder.setIsPinned(false);
		    		builder.setPinTime(-1); //unpinned pin time
		    		return tlHelper.updateTimelineItem(me, builder.build(), null, true, false);
				}
    		};
    		TimelineHelper.atomicUpdateTimelineItem(updater);
    		baseItem = updater.getItem();
    		try{ Thread.sleep(TIMELINE_UPDATE_INTERVAL); }catch(Exception e){}
		}
		
		public void stopUpdates(){
			running = false;
		}
	}
	TimelineUpdater timelineUpdater = null;
}
