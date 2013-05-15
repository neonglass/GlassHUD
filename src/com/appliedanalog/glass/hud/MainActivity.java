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

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.widget.Button;
import android.widget.TextView;

/**
 * The MainActivity for this app is simply a controller for the backing Service. The
 * Service then provides the UI through the Glass Timeline. This activity allows the
 * user to turn Service functionality on and off and lock the screen in a permanently
 * on state.
 * @author betker
 */
public class MainActivity extends Activity implements PhoneSensorComm.BTStateListener {
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
			if(hudBinder.isBtConnected()){
				tConnectedToPhone.setText("Connected to phone");
				tConnectedToPhone.setTextColor(0xff00ff00); //Green
			}else{
				tConnectedToPhone.setText("Not connected to phone");
				tConnectedToPhone.setTextColor(0xffff0000); //Red
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
			hudBinder.setBTListener((PhoneSensorComm.BTStateListener)me); //don't know why this cast is necessary..
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
			hudBinder.setBTListener(null);
			this.unbindService(mConnection);
		}
	}

	final int UPDATE_TEXT_FIELDS = 32832;
    private Handler handler = new Handler(){
    	public void handleMessage(Message msg){
    		switch(msg.what){
    		case UPDATE_TEXT_FIELDS:
    			updateTextFields();
    			break;
    		}
    	}
    };
    
    /**
     * Callback from the bluetooth manager that indicates
     * when a HUD server on a companion device is connected
     * or disconnected.
     */
	@Override
	public void btStatusChanged(boolean state) {
		Message msg = handler.obtainMessage();
		msg.what = UPDATE_TEXT_FIELDS;
		handler.sendMessage(msg);
	}
}
