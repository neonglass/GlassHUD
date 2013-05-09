package com.appliedanalog.glass.generic;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
	final String TAG = "MainActivity";
	
	SensorManager sensorManager;
	Sensor magSensor;
	Sensor accelSensor;
	
	TextView label1;
	ImageView compassneedle;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		
		//label1 = (TextView)findViewById(R.id.label1);
		compassneedle = (ImageView)findViewById(R.id.compassneedle);
	}
	
	
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

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

	float DEFAULT_ARROW_ANGLE = 270f;
	float[] latestAccelData;
	float[] rot = new float[9];
	float[] orient = new float[3];
	float currentHeadingFactor = 0;
	
	boolean hi_lo_latch = false;
	boolean lo_hi_latch = false;
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor == magSensor){
			if(latestAccelData == null) return;
			if(sensorManager.getRotationMatrix(rot, null, latestAccelData, event.values)){
				sensorManager.getOrientation(rot, orient);
				float north_dir = DEFAULT_ARROW_ANGLE - (float)(180f * orient[0] / Math.PI);
				if(north_dir < 0f){
					north_dir += 360f;
				}
				if(north_dir > 361f){
					north_dir -= 360f;
				}
				
				if(hi_lo_latch && north_dir < 90f){
					//then we surpassed 360, add one onto the factor
					currentHeadingFactor++;
				}
				if(lo_hi_latch && north_dir > 270f){
					currentHeadingFactor--;
				}
				hi_lo_latch = north_dir > 270f;
				lo_hi_latch = north_dir < 90f;
				north_dir += 360f * currentHeadingFactor;
				
				Log.v(TAG, "Inclination determined: " + north_dir);
				compassneedle.clearAnimation();
				compassneedle.animate().rotation(north_dir).setDuration(100).setListener(aniListener);
			}
		}else{
			latestAccelData = event.values.clone();
		}
		//
	}
	
	@Override
	protected void onStart(){
		super.onStart();
		
		sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//label1.animate().x(50).y(50).setDuration(1000).setListener(aniListener);
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		sensorManager.unregisterListener(this);
	}

}
