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

	float[] latestAccelData;
	float[] rot = new float[9];
	float[] orient = new float[3];
	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor == magSensor){
			Log.v(TAG, "Mag sensor reading.");
			if(latestAccelData == null) return;
			if(sensorManager.getRotationMatrix(rot, null, latestAccelData, event.values)){
				sensorManager.getOrientation(rot, orient);
				float maginc = 180f + (float)(180f * orient[1] / Math.PI);
				Log.v(TAG, "Inclination determined: " + maginc);
				compassneedle.clearAnimation();
				compassneedle.animate().rotation(maginc).setDuration(100).setListener(aniListener);
			}
		}else{
			Log.v(TAG, "Accell sensor reading.");
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
