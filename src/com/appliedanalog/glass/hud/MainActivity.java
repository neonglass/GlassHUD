package com.appliedanalog.glass.hud;

import java.text.NumberFormat;

import com.appliedanalog.glass.hud.sources.CompassSource;
import com.appliedanalog.glass.hud.sources.GSensorSource;
import com.google.glass.location.GlassLocationManager;
import com.google.glass.timeline.TimelineHelper;
import com.google.glass.timeline.TimelineProvider;
import com.google.glass.util.SettingsSecure;
import com.google.googlex.glass.common.proto.TimelineItem;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {
	final String TAG = "MainActivity";

    //UI Elements
	Activity me;
    Button bEnableHUD;
    Button bWakeLock;
    TextView tConnectedToPhone;
    
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Set up UI
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		bEnableHUD = (Button)findViewById(R.id.cEnableUpdates);
		bWakeLock = (Button)findViewById(R.id.cEnableWakeLock);
		tConnectedToPhone = (TextView)findViewById(R.id.tConnectedToHost);
		me = this;
		
		//And bind actions
		bEnableHUD.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				if(bound && hudBinder.running()){
					hudBinder.shutdown();
				}else if(bound){
					hudBinder.startup();
				}
				updateTextFields();
			}
		});
		bWakeLock.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				if(bound && hudBinder.wakeLockObtained()){
					hudBinder.releaseWakeLock();
				}else if(bound){
					hudBinder.getWakeLock();
				}
				updateTextFields();
			} 
		});
	}
	
	private void updateTextFields(){
		if(bound){
			if(hudBinder.running()){
				bEnableHUD.setText("Turn off HUD");
			}else{
				bEnableHUD.setText("Turn on HUD");
			}
			if(hudBinder.wakeLockObtained()){
		    	bWakeLock.setText("Let Screen Off");
			}else{
		    	bWakeLock.setText("Keep Screen On");
			}
		}
	}

	GlassHUDService.HUDBinder hudBinder;
	boolean bound = false;
	ServiceConnection mConnection = new ServiceConnection(){
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.v(TAG, "ServiceConnected");
			hudBinder = (GlassHUDService.HUDBinder)service;
			bound = true;
			updateTextFields();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.v(TAG, "ServiceDisconnected");
			bound = false;
		}
	};
	
	@Override
	public void onStart(){
		super.onStart();
		startService(new Intent(this, GlassHUDService.class));
		Intent sIntent = new Intent(this, GlassHUDService.class);
		bindService(sIntent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onStop(){
		super.onStop();
		if(bound){
			this.unbindService(mConnection);
		}
	}
}
