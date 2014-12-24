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

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.TimeZone;


public class MainActivity extends Activity {

    private Intent intent;
    private MainActivity instance;
    public static Handler serviceMessagesHandler = null;
    private ActionMode mActionMode;
    private int selectedActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        intent = new Intent(this, LoggingService.class);

        //------------------------------------------------------------------------------------------
        final TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText("");

        //------------------------------------------------------------------------------------------
        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);

        if (LoggingService.isRunning()) {
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
        } else {
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

        //------------------------------------------------------------------------------------------
        ArrayList<String> activities = new ArrayList<String>(20);
        final ListView listActivities;
        listActivities = (ListView) findViewById(R.id.listActivities);
        selectedActivity = -1;

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, activities);
        listActivities.setAdapter(adapter);
        listActivities.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedActivity = position;
                String itemValue = (String)listActivities.getItemAtPosition(selectedActivity);
                Log.d("SmartDAYS", "Position:" + selectedActivity + " ListItem:" + itemValue);
            }
        });

        //------------------------------------------------------------------------------------------
        final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
            // Called when the action mode is created; startActionMode() was called
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate a menu resource providing context menu items
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.context_menu, menu);
                mode.setTitle(adapter.getItem(selectedActivity));
                return true;
            }

            // Called each time the action mode is shown. Always called after onCreateActionMode, but
            // may be called multiple times if the mode is invalidated.
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false; // Return false if nothing is done
            }

            // Called when the user selects a contextual menu item
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_delete:
                        Log.d("SmartDAYS", "Delete!!!");
                        adapter.remove(adapter.getItem(selectedActivity));
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            // Called when the user exits the action mode
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }
        };

        //------------------------------------------------------------------------------------------
        listActivities.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
                if (mActionMode != null) {
                    return false;
                }
                selectedActivity = pos;
                mActionMode = instance.startActionMode(mActionModeCallback);
                v.setSelected(true);
                return true;
            }
        });

        //------------------------------------------------------------------------------------------
        final EditText newActivity = (EditText) findViewById(R.id.newActivity);
        Button buttonAddActivity = (Button) findViewById(R.id.buttonAddActivity);
        buttonAddActivity.setOnClickListener(new View.OnClickListener() {
 			@Override
			public void onClick(View arg0) {
                String temp = newActivity.getText().toString().trim();
                Log.d("SmartDAYS", "You entered: " + temp);
                if (temp.length() > 0) {
                    adapter.add(temp);
                    newActivity.getText().clear();
                    adapter.notifyDataSetChanged();
                    listActivities.setSelection(adapter.getCount()-1);
                }
			}
		});

        //------------------------------------------------------------------------------------------
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

        //------------------------------------------------------------------------------------------
        Log.d("SmartDAYS", "Ready...");

    }

}
