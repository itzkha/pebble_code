package smartdays.smartdays;

// Works with smartdays on the Pebble
// Saves the buffer to a file using a service

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
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
    private MainActivity instance;
    public static Handler serviceMessagesHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        textView = (TextView) findViewById(R.id.textView);
        intent = new Intent(this, LoggingService.class);

        textView.setText("");

        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);

        if (LoggingService.isRunning()) {
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
        } else {
            buttonStart.setEnabled(true);
            buttonStop.setEnabled(false);
        }

        Log.d("SmartDAYS", "Ready...");

        buttonStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!LoggingService.isRunning()) {
                    if (PebbleKit.isWatchConnected(instance)) {
                        //-------------------------------------------------------------------------- Try to start the application on the Pebble
                        PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);

                        //-------------------------------------------------------------------------- Try to start the service
                        startService(intent);
                        textView.setText("Service started...");
                        buttonStart.setEnabled(false);
                        buttonStop.setEnabled(true);
                    } else {
                        textView.setText("Watch is not connected.\nConnect to a Pebble first");
                    }
                }
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (LoggingService.isRunning()) {
                    //------------------------------------------------------------------------------ Try to stop background worker on the Pebble
                    PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);

                    PebbleDictionary data = new PebbleDictionary();
                    data.addUint8(Constants.COMMAND_KEY, (byte) Constants.STOP_COMMAND);
                    PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), Constants.WATCHAPP_UUID, data, Constants.STOP_COMMAND);
                }
            }
        });

        serviceMessagesHandler = new Handler() {
		    @Override
		    public void handleMessage(Message msg) {
       			Log.d("SmartDAYS", String.format("Handler.handleMessage(): msg=%s", msg));

                if (msg.what == Constants.SERVICE_STOPPED) {
                    textView.setText("Service stopped...");
                    buttonStart.setEnabled(true);
                    buttonStop.setEnabled(false);
                }
    			super.handleMessage(msg);
    		}
    	};
    }

}
