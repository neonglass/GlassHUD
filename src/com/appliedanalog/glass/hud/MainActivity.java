package com.appliedanalog.glass.hud;

import java.text.NumberFormat;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.app.Activity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
	final String TAG = "MainActivity";
	
	SensorManager sensorManager;
	Sensor magSensor;
	Sensor gSensor;
    private PowerManager.WakeLock wakelock;	
	
	TextView compasstext;
	TextView acceltext;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		gSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		
		compasstext = (TextView)findViewById(R.id.compasstext);
		acceltext = (TextView)findViewById(R.id.bartext);
	}


	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

	float DEFAULT_ARROW_ANGLE = 270f;
	float[] latestAccelData;
	float[] rot = new float[9];
	float[] orient = new float[3];
	
	//only display average of every 20 readings
	double[] headingReadings = new double[20];
	int readingNo = 0;
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor == magSensor){
			double senseNS = -event.values[2]; //N is positive
			double senseWE = -event.values[0]; //W is positive
			double angle = 0f;
			if(senseNS == 0){
				if(senseWE > 0){
					angle = 90f;
				}else{
					angle = 270f;
				}
			}else{
				angle = Math.atan(senseWE / senseNS);
				if(senseNS > 0 && senseWE < 0){
					angle += 2 * Math.PI;
				}else if(senseNS < 0){
					angle += Math.PI;
				}
			}
			angle = angle * 180 / Math.PI;
			
			headingReadings[readingNo] = angle;
			readingNo++;
			if(readingNo == headingReadings.length){
				double mean = 0.;
				for(int x = 0; x < headingReadings.length; x++) mean += headingReadings[x];
				mean /= headingReadings.length;
				readingNo = 0;
				
				//set the text
				String headingStr = ((int)mean) + "° " + getHeadingString(mean);
	            Message msg = handler.obtainMessage(COMPASS_VALUE_CHANGED);
	            Bundle bundle = new Bundle();
	            bundle.putString(NEW_VALUE, headingStr);
	            msg.setData(bundle);
	            handler.sendMessage(msg);
			}
		}else if(event.sensor == gSensor){
			float mag = (float)Math.sqrt(event.values[0] * event.values[0] +
										 event.values[1] * event.values[1] +
										 event.values[2] * event.values[2]);
			mag /= 9.81; //convert to g-forces
			//compensate for error at small forces
			if(mag < .1f) mag = 0f;
			
			//set the text
			final NumberFormat format = NumberFormat.getNumberInstance();
			format.setMaximumFractionDigits(2);
			format.setMinimumFractionDigits(2);
            Message msg = handler.obtainMessage(BAR_VALUE_CHANGED);
            Bundle bundle = new Bundle();
            bundle.putString(NEW_VALUE, format.format(mag));
            msg.setData(bundle);
            handler.sendMessage(msg);
		}
	}
	
	@Override
	protected void onStart(){
		super.onStart();
		Log.v(TAG, "HUD Activity -> Start");
		
		sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, gSensor, SensorManager.SENSOR_DELAY_NORMAL);
		
		//acquire wakelock
    	PowerManager powman = (PowerManager)getSystemService(Context.POWER_SERVICE);
    	wakelock = powman.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "HUD");
    	wakelock.acquire();
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		Log.v(TAG, "HUD Activity -> Stop");
		sensorManager.unregisterListener(this);
    	wakelock.release();
	}

	private String getHeadingString(double head){
		while(head < 0){
			head += 360;
		}
		int comphead = (int)head % 360;
		if(comphead < 30){
			return "N";
		}
		if(comphead < 60){
			return "NW";
		}
		if(comphead < 120){
			return "W";
		}
		if(comphead < 150){
			return "SW";
		}
		if(comphead < 210){
			return "S";
		}
		if(comphead < 240){
			return "SE";
		}
		if(comphead < 300){
			return "E";
		}
		if(comphead < 330){
			return "NE";
		}
		return "N";
	}
	
	//Handler constants
	private final int COMPASS_VALUE_CHANGED = 5332;
	private final int BAR_VALUE_CHANGED = 5333;
	private final String NEW_VALUE = "value";
    private Handler handler = new Handler(){
    	public void handleMessage(Message msg){
    		switch(msg.what){
    		case COMPASS_VALUE_CHANGED:
    			compasstext.setText(msg.getData().getString(NEW_VALUE));
    			break;
    		case BAR_VALUE_CHANGED:
    			acceltext.setText(msg.getData().getString(NEW_VALUE));
    			break;
    		}
    	}
    };
	
	AnimatorListener aniListener = new AnimatorListener(){
		@Override
		public void onAnimationEnd(Animator animation) {
		}
		
		@Override
		public void onAnimationCancel(Animator animation) { }

		@Override
		public void onAnimationRepeat(Animator animation) { }

		@Override
		public void onAnimationStart(Animator animation) { }
	};
}
