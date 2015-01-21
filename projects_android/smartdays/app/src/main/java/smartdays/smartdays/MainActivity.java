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
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
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
        SharedPreferences preferences = getSharedPreferences("smartdays", 0);
        textViewCurrentActivity.setText(preferences.getString("currentActivity", "No activity"));

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
        activities.add("Cook");
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
                    case Constants.ACTIVITY_LABEL_COMMAND:
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
        dropboxAccessToken = preferences.getString("dropboxToken", "");
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

    @Override
    protected void onResume() {
        super.onResume();

        if (dropboxAuthenticating) {
            if (mDBApi.getSession().authenticationSuccessful()) {
                try {
                    // Required to complete auth, sets the access token on the session
                    mDBApi.getSession().finishAuthentication();
                    dropboxAccessToken = mDBApi.getSession().getOAuth2AccessToken();
                    SharedPreferences settings = getSharedPreferences("smartdays", 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("dropboxToken", dropboxAccessToken);
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

    @Override
    protected void onDestroy() {
        SharedPreferences settings = getSharedPreferences("smartdays", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("currentActivity", String.valueOf(textViewCurrentActivity.getText()));
        editor.commit();

        super.onDestroy();
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
            textViewCurrentActivity.setText(activity);
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
            ((Button)findViewById(R.id.buttonFiles)).setText("Uploading files");
            ((Button)findViewById(R.id.buttonStart)).setEnabled(false);
        }

        private void uploadFile(File file) throws IOException {
            String encodedFilename = URLEncoder.encode(file.getName());
            Request request = new Request.Builder()
                .url("http://10.192.54.154:5000/upload?filename=" + encodedFilename)
                .post(RequestBody.create(MEDIA_TYPE_BINARY, file))
                .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            else {
                file.delete();
            }

            System.out.println(response.body().string());

        }

        @Override
        protected Integer doInBackground(File... files) {
            int counter = 0;
            ProgressBar progressFiles = (ProgressBar) findViewById(R.id.progressBarFiles);
            progressFiles.setMax(100);
            progressFiles.setProgress(0);

            for (int i=0; i < files.length; i++) {
                Log.d(Constants.TAG, files[i].getName());
                try {
                    uploadFile(files[i]);
                } catch (IOException e) {
                    Log.e(Constants.TAG, "Error uploading file " + files[i].getAbsolutePath(), e);
                    e.printStackTrace();
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
            super.onPostExecute(result);
            ((Button)findViewById(R.id.buttonFiles)).setEnabled(true);
            ((Button)findViewById(R.id.buttonFiles)).setText("Upload files");
            ((Button)findViewById(R.id.buttonStart)).setEnabled(true);

        }
    }

}
