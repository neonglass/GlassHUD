package com.appliedanalog.glass.generic;

import java.text.NumberFormat;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
	Sensor accelSensor;
	Sensor gSensor;
	
	
	TextView label1;
	TextView compasstext;
	ImageView compassneedle;
	TextView bartext;
	ImageView barmeter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		gSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		
		//label1 = (TextView)findViewById(R.id.label1);
		compassneedle = (ImageView)findViewById(R.id.compassneedle);
		compasstext = (TextView)findViewById(R.id.compasstext);
		barmeter = (ImageView)findViewById(R.id.barmeter);
		bartext = (TextView)findViewById(R.id.bartext);
	}


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
				
				compassneedle.clearAnimation();
				compassneedle.animate().rotation(north_dir).setDuration(100).setListener(aniListener);
				
				//set the text
	            Message msg = handler.obtainMessage(COMPASS_VALUE_CHANGED);
	            Bundle bundle = new Bundle();
	            bundle.putString(NEW_VALUE, getHeadingString(north_dir));
	            msg.setData(bundle);
	            handler.sendMessage(msg);
			}
		}else if(event.sensor == accelSensor){
			latestAccelData = event.values.clone();
		}else if(event.sensor == gSensor){
			float mag = (float)Math.sqrt(event.values[0] * event.values[0] +
										 event.values[1] * event.values[1] +
										 event.values[2] * event.values[2]);
			mag /= 9.81; //convert to g-forces
			
			//max barmeter height is 300px, max detectable accell is 2g, do the conversion.
			float perc = (mag / 2f);
			Log.v(TAG, "Accelleration magnitude: " + mag + " percent: " + perc);
			//we also need to reposition the bar since the scale squashes the bar from both ends.
			int barheight = (int)(perc * 300f);
			//by default the bar will be centered on the squash, so we need to calculate how much damage half of the squash is doing and compensate for that.
			barheight /= 2;
			barmeter.clearAnimation();
			barmeter.animate().scaleY(perc).y(190 - barheight).setDuration(100).setListener(aniListener);
			
			//set the text
			final NumberFormat format = NumberFormat.getNumberInstance();
			format.setMaximumFractionDigits(1);
			format.setMinimumFractionDigits(1);
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
		
		sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, gSensor, SensorManager.SENSOR_DELAY_NORMAL);
		//label1.animate().x(50).y(50).setDuration(1000).setListener(aniListener);
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		sensorManager.unregisterListener(this);
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
			return "NE";
		}
		if(comphead < 120){
			return "E";
		}
		if(comphead < 150){
			return "SE";
		}
		if(comphead < 210){
			return "S";
		}
		if(comphead < 240){
			return "SW";
		}
		if(comphead < 300){
			return "W";
		}
		if(comphead < 330){
			return "NW";
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
    			bartext.setText(msg.getData().getString(NEW_VALUE));
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
