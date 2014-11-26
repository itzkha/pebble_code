package smartdays.testlogging_0;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
//import android.content.Intent;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;


import java.util.UUID;


public class MainActivity extends Activity {

    // Configuration
    private static final UUID WATCHAPP_UUID = UUID.fromString("11524b3f-a8f8-4cba-82a4-4c50a1dfae5d");
    private static final int DATA_LOG_TAG_COMPASS = 52;

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

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, int data) {
                // Check this is the compass headings log
                textView.setText(tag.toString());
                if(tag.intValue() == DATA_LOG_TAG_COMPASS) {
                    // Get the compass value and append to result StringBuilder
                    resultBuilder.append("Heading: " + data + " degrees");
                    resultBuilder.append("\n");
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);

                if(tag.intValue() == DATA_LOG_TAG_COMPASS) {
                    // Display all compass headings received
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
