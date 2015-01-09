package smartdays.smartdays;

// Works with smartdays on the Pebble
// Saves the buffer to a file using a service

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.ArrayList;


public class MainActivity extends Activity implements  CurrentActivityDialog.NoticeDialogListener {

    private Intent intent;
    private MainActivity instance;
    public static Handler serviceMessagesHandler = null;
    private int menuItemInitIndex = 0;
    private ArrayList<String> activities;
    private CurrentActivityDialog currentActivityDialog;
    private TextView textViewCurrentActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        intent = new Intent(this, LoggingService.class);

        //------------------------------------------------------------------------------------------
        final TextView textViewMessage = (TextView) findViewById(R.id.textViewMessage);
        textViewMessage.setText("");

        textViewCurrentActivity = (TextView) findViewById(R.id.textViewCurrentActivity);
        textViewCurrentActivity.setText("No activity");

        //------------------------------------------------------------------------------------------
        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);

        if (LoggingService.isRunning()) {
            textViewMessage.setText("Logging started...");
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
        } else {
            textViewMessage.setText("Logging stopped...");
            buttonStart.setEnabled(true);
            buttonStop.setEnabled(false);
        }

        buttonStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!LoggingService.isRunning()) {
                    if (PebbleKit.isWatchConnected(instance)) {
                        //-------------------------------------------------------------------------- Try to start the application on the Pebble
                        PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);

                        //-------------------------------------------------------------------------- Try to start the service
                        startService(intent);
                        textViewMessage.setText("Logging started...");
                        buttonStart.setEnabled(false);
                        buttonStop.setEnabled(true);
                    } else {
                        textViewMessage.setText("Watch is not connected.\nConnect to a Pebble first");
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

        //------------------------------------------------------------------------------------------
        activities = new ArrayList<String>(20);
        activities.add("Breakfast");
        activities.add("Coffee");
        activities.add("Dinner");
        activities.add("Lunch");
        activities.add("No activity");
        activities.add("Rest");
        activities.add("Run");
        activities.add("Walk");
        activities.add("Work");

        currentActivityDialog = new CurrentActivityDialog();
        currentActivityDialog.setActivities(activities);
        final Button buttonCurrentActivity = (Button) findViewById(R.id.buttonCurrentActivity);
        buttonCurrentActivity.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                currentActivityDialog.show(getFragmentManager(), "Current activity");
            }
        });

        //------------------------------------------------------------------------------------------
        serviceMessagesHandler = new Handler() {
		    @Override
		    public void handleMessage(Message msg) {
       			Log.d("SmartDAYS", String.format("Activity received message: msg=%s", msg));

                switch (msg.what) {
                    case Constants.SERVICE_STOPPED:
                        textViewMessage.setText("Logging stopped...");
                        buttonStart.setEnabled(true);
                        buttonStop.setEnabled(false);
                        break;
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        if (menuItemInitIndex < activities.size()) {
                            askService(Constants.SYNC_MENU_ITEM_COMMAND, activities.get(menuItemInitIndex));
                            Log.d("SmartDAYS", "Asking to send: " + activities.get(menuItemInitIndex));
                            menuItemInitIndex++;
                        }
                        else {
                            menuItemInitIndex = 0;
                        }
                        break;
                    case Constants.LABEL_COMMAND:
                        textViewCurrentActivity.setText(msg.obj.toString());
                        break;
                }

    			super.handleMessage(msg);
    		}
    	};

        //------------------------------------------------------------------------------------------
        Log.d("SmartDAYS", "Ready...");

    }

    protected void onDestroy() {

    }

    private boolean askService(int command, String text) {
        if(LoggingService.serviceMessagesHandler != null) {
            Message msg = Message.obtain();
            msg.what = command;
            msg.obj = text;
            LoggingService.serviceMessagesHandler.sendMessage(msg);
            return true;
        }
        else {
            Toast.makeText(instance, R.string.start_service_first, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void onDialogPositiveClick(String activity) {
        if (askService(Constants.LABEL_COMMAND, activity)) {
            textViewCurrentActivity.setText(activity);
        }

    }

}
