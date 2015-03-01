package smartdays.smartdays;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;


/**
 * Created by hector on 02/12/14.
 */
public class PhoneSensorEventListener implements SensorEventListener {

    private PhoneDataBuffer dataBuffer;
    private long firstTimeStampElapsed;
    private long nextTimeStampElapsed;
    private long firstTimeStampReal;
    private BufferedOutputStream bufferOutPhone;
    private boolean firstTime;
    private int sampleCounter;
    private ByteBuffer byteBuffer;

    private PhoneData currentSample;
    private PhoneData previousSample;

    public PhoneSensorEventListener(PhoneDataBuffer b, BufferedOutputStream bop) {
        dataBuffer = b;
        bufferOutPhone = bop;
        firstTime = true;
        sampleCounter = 0;
        byteBuffer = null;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (firstTime) {
            firstTimeStampElapsed = sensorEvent.timestamp;
            firstTimeStampReal = System.currentTimeMillis();
            nextTimeStampElapsed = firstTimeStampElapsed + Constants.PHONE_SAMPLING_PERIOD_NS;
            currentSample = new PhoneData(firstTimeStampElapsed, sensorEvent.values);
            firstTime = false;
        }
        else {
            previousSample = currentSample;
            currentSample = new PhoneData(sensorEvent.timestamp, sensorEvent.values);

            if ((currentSample.getTimeStamp() >= nextTimeStampElapsed) && (nextTimeStampElapsed >= previousSample.getTimeStamp())) {
                PhoneData bestSample;

                if ( (currentSample.getTimeStamp() - nextTimeStampElapsed) > (nextTimeStampElapsed - previousSample.getTimeStamp()) ) {
                    bestSample = previousSample;
                }
                else {
                    bestSample = currentSample;
                }

                nextTimeStampElapsed = currentSample.getTimeStamp() + Constants.PHONE_SAMPLING_PERIOD_NS;

                long actualTimeStamp = firstTimeStampReal + ((bestSample.getTimeStamp() - firstTimeStampElapsed) / 1000000);
                // computing the actual timestamp -> sensorEvent.timestamp (depending on hardware)
                // does not yield system time but elapsed time since boot
                //long actualTimeStamp = System.currentTimeMillis() + ((sensorEvent.timestamp - System.nanoTime()) / 1000000L);
                //Log.d("SmartDAYS", "timestamp=" + String.valueOf(actualTimeStamp) + " firstTMS=" + String.valueOf(firstTimeStampReal) + " sensorT=" + String.valueOf(sensorEvent.timestamp) + " firstTNS=" + String.valueOf(firstTimeStampElapsed));

                if (dataBuffer != null) {
                    dataBuffer.putData(bestSample);
                }
                if (bufferOutPhone != null) {
                    if ((sampleCounter % 25) == 0) {
                        byteBuffer = ByteBuffer.allocate(Constants.PACKET_SIZE);
                        byteBuffer.putLong(actualTimeStamp);
                    }
                    for (int i = 0; i < 3; i++) {
                        byteBuffer.putShort(bestSample.getXYZ()[i]);
                    }
                    sampleCounter++;
                    if ((sampleCounter % 25) == 0) {
                        try {
                            bufferOutPhone.write(byteBuffer.array());                                   // write Phone signals  (timestamp + data)
                        } catch (IOException ioe) {
                            Log.d("SmartDAYS", "Error writing phone data...");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}
