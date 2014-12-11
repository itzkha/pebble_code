package smartdays.smartdays;

// Works with smartdays on the Pebble
// Saves the buffer to a file using a service

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.TimeZone;


public class MainActivity extends Activity {

    private TextView textView;
    private Intent intent;
    private PebbleKit.PebbleDataReceiver pebbleReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView)findViewById(R.id.textView);
        intent = new Intent(this, LoggingService.class);

        textView.setText("Waiting for logging data...");

        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);

        Log.d("SmartDAYS", "Ready...");

        buttonStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //startService(intent);
                //textView.setText("Service started...");
                PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(intent);
                textView.setText("Service stopped...");
                PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);
            }
        });


        pebbleReceiver = new PebbleKit.PebbleDataReceiver(Constants.WATCHAPP_UUID) {
            private long ackTime = 0;
            private long deltaCom = 0;
            TimeZone tz = TimeZone.getDefault();
            long offsetFromUTC = tz.getOffset(System.currentTimeMillis());

            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {

                switch (data.getInteger(Constants.COMMAND_KEY).intValue()) {
                    case Constants.START_COMMAND:
                        if (!LoggingService.isRunning()) {
                            startService(intent);
                            textView.setText("Service started...");
                            ackTime = System.currentTimeMillis();
                        }
                        break;
                    case Constants.STOP_COMMAND:
                        if (LoggingService.isRunning()) {
                            stopService(intent);
                            textView.setText("Service stopped...");
                        }
                        break;
                    case Constants.TIMESTAMP_COMMAND:
                        deltaCom = (System.currentTimeMillis() - ackTime) / 2;
                        ackTime = System.currentTimeMillis();

                        offsetFromUTC = ( offsetFromUTC + ((ByteBuffer.wrap(data.getBytes(Constants.TIMESTAMP_KEY)).order(ByteOrder.LITTLE_ENDIAN).getLong() + deltaCom) - ackTime) ) / 2;
                        Log.d("SmartDAYS", "deltaCom=" + String.valueOf(deltaCom) + " new offset=" + String.valueOf(offsetFromUTC));
                        LoggingService.getInstance().setOffset(offsetFromUTC);

                        break;
                }
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
            }
        };
        PebbleKit.registerReceivedDataHandler(this, pebbleReceiver);

    }
/*
    @Override
    public void onStop() {
        unregisterReceiver(pebbleReceiver);
        super.onStop();
    }
*/
}
