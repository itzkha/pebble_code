package smartdays.testlogging_5;

// Works with test_accel_4 on the Pebble
// Saves the buffer to a file "testCapture" using a service

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity {

    private TextView textView;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView)findViewById(R.id.textView);
//        intent = new Intent(this, PebbleLoggingIntentService.class);
        intent = new Intent(this, PebbleLoggingService.class);

//        if  (PebbleLoggingService.isRunning()) {
//            textView.setText("Waiting for logging data... Service started...");
//        }
//        else {
            textView.setText("Waiting for logging data...");
//        }

        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);

        buttonStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startService(intent);
                textView.setText("Waiting for logging data... Service started...");
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(intent);
                textView.setText("Waiting for logging data... Service stopped...");
            }
        });
    }


}
