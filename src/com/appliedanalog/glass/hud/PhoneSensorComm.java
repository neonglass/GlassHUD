package com.appliedanalog.glass.hud;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * Attempts to establish and maintain a bluetooth connection with the Android phone paired to
 * this glass. If successful, pushes phone sensor data into the sink.
 * @author betker
 *
 */
public class PhoneSensorComm implements Runnable {
	final String TAG = "PhoneSensorComm";
    private static final UUID MY_UUID =
            UUID.fromString("fa8730d5-afac-11df-8739-0310210c9366");
    final int DEFAULT_RECONNECT_PERIOD = 2000; //2 seconds, doubled until over 5 minutes
    final int MAX_RECONNECT_PERIOD = 300000; //5 minutes
    
	SensorDataSink sink;
	BluetoothAdapter btAdapter;
	BluetoothDevice devPhone;
	boolean running;
	boolean connected;
	int reconnectAttemptTime = DEFAULT_RECONNECT_PERIOD;
	
	public interface BTStateListener{
		public void btStatusChanged(boolean state);
	}
	BTStateListener btListener = null;
	
	public PhoneSensorComm(SensorDataSink sds){
		sink = sds;
		running = false;
		connected = false;
	}
	
	public void setStateListener(BTStateListener sl){
		btListener = sl;
	}
	
	public boolean connected(){
		return connected;
	}
	
    public void startup(){
    	Log.v(TAG, "Starting up BT connection.");
    	
    	//reset the reconnect period
    	reconnectAttemptTime = DEFAULT_RECONNECT_PERIOD;
    	
    	//start up bluetooth.
    	btAdapter = BluetoothAdapter.getDefaultAdapter();
    	if(btAdapter == null){
    		//what kind of glass is this??
    		Log.v(TAG, "Cannot obtain BluetoothAdapter handle, aborting.");
    	}
    	Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
    	if(devices.size() > 1){
    		Log.v(TAG, "There are more than one phones paired to this glass.. I'm just going to use the first one.");
    	}
    	if(devices.size() <= 0){
    		Log.v(TAG, "No devices paired to this glass, quitting.");
    		System.exit(1);
    	}
    	devPhone = devices.iterator().next();
    	Log.v(TAG, "Using BT connection to " + devPhone.getName() + "{" + devPhone.getAddress() + "}");

    	//startup thread
    	(new Thread(this)).start();
    }
    
    public void stop(){
    	Log.v(TAG, "Shutting down BT connection.");
    	running = false;
    	if(socket != null){
    		try{
    			socket.close();
    		}catch(Exception e){}
    	}
    }
    
    /**
     * The incoming sensor data will have the following format:
     * {name}><{value1}><{value2}
     * @param line
     */
    private void parsePhoneSensorData(String line){
    	String[] spl = line.split("><");
    	if(spl.length != 3){
    		Log.v(TAG, "Invalid sensor data in: '" + line + "'");
    	}
    	sink.sensorReading(spl[0], spl[1], spl[2]);
    }
    
    BluetoothSocket socket = null;
    public void run(){
    	running = true;
    	while(running){
			try{
				socket = devPhone.createRfcommSocketToServiceRecord(MY_UUID);
				Log.v(TAG, "attempting connection...");
				socket.connect();
				Log.v(TAG, "bluetooth connected.");

				connected = true;
				if(btListener != null) btListener.btStatusChanged(true);
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String line = null;
				while((line = reader.readLine()) != null){
					parsePhoneSensorData(line);
				}
				Log.v(TAG, "falling out of readLine loop..");
				socket.close();
			}catch(Exception e){
				Log.v(TAG, "bluetooth connection terminated/failed: " + e.getMessage());
			}
			//failed connection attempts AND closed connections fall out here.
			if(!running){
				connected = false;
				if(btListener != null) btListener.btStatusChanged(false);
				return;
			}
			if(connected){ //we WERE connected
				connected = false;
				if(btListener != null) btListener.btStatusChanged(false);
				
				//reset connection attempt interval to default period
				reconnectAttemptTime = DEFAULT_RECONNECT_PERIOD;
			}else{
				try{
					Log.v(TAG, "Will attempt a reconnect in " + (reconnectAttemptTime / 1000) + " seconds");
					Thread.sleep(reconnectAttemptTime);
					//wait longer if we fail a consecutive time..
					reconnectAttemptTime *= 2;
					if(reconnectAttemptTime > MAX_RECONNECT_PERIOD){
						reconnectAttemptTime = MAX_RECONNECT_PERIOD;
					}
				}catch(Exception e){}
			}
    	}
    }
    
}
