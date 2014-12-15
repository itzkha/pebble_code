package smartdays.smartdays;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * Created by hector on 02/12/14.
 */
public class PhoneSensorEventListener implements SensorEventListener {

    private PhoneDataBuffer dataBuffer;
    private long previousTimeStamp;
    private BufferedOutputStream bufferOutPhone;

    public PhoneSensorEventListener(PhoneDataBuffer b) {
        dataBuffer = b;
        previousTimeStamp = 0;
    }

    public PhoneSensorEventListener(PhoneDataBuffer b, BufferedOutputStream bop) {
        dataBuffer = b;
        previousTimeStamp = 0;
        bufferOutPhone = bop;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long systemTimeStamp = System.currentTimeMillis();                                          // getting system time here because sensorEvent.timestamp (depending on hardware)
                                                                                                    // does not yield system time but elapsed time since boot

        if ((systemTimeStamp - previousTimeStamp) >= Constants.PHONE_SAMPLING_FREQUENCY) {             // discards too frequent measures (< 20 mS)
            previousTimeStamp = systemTimeStamp;

            PhoneData temp = new PhoneData(systemTimeStamp, sensorEvent.values);
            dataBuffer.putData(temp);

            ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.PACKET_SIZE);
            byteBuffer.putLong(systemTimeStamp);
            for (int i = 0; i < 3; i++) {
                byteBuffer.putShort((short) (100*sensorEvent.values[i]));
            }
            try {
                bufferOutPhone.write(byteBuffer.array());
            }
            catch (IOException ioe) {
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}
