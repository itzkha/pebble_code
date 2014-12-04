package smartdays.smartdays;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by hector on 02/12/14.
 */
public class PhoneSensorEventListener implements SensorEventListener {

    private PhoneDataBuffer dataBuffer;
    private long previousTimeStamp;

    public PhoneSensorEventListener(PhoneDataBuffer b) {
        dataBuffer = b;
        previousTimeStamp = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if ((sensorEvent.timestamp - previousTimeStamp) >= Constants.TOO_FREQUENT_MEASURES) {      // discards too frequent measures (< 16 mS)
            previousTimeStamp = sensorEvent.timestamp;

            PhoneData temp = new PhoneData(sensorEvent.timestamp, sensorEvent.values);
            dataBuffer.putData(temp);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}
