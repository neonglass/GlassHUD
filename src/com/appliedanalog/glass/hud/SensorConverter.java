package com.appliedanalog.glass.hud;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/**
 * Produces HTML code from Sensor input
 * @author James
 *
 */
public class SensorConverter {
	class SensorReading{
		public String sensor;
		public String reading1;
		public String reading2;
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
	
	public SensorConverter(){ }
	
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
	
	final String HEAD = "<article><section><table class=\"align-justify\"><tbody>";
	final String FOOT = "</tbody></table></section></article>";
	final String SECTION_HEAD = "<tr>";
	final String SECTION_FOOT = "</tr>";
	
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
		return "<td class=\"" + color + "\">";
	}
	final String VALUE_FOOT = "</td>";
	
	public String getHtml(){
		try{
			dataLock.acquire();
			String html = HEAD;
			Iterator<SensorReading> iter = readings.values().iterator();
			int counter = 0;
			while(iter.hasNext()){
				SensorReading reading = iter.next();
				if(!reading.stale()){
					html += SECTION_HEAD;
					html += getValueHead(counter) + reading.sensor + VALUE_FOOT;
					html += getValueHead(counter) + reading.reading1 + VALUE_FOOT;
					html += getValueHead(counter) + reading.reading2 + VALUE_FOOT;
					html += SECTION_FOOT;
					counter++;
				}
			}
			html += FOOT;
			return html;
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			dataLock.release();
		}
		return "<html>No Data</html>";
	}
}
