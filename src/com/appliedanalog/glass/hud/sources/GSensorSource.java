package com.appliedanalog.glass.hud.sources;

import java.text.NumberFormat;
import java.util.concurrent.Semaphore;

public class GSensorSource implements InternalSource {
	String gReading = "0.00g";
	Semaphore dataLock = new Semaphore(1);
	
	public void accelReading(float[] values){
		float mag = (float)Math.sqrt(values[0] * values[0] +
									 values[1] * values[1] +
									 values[2] * values[2]);
		mag /= 9.81; //convert to g-forces
		//compensate for error at small forces
		if(mag < .1f) mag = 0f;
		
		//set the text
		final NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(2);
		format.setMinimumFractionDigits(2);
		try{
			dataLock.acquire();
			gReading = format.format(mag) + "g";
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			dataLock.release();
		}
	}
	
	@Override
	public String getTitle() {
		return "Acceleration:";
	}

	@Override
	public String getValue1() {
		//No value1 for this sensor.
		return "";
	}

	@Override
	public String getValue2() {
		return gReading;
	}
	
}
