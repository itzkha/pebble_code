package com.zackzhu.logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.common.primitives.UnsignedInteger;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;
import android.util.JsonWriter;
import android.support.v4.content.LocalBroadcastManager;

public class LoggerService extends Service {
	private static final UUID PEBBLE_LOGGER_APP_UUID = UUID.fromString("df64aa1a-e41f-4f9c-b234-571e49e43874");
	//private static final UUID OCEAN_SURVEY_APP_UUID = UUID.fromString("0a5399d9-5693-4f3e-b768-9c99b5f5dcea");
    private PebbleKit.PebbleDataLogReceiver mDataLogReceiverByteArray = null;
    private PebbleKit.PebbleDataReceiver mActivityMarkerReceiver = null;

//    
//    private LocationManager mLocationManager;
//    private String mProviderName;
//    private boolean mLocationPending;
    
//    private final LocationListener mLocationListener = new LocationListener() {
//        @Override
//        public void onLocationChanged(Location location) {
//        		Log.i("PebbleService", "onLocationChanged");
//            try {
//				writeLocationToFile(location);
//			} catch (JSONException e) {
//				e.printStackTrace();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//            mLocationManager.removeUpdates(this);
//        }
//
//        @Override
//        public void onStatusChanged(String provider, int status, Bundle bundle) {
//        		Log.e("PebbleService", "onStatusChanged");
//        }
//
//        @Override
//        public void onProviderEnabled(String provider) {
//        		Log.e("PebbleService", "onProviderEnabled");
//        }
//
//        @Override
//        public void onProviderDisabled(String provider) {
//        		Log.e("PebbleService", "onProviderDisabled");
//
//        }
//    };
    
    /* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}
	
	public void sendWriteUpdate(String message) {
	    Intent intent = new Intent("Write-Update");
	    if(message != null)
	        intent.putExtra("message", message);
	    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
//	public void requestLocation() {
//		if (mProviderName != null && mLocationPending == false) {
//			mLocationPending = true;
//			
//			Location lastLocation = mLocationManager.getLastKnownLocation(mProviderName);
//			
//			if (lastLocation != null && (System.currentTimeMillis()-lastLocation.getTime() > DateUtils.MINUTE_IN_MILLIS)) {
//				mLocationManager.requestLocationUpdates(mProviderName,
//						10000,10,
//						mLocationListener);
//			}
//		}
//	}
//	
//	public void writeLocationToFile(Location l) throws JSONException, IOException {
//		if (mLocationPending && isExternalStorageWritable()) {
//			mLocationPending = false;
//			File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PebbleLocation.txt");
//			FileOutputStream outputStream = new FileOutputStream(file, true); 
//			OutputStreamWriter outWriter = new OutputStreamWriter(outputStream);
//			
//			
//			JSONObject locationJson = new JSONObject();
//			JSONObject coordJson = new JSONObject();
//			
//			locationJson.put("timestamp", l.getTime());
//			
//			coordJson.put("latitude", l.getLatitude());
//			coordJson.put("longitude", l.getLongitude());
//			coordJson.put("altitude", l.getAltitude());
//			coordJson.put("accuracy", l.getAccuracy());
//			coordJson.put("speed", l.getSpeed());
//			coordJson.put("heading", l.getBearing());
//			
//			locationJson.put("coords", coordJson);
//			
//			outWriter.write(locationJson.toString()+"\n");
//			Log.i("PebbleService", "Location Writing: " + locationJson.toString());
//			outWriter.close();
//			
//		}
//	}
	
	
    @Override
    public void onCreate() {
    		Toast.makeText(getApplicationContext(), "Pebble logger service starting", Toast.LENGTH_SHORT).show();
    		
//    		mLocationManager =
//                    (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
//
//    		Criteria criteria = new Criteria();
//        criteria.setAccuracy(Criteria.ACCURACY_FINE);
//        criteria.setCostAllowed(false);
//    		mProviderName = mLocationManager.getBestProvider(criteria, true);
        
    		mDataLogReceiverByteArray = new PebbleKit.PebbleDataLogReceiver(PEBBLE_LOGGER_APP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, UnsignedInteger timestamp, UnsignedInteger tag,
                                    byte [] data) {
            	
            	String lastWriteTimeString = new String(); 
            	if (isExternalStorageWritable()) { 
            		try {
            			File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PebbleAccel.csv");
                		
        				FileOutputStream outputStream = new FileOutputStream(file, true); 
        				OutputStreamWriter outWriter = new OutputStreamWriter(outputStream);
       
        				Log.i("PebbleService", "receive byte array!" + Arrays.toString(data) + " with sizeof = " + data.length);
        				
        				// initialize ByteBuffer from the byte array "data"
                    	ByteBuffer buf = ByteBuffer.wrap(data);
                    	// specify that the order is Little_Endian
                    	buf.order(ByteOrder.LITTLE_ENDIAN);
                                       	

                    	int mean_x = 0;
                    	int mean_y = 0;
                    	int mean_z = 0;
                    	int var_x = 0;
                    	int var_y = 0;
                    	int var_z = 0;
                    	int chunk_time = 0;
                		
                    	if (buf.hasRemaining()) {
                			mean_x = buf.getInt();
                		}
                    	if (buf.hasRemaining()) {
                    		mean_y = buf.getInt();
                		}
                    	if (buf.hasRemaining()) {
                    		mean_z = buf.getInt();
                		}
                		if (buf.hasRemaining()) {
                			var_x = buf.getInt();
                		}
                		if (buf.hasRemaining()) {
                			var_y = buf.getInt();
                		}
                		if (buf.hasRemaining()) {
                			var_z = buf.getInt();
                		}
                		if (buf.hasRemaining()) {
                			chunk_time = buf.getInt();
                		}
                		
//                    	int mean_x = buf.getInt();
//                    	int mean_y = buf.getInt();
//                    	int mean_z = buf.getInt();
//                    	int var_x = buf.getInt();
//                    	int var_y = buf.getInt();
//                    	int var_z = buf.getInt();
//                    	int chunk_time = buf.getInt();
//                    	
//                    	long t = 0;
//        				if(buf.hasRemaining()) {
//                        byte boolbyte = buf.get();
//                        didvibrate = (boolbyte != 0);
//                            //Log.i("PebbleService", "Before flipping: data[3] = "+didvibrate);  
//                    }
//        				
//        				if(buf.hasRemaining()) {
//                    		t = buf.getLong();
//                    		Log.i("PebbleService", "Before flipping: data[4] = " +t); 
//                    	}
//        				
//                    	
//        				String accelInstance = String.valueOf(x) + "," + String.valueOf(y) + "," + String.valueOf(z) +
//        						"," + String.valueOf(didvibrate) + "," + String.valueOf(t) + "\n";
                    	String accelInstance = String.valueOf(mean_x) + "," + String.valueOf(mean_y) + "," + String.valueOf(mean_z) + "," +
                    			String.valueOf(var_x) + "," + String.valueOf(var_y) + "," + String.valueOf(var_z) + "," + 
                    			String.valueOf(chunk_time) + "\n";
                    		Log.i("PebbleService", "Writing: " + accelInstance);
        				outWriter.write(accelInstance);
        				outWriter.close();
        				
        				// Send update back to text viewer in activity
        				String converted_time = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date (((long) chunk_time - 3600L) * 1000L));
        				sendWriteUpdate(converted_time+":\n"+accelInstance);
        			} catch (Exception e) {
        				e.printStackTrace();
        			}
            	} else {
        			Toast toast = Toast.makeText(context, "Oops, storage is not writable! Contact Zack for help.", Toast.LENGTH_SHORT);
        		
            	}
            	
            	//Log.i("PebbleService", "receive byte array!" + Arrays.toString(data) + " with sizeof = " + data.length);
            	
           

            }
        };

        mActivityMarkerReceiver = new PebbleKit.PebbleDataReceiver(PEBBLE_LOGGER_APP_UUID) {
			
			@Override
			public void receiveData(final Context context, final int transactionId,
					PebbleDictionary data) {
				File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PebbleLabels.csv");
        		
			 	if (isExternalStorageWritable()) { 
			 		try {
			 			FileOutputStream outputStream = new FileOutputStream(file, true); 
						OutputStreamWriter outWriter = new OutputStreamWriter(outputStream);
						
						String writeString = new String();
						
						if (data.getInteger(0) != null) {
							writeString = "stopAct,"+String.valueOf(data.getInteger(0)+","+
									String.valueOf(data.getUnsignedInteger(10)+"\n"));
						} else if (data.getInteger(1) != null) {
							writeString = "startAct,"+String.valueOf(data.getInteger(1)+","+
									String.valueOf(data.getUnsignedInteger(10)+"\n"));
						} else if (data.getInteger(2) != null) {
							writeString = "fuzzyStopAct,"+String.valueOf(data.getInteger(2)+","+
									String.valueOf(data.getUnsignedInteger(10)+"\n"));
						} else if (data.getInteger(3) != null) {
							writeString = "fuzzyStartAct,"+String.valueOf(data.getInteger(3)+","+
									String.valueOf(data.getUnsignedInteger(10)+"\n"));
						}
						
						outWriter.write(writeString);
						outWriter.close();
						Log.i("PebbleService", "Writing: " + writeString);	
						
						// Send update to Pebble acknowledging write succeeded
						PebbleDictionary ackMessageDict = new PebbleDictionary();
						ackMessageDict.addInt8(7, (byte) 1); // 7 is the key for phone confirmation, defined in Pebble app's appinfo.json
						PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_LOGGER_APP_UUID, ackMessageDict);
			 		} catch (Exception e) {
			 			PebbleDictionary ackMessageDict = new PebbleDictionary();
						ackMessageDict.addInt8(7, (byte) 0); // 7 is the key for phone confirmation, defined in Pebble app's appinfo.json
						PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_LOGGER_APP_UUID, ackMessageDict);
						
			 		}
			 	}
				
				PebbleKit.sendAckToPebble(context, transactionId);
				
			}
		};
        // register the handler
        PebbleKit.registerDataLogReceiver(this, mDataLogReceiverByteArray);

        // request data from the app
        PebbleKit.requestDataLogsForApp(this, PEBBLE_LOGGER_APP_UUID);
        
        PebbleKit.registerReceivedDataHandler(this, mActivityMarkerReceiver);
        
    }
    
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}

	@Override
    public void onDestroy() {

        if (mDataLogReceiverByteArray != null) {
            unregisterReceiver(mDataLogReceiverByteArray);
            mDataLogReceiverByteArray = null;
        }
        // Tell the user we stopped.
        Toast.makeText(this, "Pebble service is being destroyed", Toast.LENGTH_SHORT).show();
    }

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
