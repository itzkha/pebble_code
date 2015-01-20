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
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;


public class MainActivity extends Activity implements  CurrentActivityDialog.NoticeDialogListener {

    private Intent intent;
    private static MainActivity instance;
    public static Handler serviceMessagesHandler = null;
    private int menuItemInitIndex = 0;
    private ArrayList<String> activities;
    private CurrentActivityDialog currentActivityDialog;
    private TextView textViewCurrentActivity;

    private DropboxAPI<AndroidAuthSession> mDBApi;
    private String dropboxAccessToken;
    private boolean dropboxAuthenticating = false;

    public static MainActivity getInstance() {
        return instance;
    }
    
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

        //------------------------------------------------------------------------------------------
        final TextView textViewMessage = (TextView) findViewById(R.id.textViewMessage);
        textViewMessage.setText("");

        textViewCurrentActivity = (TextView) findViewById(R.id.textViewCurrentActivity);
        textViewCurrentActivity.setText("No activity");

        //------------------------------------------------------------------------------------------
        final Button buttonStart = (Button) findViewById(R.id.buttonStart);
        final Button buttonStop = (Button) findViewById(R.id.buttonStop);
        final Button buttonFiles = (Button) findViewById(R.id.buttonFiles);

        if (LoggingService.isRunning()) {
            textViewMessage.setText("Logging started...");
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
            buttonFiles.setEnabled(false);
        }
        else {
            textViewMessage.setText("Logging stopped...");
            buttonStart.setEnabled(true);
            buttonStop.setEnabled(false);
            buttonFiles.setEnabled(true);
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
                        buttonFiles.setEnabled(false);
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

        buttonFiles.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                uploadFiles();
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
       			Log.d(Constants.TAG, String.format("Activity received message: msg=%s", msg));

                switch (msg.what) {
                    case Constants.SERVICE_STOPPED:
                        textViewMessage.setText("Logging stopped...");
                        buttonStart.setEnabled(true);
                        buttonStop.setEnabled(false);
                        buttonFiles.setEnabled(true);
                        uploadFiles();
                        break;
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        if (menuItemInitIndex < activities.size()) {
                            askService(Constants.SYNC_MENU_ITEM_COMMAND, activities.get(menuItemInitIndex));
                            Log.d(Constants.TAG, "Asking to send: " + activities.get(menuItemInitIndex));
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
        AppKeyPair appKeys = new AppKeyPair(Constants.DROPBOX_APP_KEY, Constants.DROPBOX_APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeys, Session.AccessType.APP_FOLDER);
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        SharedPreferences settings = getSharedPreferences("dropbox", 0);
        dropboxAccessToken = settings.getString("token", "");
        if (dropboxAccessToken.length() == 0) {
            dropboxAuthenticating = true;
            mDBApi.getSession().startOAuth2Authentication(this);
        }
        else {
            mDBApi.getSession().setOAuth2AccessToken(dropboxAccessToken);
        }
        Log.d(Constants.TAG, "Token: " + dropboxAccessToken);

        //------------------------------------------------------------------------------------------
        Log.d(Constants.TAG, "Ready...");
    }

    protected void onResume() {
        super.onResume();

        if (dropboxAuthenticating) {
            if (mDBApi.getSession().authenticationSuccessful()) {
                try {
                    // Required to complete auth, sets the access token on the session
                    mDBApi.getSession().finishAuthentication();
                    dropboxAccessToken = mDBApi.getSession().getOAuth2AccessToken();
                    SharedPreferences settings = getSharedPreferences("dropbox", 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("token", dropboxAccessToken);
                    editor.commit();
                } catch (IllegalStateException e) {
                    Log.d(Constants.TAG, "Error authenticating", e);
                }
            }
            else {
                dropboxAccessToken = "";
                Log.d(Constants.TAG, "Authentication failed");
            }
            dropboxAuthenticating = false;
            Log.d(Constants.TAG, "Token: " + dropboxAccessToken);
        }
    }

    protected void onDestroy() {

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
        if (askService(Constants.LABEL_COMMAND, activity)) {
            textViewCurrentActivity.setText(activity);
        }

    }

    private void uploadFiles() {
        Log.d(Constants.TAG, "Uploading...");
        ((TextView)findViewById(R.id.textViewMessage)).setText("Uploading files...");
        ((Button)findViewById(R.id.buttonFiles)).setEnabled(false);

        File appDir = new File(Environment.getExternalStorageDirectory() + "/smartdays");
        File files[] = appDir.listFiles();

        ((TextView)findViewById(R.id.textViewMessage)).setText("Uploading files...");
        new DropboxUploader().execute(files);
    }

    private class DropboxUploader extends AsyncTask<File, Integer, Integer> {
        @Override
        protected Integer doInBackground(File... files) {
            int counter = 0;
            ProgressBar progressFiles = (ProgressBar) findViewById(R.id.progressBarFiles);
            progressFiles.setMax(100);
            progressFiles.setProgress(0);
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            for (int i=0; i < files.length; i++) {
                Log.d(Constants.TAG, files[i].getName());
                try {
                    DropboxAPI.Entry inDB = mDBApi.metadata("/" + deviceId + "/" + files[i].getName(), 1, null, false, null);
                    Log.d(Constants.TAG, "The file already exists");
                    if ( (((CheckBox)findViewById(R.id.checkBoxDeleteFiles)).isChecked()) && (inDB.bytes == files[i].length())) {
                        Log.d(Constants.TAG, "The files have the same length... deleting");
                        files[i].delete();
                    }
                }
                catch (DropboxUnlinkedException e) {
                    Log.d(Constants.TAG, "Error. DropBox unlinked");
                }
                catch (DropboxException de1) {
                    Log.d(Constants.TAG, "The file does not exist");
                    // Upload it
                    try {
                        FileInputStream inputStream = new FileInputStream(files[i]);
                        DropboxAPI.Entry response = mDBApi.putFile("/" + deviceId + "/" + files[i].getName(), inputStream, files[i].length(), null, null);
                        //DropboxAPI.Entry response = mDBApi.putFileOverwrite(files[i].getName(), inputStream, files[i].length(), null);
                    }
                    catch (FileNotFoundException fnfe) {
                        Log.d(Constants.TAG, "Error. file: " + files[i].getName() + " not found.");
                    }
                    catch (DropboxUnlinkedException e) {
                        Log.d(Constants.TAG, "Error. DropBox unlinked");
                    }
                    catch (DropboxException de2) {
                        Log.d(Constants.TAG, "Error. DropBox exception");
                    }
                }
                progressFiles.setProgress((int) (100 * (i+1) / (float)files.length));
            }
            return counter;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onPostExecute(Integer result) {
            ((TextView)findViewById(R.id.textViewMessage)).setText("Logging stopped...");
            ((Button)findViewById(R.id.buttonFiles)).setEnabled(true);

        }
    }

}
