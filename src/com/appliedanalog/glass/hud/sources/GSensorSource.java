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
		return "Acceleration";
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
