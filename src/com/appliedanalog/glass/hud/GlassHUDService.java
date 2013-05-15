/*
    GlassHUD - Heads Up Display for Google Glass
    Copyright (C) 2013 James Betker

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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

/**
 * A persistant service that maintains a card on the Glass timeline. When turned
 * "on", this service pushes sensor data from a variety of sources onto this card.
 * @author betker
 *
 */
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
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startid){
		super.onStartCommand(intent, flags, startid);
				
		return START_STICKY;
	}
	
	/**
	 * The Binder class for interfacing between the controller activity and this service.
	 * @author betker
	 *
	 */
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
		
		/**
		 * Calling this method will tell the service to grab the wake lock, permanently keeping the screen on until
		 * releaseWakeLock() is called.
		 */
		public void getWakeLock(){
	    	PowerManager powman = (PowerManager)getSystemService(Context.POWER_SERVICE);
	    	wakelock = powman.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "HUD");
	    	wakelock.acquire();
		}
		
		/**
		 * Releases the wake lock obtained in getWakeLock().
		 */
		public void releaseWakeLock(){
	    	wakelock.release();
	    	wakelock = null;
		}
		
		/**
		 * Returns true if a companion device (phone) that is capable of serving
		 * sensor data is connected.
		 */
		public boolean isBtConnected(){
			return btComm.connected();
		}
		
		/**
		 * The client is responsible for unregistering itself by calling this function with null when it goes out of context..
		 * Failing to do so will cause the service (or at least the bluetooth binding functionality) to crash.
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
	
	/**
	 * This service only handles two sensors internal to glass: the magnetometer and the accelerometer.
	 * The rest of the data comes from the companion device.
	 */
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
	
	/**
	 * Starts up the sensors/bluetooth connection and begins pushing data to the timeline.
	 */
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
	
	/**
	 * Shuts down sensors/bluetooth.
	 */
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
	
	//Shared preference name for the timeline element ID being controlled.
	final String SPREF_STORED_TIMELINE_ID = "storedTimelineId";
	TimelineItem baseItem = null;
	
	/**
	 * This class is responsible for maintaining and updating the timeline card that we are posting our data to.
	 * @author betker
	 */
	class TimelineUpdater extends Thread{
		boolean running = false;
		
		@Override
		public void run(){
			running = true;
	    	ContentResolver cr = getContentResolver();
	    	//For some reason an TimelineHelper instance is required to call some methods.
	    	final TimelineHelper tlHelper = new TimelineHelper();
	    	
	    	//baseItem==null only on the first startupHUD() of this service.
	    	if(baseItem == null){
	    		//We store the timeline ID used by the HUD service inside of the shared preferences. We should re-use this timeline
	    		//card if at all possible. This prevents us from spamming the users timeline with HUD cards.
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
			    	ntib.setHtml(SensorDataSink.HUD_OFF_STRING);
			    	baseItem = ntib.build();
			    	ContentValues vals = TimelineHelper.toContentValues(baseItem);
			    	cr.insert(TimelineProvider.TIMELINE_URI, vals);
			    	
			    	//also insert the new ID into the sharedprefs.
			    	SharedPreferences.Editor editor = pref.edit();
			    	editor.putString(SPREF_STORED_TIMELINE_ID, baseItem.getId());
			    	editor.commit();
	    		}
	    	}
	    	
	    	//In the thread loop we continuously update the card we control with sensor data.
			while(running){
				//Updating is handled in a special context maintained by TimelineHelper,
				//we just provide an Updater callback.
        		TimelineHelper.Update updater = new TimelineHelper.Update(){
    				@Override
    				public TimelineItem onExecute() {
    		    		TimelineItem.Builder builder = TimelineItem.newBuilder(baseItem);
    		    		builder.setHtml(senseConverter.getHtml());
    		    		
    		    		//Keep the card pinned while the service is alive.
    		    		builder.setIsPinned(true);
    		    		builder.setPinTime(System.currentTimeMillis());
    		    		
    		    		//I still haven't figured out quite what the last two booleans here do..
    		    		return tlHelper.updateTimelineItem(me, builder.build(), null, true, false);
    				}
        		};
        		//Send the callback off to the thread. This should block until the update is complete.
        		TimelineHelper.atomicUpdateTimelineItem(updater);
        		//Make sure to record the new TimelineItem.
        		baseItem = updater.getItem();
        		//Wait for the <interval> before the next update.
        		try{ Thread.sleep(TIMELINE_UPDATE_INTERVAL); }catch(Exception e){}
			}
			
			//The service is shutting down. We should un-pin the card.
    		TimelineHelper.Update updater = new TimelineHelper.Update(){
				@Override
				public TimelineItem onExecute() {
		    		TimelineItem.Builder builder = TimelineItem.newBuilder(baseItem);
		    		builder.setHtml(SensorDataSink.HUD_OFF_STRING);
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
