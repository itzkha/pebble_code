package smartdays.testlogging_1;

// Works with test_accel_3 on the Pebble
// Shows the buffer

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
import android.content.Intent;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;


public class MainActivity extends Activity {

    // Configuration
    private static final UUID WATCHAPP_UUID = UUID.fromString("24fbe952-c30a-4c7f-ac50-5ab72bdf8aca");
    private static final int DATA_LOG_TAG_ACCEL = 51;

    // App elements
    private PebbleDataLogReceiver dataloggingReceiver;
    private TextView textView;
    private StringBuilder resultBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup TextView
        textView = (TextView)findViewById(R.id.text_view);
        textView.setText("Waiting for logging data...");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Define data reception behavior
        dataloggingReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID) {

//            @Override
//            public void onReceive(Context context, Intent intent) {
//                textView.setText(intent.getSerializableExtra("uuid").toString());
//            }

//            @Override
//            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, int data) {
//                textView.setText("int");
//            }

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    // Get the acceleration value and append to result StringBuilder
                    long timestamp_data = buffer.getLong(0);    //8 bytes of timestamp
                    int x = buffer.getInt(8);                   //4 bytes of x
                    int y = buffer.getInt(12);                  //4 bytes of y
                    int z = buffer.getInt(16);                  //4 bytes of z
                    resultBuilder.append(String.valueOf(timestamp_data) + " " + String.valueOf(x) + " " + String.valueOf(y) + " " + String.valueOf(z) + "\n");
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);

                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    // Display all data received
                    textView.setText("Session finished!\n" + "Results were: \n\n" + resultBuilder.toString());
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
    }

}
