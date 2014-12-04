package smartdays.smartdays;

import android.content.Context;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by hector on 03/12/14.
 */
public class SmartDaysPebbleDataLogReceiver extends PebbleKit.PebbleDataLogReceiver {

    private int packetCounter = 0;
    private long offsetFromUTC;
    private BufferedOutputStream bufferOutPebble;
    private BufferedOutputStream bufferOutPhone;
    private PhoneDataBuffer phoneDataBuffer;

    public SmartDaysPebbleDataLogReceiver(UUID app, BufferedOutputStream bope, BufferedOutputStream boph, PhoneDataBuffer pdf) {
        super(app);
        TimeZone tz = TimeZone.getDefault();
        offsetFromUTC = tz.getOffset(new Date().getTime());
        bufferOutPebble = bope;
        bufferOutPhone = boph;
        phoneDataBuffer = pdf;
    }

    @Override
    public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
        if (tag.intValue() == Constants.DATA_LOG_TAG_ACCEL) {
            try {
                // Get the acceleration value
                long timestampData = ByteBuffer.wrap(data, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong() - offsetFromUTC;
                Log.d("SmartDAYS", "Packets received: " + String.valueOf(packetCounter + 1) + " : " + String.valueOf(timestampData));

                bufferOutPebble.write(data);
                for (int i = 0; i < Constants.PEBBLE_BUFFER_SIZE; i++) {
                    bufferOutPhone.write(phoneDataBuffer.getMatchingData(timestampData + (i * Constants.PEBBLE_SAMPLING_PERIOD)));
                }
                packetCounter++;
            } catch (IOException ioe) {
                Log.d("SmartDAYS", "Error writing data...");
            }
        }
    }
}
