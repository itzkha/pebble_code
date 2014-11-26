package smartdays.testlogging_4;

// Works with test_accel_4 on the Pebble
// Saves the buffer to a file "testCapture"


import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.UUID;

import java.io.File;
import java.io.FileOutputStream;

import android.os.Environment;

public class MainActivity extends Activity {

    // Configuration
    private static final UUID WATCHAPP_UUID = UUID.fromString("e92cf086-2ec1-4814-b360-340db0da9aa6");
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
            textView.setText("Waiting for logging data...\nFile testCapture has been created");
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

//            @Override
//            public void onReceive(Context context, Intent intent) {
//                Log.d("commPebble", intent.getSerializableExtra("uuid").toString());
//                super.onReceive(context, intent);
//            }

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    try {
                        // Get the acceleration value
                        if (bufferOut != null) bufferOut.write(data);
                        textView.setText("Timestamp: " + String.valueOf(timestamp));
                    }
                    catch (IOException ioe) {
                        textView.setText("Error writing data...");
                    }
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
