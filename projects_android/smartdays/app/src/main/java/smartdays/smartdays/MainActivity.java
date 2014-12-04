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


public class MainActivity extends Activity {

    private TextView textView;
    private Intent intent;

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
                startService(intent);
                textView.setText("Waiting for logging data... Service started...");
                PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(intent);
                textView.setText("Waiting for logging data... Service stopped...");
                PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);
            }
        });

        PebbleKit.registerReceivedDataHandler(this, new PebbleKit.PebbleDataReceiver(Constants.WATCHAPP_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                Log.d("SmartDAYS", "Received value=" + data.getInteger(Constants.START_STOP_KEY));
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);

                if (data.getInteger(Constants.START_STOP_KEY) == Constants.START_MESSAGE) {
                    if (!LoggingService.isRunning()) {
                        startService(intent);
                        textView.setText("Waiting for logging data... Service started...");
                    }
                }
                if (data.getInteger(Constants.START_STOP_KEY) == Constants.STOP_MESSAGE) {
                    if (LoggingService.isRunning()) {
                        stopService(intent);
                    }
                }
            }
        });
    }

}
