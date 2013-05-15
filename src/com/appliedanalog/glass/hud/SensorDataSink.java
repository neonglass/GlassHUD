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

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Produces HTML code from Sensor input
 * @author James
 *
 */
public class SensorDataSink {
	class SensorReading{
		public String sensor;
		public String reading1; //Generally less important flavor text.
		public String reading2; //Generally the actual reading.
		public long timestamp;
		
		final int STALE_PERIOD = 2000; //2 seconds until a sensor reading is stale.
		
		public SensorReading(String s, String r1, String r2){
			sensor = s; reading1 = r1; reading2 = r2;
			timestamp = System.currentTimeMillis();
		}
		public void update(String r1, String r2){
			reading1 = r1; reading2 = r2;
			timestamp = System.currentTimeMillis();
		}
		
		public boolean stale(){
			return (System.currentTimeMillis() - timestamp) > STALE_PERIOD;
		}
	}
	HashMap<String, SensorReading> readings = new HashMap<String, SensorReading>();
	Semaphore dataLock = new Semaphore(1);
	String[] filter = null;
	
	public SensorDataSink(){ }
	
	/**
	 * The filter in this case controls what is displayed and the order in which
	 * it is displayed.
	 * @param fil
	 */
	public void applyFilter(String[] fil){
		filter = fil;
	}
	
	public void sensorReading(String name, String val1, String val2){
		SensorReading reading = readings.get(name);
		try{
			dataLock.acquire();
			if(reading == null){
				readings.put(name, new SensorReading(name, val1, val2));
			}else{
				reading.update(val1, val2);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			dataLock.release();
		}
	}

	//Should be in the resources file, so sue me.
	public static final String HUD_OFF_STRING = "<html><article><section><b>Glass HUD is Off</b></section></article></html>";
	final String HEAD = "<article><section><table class=\"align-justify\">";
	final String FOOT = "</table></section></article>";
	final String SECTION_HEAD = "<tr>";
	final String SECTION_FOOT = "</tr>";
	
	/**
	 * Controls the color scheme of the different columns.
	 * @param mod
	 * @return
	 */
	String getValueHead(int mod){
		String color = "white";
		switch(mod){
		case 0:
			color = "red";
			break;
		case 1:
			color = "green";
			break;
		case 2:
			color = "blue";
			break;
		}
		return "<td class=\"text-x-small var " + color + "\">";
	}
	final String VALUE_FOOT = "</td>";
	
	private String wrapReading(SensorReading reading, int counter){
		StringBuffer html = new StringBuffer(100);
		html.append(SECTION_HEAD);
		html.append(getValueHead(counter) + reading.sensor + VALUE_FOOT);
		html.append(getValueHead(counter) + reading.reading1 + VALUE_FOOT);
		html.append(getValueHead(counter) + reading.reading2 + VALUE_FOOT);
		html.append(SECTION_FOOT);
		return html.toString();
	}
	
	/**
	 * Translates the sensor readings stored in this sink into HTML that is pretty.
	 * @return
	 */
	public String getHtml(){
		try{
			dataLock.acquire();
			StringBuffer html = new StringBuffer();
			html.append(HEAD);
			if(filter == null){
				//If there is no filter, just put together a listing of all non-stale values.
				Iterator<SensorReading> iter = readings.values().iterator();
				int counter = 0;
				while(iter.hasNext()){
					SensorReading reading = iter.next();
					if(!reading.stale()){
						counter++;
						html.append(wrapReading(reading, counter));
					}
				}
				html.append(FOOT);
			}else{
				//Otherwise, list the filtered sensors.
				for(int x = 0; x < filter.length; x++){
					SensorReading reading = readings.get(filter[x]);
					if(reading == null) continue;
					html.append(wrapReading(reading, x));
				}
			}
			return html.toString();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			dataLock.release();
		}
		return HUD_OFF_STRING;
	}
}
