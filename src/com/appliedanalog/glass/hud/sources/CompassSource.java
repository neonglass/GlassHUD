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

import java.util.concurrent.Semaphore;

public class CompassSource implements InternalSource{
	//only display average of every 20 readings
	double[] headingReadings = new double[20];
	int readingNo = 0;
	
	String headingAngle = "0°";
	String compassDirection = "N";
	Semaphore dataLock = new Semaphore(1);
	
	/**
	 * This implementation only really works when glass is perfectly perpendicular to the ground.
	 * Unfortunately, the orientation algorithm built into android does not work very well in
	 * Glass so I couldnt use that.
	 * @param sense raw magnetometer data.
	 */
	public void magReading(float[] sense){
		double senseNS = -sense[2]; //N is positive
		double senseWE = -sense[0]; //W is positive
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
			
			try{
				dataLock.acquire();
				//set the text
				headingAngle = ((int)mean) + "°";
				compassDirection = getHeadingString(mean);
			}catch(Exception e){
				e.printStackTrace();
			}finally{
				dataLock.release();
			}
		}
	}

	@Override
	public String getTitle() {
		return "Compass";
	}

	@Override
	public String getValue1() {
		return compassDirection;
	}

	@Override
	public String getValue2() {
		return headingAngle;
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
	
}
