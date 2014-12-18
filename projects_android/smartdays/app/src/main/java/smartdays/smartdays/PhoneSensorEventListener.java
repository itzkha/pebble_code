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
    private long firstTimeStampElapsed;
    private long firstTimeStampReal;
    private BufferedOutputStream bufferOutPhone;

    public PhoneSensorEventListener(PhoneDataBuffer b) {
        dataBuffer = b;
        previousTimeStamp = 0;
        bufferOutPhone = null;
        firstTimeStampElapsed = System.nanoTime();
        firstTimeStampReal = System.currentTimeMillis();
    }

    public PhoneSensorEventListener(PhoneDataBuffer b, BufferedOutputStream bop) {
        dataBuffer = b;
        previousTimeStamp = 0;
        bufferOutPhone = bop;
        firstTimeStampElapsed = System.nanoTime();
        firstTimeStampReal = System.currentTimeMillis();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if ((sensorEvent.timestamp - previousTimeStamp) >= Constants.PHONE_SAMPLING_PERIOD_NS) {    // discards too frequent measures (< 20 mS)
            previousTimeStamp = sensorEvent.timestamp;

            long actualTimeStamp = firstTimeStampReal + ((sensorEvent.timestamp - firstTimeStampElapsed) / 1000000);
                                                                                                    // computing the actual timestamp -> sensorEvent.timestamp (depending on hardware)
                                                                                                    // does not yield system time but elapsed time since boot
            PhoneData temp = new PhoneData(actualTimeStamp, sensorEvent.values);
            dataBuffer.putData(temp);

            if (bufferOutPhone != null) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.PACKET_SIZE);
                byteBuffer.putLong(actualTimeStamp);
                for (int i = 0; i < 3; i++) {
                    byteBuffer.putShort((short) (100 * sensorEvent.values[i]));
                }
                try {
                    bufferOutPhone.write(byteBuffer.array());
                } catch (IOException ioe) {
                }
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}
