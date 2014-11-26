package smartdays.testlogging_3;

// Works with test_accel_3 on the Pebble
// Saves the buffer to a file "testCapture"

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import java.io.File;
import java.io.FileOutputStream;

import android.os.Environment;
import android.util.Log;

public class MainActivity extends Activity {

    // Configuration
    private static final UUID WATCHAPP_UUID = UUID.fromString("24fbe952-c30a-4c7f-ac50-5ab72bdf8aca");
    private static final int DATA_LOG_TAG_ACCEL = 51;

    // App elements
    private PebbleDataLogReceiver dataloggingReceiver;
    private TextView textView;
    private BufferedOutputStream bufferOut = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup TextView
        textView = (TextView)findViewById(R.id.text_view);
        textView.setText("Waiting for logging data...");

        // Setup file
        try {
            File root = Environment.getExternalStorageDirectory();
            FileOutputStream f = new FileOutputStream(new File(root, "testCapture"));

            bufferOut = new BufferedOutputStream(f);
            //bufferOut = new BufferedOutputStream(openFileOutput("testCapture", Context.MODE_PRIVATE));
        }
        catch (IOException ioe) {
            textView.setText("Error creating file...");
        }


    }

    @Override
    protected void onResume() {
        super.onResume();

        // Define data reception behavior
        dataloggingReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID) {

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    try {
                        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        // Get the acceleration value
                        long timestamp_data = byteBuffer.getLong(0);    //8 bytes of timestamp
                        int x = byteBuffer.getShort(8);                   //2 bytes of x
                        int y = byteBuffer.getShort(10);                  //2 bytes of y
                        int z = byteBuffer.getShort(12);                  //2 bytes of z

                        Log.d("comPebble", String.valueOf(x) + " " + String.valueOf(y) + " " + String.valueOf(z));

                        if (bufferOut != null) bufferOut.write(data);
                        textView.setText("Timestamp: " + String.valueOf(timestamp));
                    }
                    catch (IOException ioe) {
                        textView.setText("Error writing data...");
                    }
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);

                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    textView.setText("End. Timestamp: " + timestamp.toString());
                }
            }

        };

        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Always unregister callbacks
        if(dataloggingReceiver != null) {
            unregisterReceiver(dataloggingReceiver);
        }
        try {
            if (bufferOut != null) bufferOut.close();
        }
        catch (IOException ioe) {
        }
    }
}
