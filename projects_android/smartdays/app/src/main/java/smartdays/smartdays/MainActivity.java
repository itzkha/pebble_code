package smartdays.smartdays;

// Works with smartdays on the Pebble
// Saves the buffer to a file using a service

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;

import ch.heig_vd.dailyactivities.DailyActivities;
import ch.heig_vd.dailyactivities.model.ActivityBlock;
import ch.heig_vd.dailyactivities.model.Task;
import ch.heig_vd.dailyactivities.model.Timeline;


public class MainActivity extends Activity implements  CurrentActivityDialog.NoticeDialogListener {

    private Intent intent;
    private static MainActivity instance;
    public static Handler serviceMessagesHandler = null;
    private int menuItemInitIndex = 0;
    private ArrayList<Task> activities;
    private CurrentActivityDialog currentActivityDialog;

    private String[] currentNames = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //show error dialog if GoolglePlayServices not available
        if (!isGooglePlayServicesAvailable()) {
            finish();
        }

        setContentView(R.layout.activity_main);
        instance = this;

        intent = new Intent(this, LoggingService.class);

        SharedPreferences preferences = getSharedPreferences("smartdays", 0);

        //------------------------------------------------------------------------------------------
        currentNames = new String[6];
        currentNames[0] = preferences.getString("activityFileName", "");
        currentNames[1] = preferences.getString("moodFileName", "");
        currentNames[2] = preferences.getString("pebbleAccelFileName", "");
        currentNames[3] = preferences.getString("phoneAccelFileName", "");
        currentNames[4] = preferences.getString("phoneAccelSyncedFileName", "");
        currentNames[5] = preferences.getString("locationFileName", "");
        Log.d(Constants.TAG, "File names:" + Arrays.toString(currentNames));

        //------------------------------------------------------------------------------------------
        final TextView textViewMessage = (TextView) findViewById(R.id.textViewMessage);
        textViewMessage.setText("");

        //------------------------------------------------------------------------------------------
        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);
        final Button buttonFiles = (Button) findViewById(R.id.buttonFiles);
        final Button buttonDailyView = (Button) findViewById(R.id.buttonDaySummary);

        if (LoggingService.isRunning()) {
            textViewMessage.setText("Logging started...");
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
        }
        else {
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
                    }
                    else {
                        textViewMessage.setText("Watch is not connected.\nConnect to a Pebble first");
                    }
                }
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (LoggingService.isRunning()) {
                    if (PebbleKit.isWatchConnected(instance)) {
                        //-------------------------------------------------------------------------- Try to stop background worker on the Pebble
                        PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);

                        PebbleDictionary data = new PebbleDictionary();
                        data.addUint8(Constants.COMMAND_KEY, (byte) Constants.STOP_COMMAND);
                        PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), Constants.WATCHAPP_UUID, data, Constants.STOP_COMMAND);
                    }
                    else {
                        textViewMessage.setText("Watch is not connected.\nConnect to a Pebble first.\nOr long press for emergency stop.");
                    }
                }
            }
        });
        buttonStop.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                askService(Constants.STOP_COMMAND, "");
                return true;
            }
        });

        showFilesControl();
        buttonFiles.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                uploadFiles();
            }
        });

        //------------------------------------------------------------------------------------------
        activities = Timeline.getInstance().getAvailableActivities();

        currentActivityDialog = new CurrentActivityDialog();
        currentActivityDialog.setActivities(activities);
        final Button buttonCurrentActivity = (Button) findViewById(R.id.buttonChangeCurrentActivity);
        buttonCurrentActivity.setText(computeCurrentActivity());
        //buttonCurrentActivity.setText(preferences.getString("currentActivity", "No activity"));
        buttonCurrentActivity.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (LoggingService.isRunning()) {
                    currentActivityDialog.show(getFragmentManager(), "Current activity");
                }
                else {
                    Toast.makeText(instance, R.string.start_service_first, Toast.LENGTH_SHORT).show();
                }
            }
        });

        //------------------------------------------------------------------------------------------

        buttonDailyView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (LoggingService.isRunning()) {
                    Log.d(Constants.TAG, "Launching viewer...");
                    Intent intent = new Intent(getBaseContext(), DailyActivities.class);
                    startActivity(intent);
                }
                else {
                    Toast.makeText(instance, R.string.start_service_first, Toast.LENGTH_SHORT).show();
                }
            }
        });

        //------------------------------------------------------------------------------------------
        serviceMessagesHandler = new Handler() {
		    @Override
		    public void handleMessage(Message msg) {
       			Log.d(Constants.TAG, String.format("Activity received message: msg=%s", msg));

                switch (msg.what) {
                    case Constants.SERVICE_STOPPED:
                        textViewMessage.setText("Logging stopped...");
                        buttonStart.setEnabled(true);
                        buttonStop.setEnabled(false);
                        showFilesControl();
                        uploadFiles();
                        break;
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        if (menuItemInitIndex < activities.size()) {
                            askService(Constants.SYNC_MENU_ITEM_COMMAND, activities.get(menuItemInitIndex).getName());
                            Log.d(Constants.TAG, "Asking to send: " + activities.get(menuItemInitIndex));
                            menuItemInitIndex++;
                        }
                        else {
                            menuItemInitIndex = 0;
                        }
                        break;
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        buttonCurrentActivity.setText(msg.obj.toString());
                        break;
                    case Constants.NEW_FILES_COMMAND:
                        SharedPreferences preferences = getSharedPreferences("smartdays", 0);
                        currentNames[0] = preferences.getString("activityFileName", "");
                        currentNames[1] = preferences.getString("moodFileName", "");
                        currentNames[2] = preferences.getString("pebbleAccelFileName", "");
                        currentNames[3] = preferences.getString("phoneAccelFileName", "");
                        currentNames[4] = preferences.getString("phoneAccelSyncedFileName", "");
                        currentNames[5] = preferences.getString("locationFileName", "");
                        Log.d(Constants.TAG, "File names:" + Arrays.toString(currentNames));
                        break;
                }

    			super.handleMessage(msg);
    		}
    	};

        //------------------------------------------------------------------------------------------
        Log.d(Constants.TAG, "Ready...");
    }

    @Override
    protected void onResume() {
        ((Button) findViewById(R.id.buttonChangeCurrentActivity)).setText(computeCurrentActivity());

        super.onResume();
    }

    @Override
    protected void onRestart() {
        ((Button) findViewById(R.id.buttonChangeCurrentActivity)).setText(computeCurrentActivity());

        super.onRestart();
    }

    private boolean isGooglePlayServicesAvailable() {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == status) {
            return true;
        } else {
            GooglePlayServicesUtil.getErrorDialog(status, this, 0).show();
            return false;
        }
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
        if (askService(Constants.ACTIVITY_LABEL_COMMAND, activity)) {
            ((Button)findViewById(R.id.buttonChangeCurrentActivity)).setText(activity);
        }
    }

    private void uploadFiles() {
        Log.d(Constants.TAG, "Uploading...");

        File appDir = new File(Environment.getExternalStorageDirectory() + "/smartdays");
        File files[] = appDir.listFiles();

        new HTTPUploader().execute(files);
    }

    private class HTTPUploader extends AsyncTask<File, Integer, Integer> {
        private final OkHttpClient client = new OkHttpClient();
        public final MediaType MEDIA_TYPE_BINARY = MediaType.parse("application/octet-stream");

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ((Button)findViewById(R.id.buttonFiles)).setEnabled(false);
            ((TextView)findViewById(R.id.textViewFiles)).setText("Uploading");
        }

        private void uploadFile(String folder, File file) throws IOException {
            String encodedFilename = URLEncoder.encode(folder + "/" + file.getName());
            Request request = new Request.Builder()
                .url("http://" + Constants.IP_SERVER +"/upload?filename=" + encodedFilename)
                .post(RequestBody.create(MEDIA_TYPE_BINARY, file))
                .build();

            try {
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                System.out.println(response.body().string());
            }
            catch (ConnectException ce) {
                throw new IOException("Impossible to connect to server");
            }

        }

        @Override
        protected Integer doInBackground(File... files) {
            int counter = 0;
            int i, j;
            boolean skip;

            String folder = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            ProgressBar progressFiles = (ProgressBar) findViewById(R.id.progressBarFiles);
            progressFiles.setMax(files.length);
            progressFiles.setProgress(0);

            for (i = 0; i < files.length; i++) {
                Log.d(Constants.TAG, files[i].getName());
                skip = false;
                for (j = 0; j < 4; j++) {
                    skip |= (files[i].getName().compareTo(currentNames[j]) == 0);
                }
                if (skip) {
                    Log.d(Constants.TAG, "Skipping: " + files[i].getName());
                }
                else {
                    try {
                        uploadFile(folder, files[i]);
                        counter++;
                        files[i].delete();
                    }
                    catch (IOException ioe) {
                        Log.e(Constants.TAG, "Error uploading file " + files[i].getAbsolutePath(), ioe);
                        ioe.printStackTrace();
                    }
                }
                progressFiles.setProgress(i + 1);
            }
            return counter;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            ((TextView)findViewById(R.id.textViewFiles)).setText(String.valueOf(result) + " files uploaded");
            ((Button) findViewById(R.id.buttonFiles)).setEnabled(uploadableFiles());
        }
    }

    private boolean uploadableFiles() {
        File appDir = new File(Environment.getExternalStorageDirectory() + "/smartdays");
        if (appDir.exists()) {
            File files[] = appDir.listFiles();

            boolean uploadable = false;
            boolean temp;

            for (File file : files) {
                temp = true;
                for (String currentFile : currentNames) {
                    temp &= !(file.getName().compareTo(currentFile) == 0);
                }
                uploadable |= temp;
            }
            return uploadable;
        }
        return false;
    }

    private void showFilesControl() {
        if (uploadableFiles()) {
            ((Button)findViewById(R.id.buttonFiles)).setVisibility(View.VISIBLE);
            ((ProgressBar) findViewById(R.id.progressBarFiles)).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.textViewFiles)).setVisibility(View.VISIBLE);
            ((Button)findViewById(R.id.buttonFiles)).setEnabled(true);
        }
        else {
            ((Button)findViewById(R.id.buttonFiles)).setVisibility(View.INVISIBLE);
            ((ProgressBar) findViewById(R.id.progressBarFiles)).setVisibility(View.INVISIBLE);
            ((TextView) findViewById(R.id.textViewFiles)).setVisibility(View.INVISIBLE);
            ((Button)findViewById(R.id.buttonFiles)).setEnabled(false);
        }
    }

    private String computeCurrentActivity() {
        long currentTime = System.currentTimeMillis();

        for (ActivityBlock block : Timeline.getInstance().getActivities()) {
            if ( (currentTime >= block.getBegin().getTime()) && (currentTime <= block.getEnd().getTime()) ) {
                return block.getTask().getName();
            }
        }
        return Task.getDefaultTask().getName();
    }
}
