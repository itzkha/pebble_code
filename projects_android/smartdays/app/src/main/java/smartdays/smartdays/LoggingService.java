package smartdays.smartdays;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import ch.heig_vd.dailyactivities.model.ActivityBlock;
import ch.heig_vd.dailyactivities.model.Task;
import ch.heig_vd.dailyactivities.model.Timeline;

/**
 * Created by hector on 01/12/14.
 */
public class LoggingService extends Service {

    private static LoggingService instance = null;
    public static Handler serviceMessagesHandler = null;
    private SharedPreferences settings;

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PhoneSensorEventListener phoneSensorEventListener;
    private FusedLocationService fusedLocationService;

    private BufferedOutputStream bufferOutPebble = null;
    private BufferedOutputStream bufferOutPhoneSynced = null;
    private BufferedOutputStream bufferOutPhone = null;
    private BufferedOutputStream bufferOutLocation = null;
    private BufferedOutputStream bufferOutLabelActivity = null;
    private BufferedOutputStream bufferOutLabelMood = null;
    private PhoneDataBuffer phoneDataBuffer = null;
    private Timeline activityTimeline = null;

    private SmartDaysPebbleDataLogReceiver dataloggingReceiver;
    private PebbleKit.PebbleDataReceiver pebbleAppMessageDataReceiver;
    private PebbleKit.PebbleAckReceiver pebbleAppMessageAckReceiver;
    private PebbleKit.PebbleNackReceiver pebbleAppMessageNackReceiver;
    private PowerManager.WakeLock wakeLock;
    private Handler synchronizationLabellingHandler;
    private Runnable runnableSynchronizationLabelling;
    private Handler locationHandler;
    private Runnable runnableLocation;


    private long lastPhoneTimestamp = 0;
    private static boolean running = false;
    private int startFailCounter = 0;
    private int stopFailCounter = 0;
    private int timestampFailCounter = 0;
    private int activityLabelFailCounter = 0;
    private int moodLabelFailCounter = 0;
    private int timestampCounter = 0;
    private int activityLabelCounter = 0;
    private int moodLabelCounter = 0;

    private Random random;
    private File appDir;
    private Timer timerStop;
    private Timer timerStart;


    public static boolean isRunning() {
        return running;
    }

    public static LoggingService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        Log.d(Constants.TAG, "Creating..." + this);
        if (instance == null) {
            instance = this;
        }
        settings = getSharedPreferences("smartdays", 0);
        random = new Random();
        lastPhoneTimestamp = System.currentTimeMillis();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.TAG);

        //------------------------------------------------------------------------------------------
        TimerTask timerTaskStop = new TimerTask() {
            public void run() {
                Log.d(Constants.TAG, "Stopping automatically");
                stopAlarms();
                stopLoggers();
                writeActivitiesToFile();
                closeFiles();
            }
        };
        Calendar calendarToday = Calendar.getInstance();
        GregorianCalendar gcToday = new GregorianCalendar(calendarToday.get(Calendar.YEAR), calendarToday.get(Calendar.MONTH), calendarToday.get(Calendar.DATE), 23, 59, 59);
        timerStop = new Timer();
        timerStop.schedule(timerTaskStop, gcToday.getTime(), 24 * 60 * 60 * 1000);

        TimerTask timerTaskStart = new TimerTask() {
            public void run() {
                Log.d(Constants.TAG, "Starting automatically");
                openFiles();
                createTimeline();
                startLoggers(false);
                startAlarms();

                logActivity(settings.getString("currentActivity", Task.getDefaultTask().getName()), Task.Social.valueOf(settings.getString("currentAlone", Task.Social.NA.toString())));
            }
        };
        Calendar calendarTomorrow = Calendar.getInstance();
        calendarTomorrow.add(Calendar.DAY_OF_YEAR, 1);
        GregorianCalendar gcTomorrow = new GregorianCalendar(calendarTomorrow.get(Calendar.YEAR), calendarTomorrow.get(Calendar.MONTH), calendarTomorrow.get(Calendar.DATE), 00, 00, 01);
        timerStart = new Timer();
        timerStart.schedule(timerTaskStart, gcTomorrow.getTime(), 24 * 60 * 60 * 1000);

        //------------------------------------------------------------------------------------------
        fusedLocationService = new FusedLocationService(getApplicationContext());

        //------------------------------------------------------------------------------------------
        //phoneDataBuffer = new PhoneDataBuffer(Constants.BUFFER_SIZE);
        phoneDataBuffer = null;

        //------------------------------------------------------------------------------------------
        createTimeline();

        //------------------------------------------------------------------------------------------
        openFiles();

        //------------------------------------------------------------------------------------------
        pebbleAppMessageDataReceiver = new PebbleKit.PebbleDataReceiver(Constants.WATCHAPP_UUID) {
            long currentTime;
            long timestampPebble;
            TimeZone tz = TimeZone.getDefault();
            long offsetFromUTC = tz.getOffset(System.currentTimeMillis());

            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                int command = data.getInteger(Constants.COMMAND_KEY).intValue();
                Log.d(Constants.TAG, "Receiving command: " + String.valueOf(command));
                String label;
                String social;
                switch (command) {
                    case Constants.TIMESTAMP_COMMAND:
                        Log.d(Constants.TAG, "TIMESTAMP received");
                        //DeltaT = ((Tpe1 + Tpe2) - (Tph1 + Tph2)) / 2      We use Tpe1 = Tpe2
                        currentTime = System.currentTimeMillis();
                        timestampPebble = ByteBuffer.wrap(data.getBytes(Constants.TIMESTAMP_KEY)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                        offsetFromUTC = timestampPebble - ((currentTime + lastPhoneTimestamp) / 2);
                        //Log.d(Constants.TAG, "timestampPEBBLE=" + String.valueOf(timestampPebble) + " currentTime=" + String.valueOf(currentTime) + " lastTime=" + String.valueOf(lastPhoneTimestamp) + " new offset=" + String.valueOf(offsetFromUTC));
                        Log.d(Constants.TAG, "new offset=" + String.valueOf(offsetFromUTC));
                        dataloggingReceiver.setOffset(offsetFromUTC);
                        Log.d(Constants.TAG, "counter: " + String.valueOf(timestampCounter));
                        break;
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        label = data.getString(Constants.LABEL_KEY);
                        social = data.getString(Constants.SOCIAL_KEY);
                        Log.d(Constants.TAG, "Received activity label: " + label + " " + social);
                        logActivity(label, Task.Social.valueOf(social));
                        askActivity(label);
                        break;
                    case Constants.MOOD_LABEL_COMMAND:
                        label = data.getString(Constants.LABEL_KEY);
                        Log.d(Constants.TAG, "Received mood label: " + label);
                        logMood(label);
                        break;
                }
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
            }
        };
        PebbleKit.registerReceivedDataHandler(this, pebbleAppMessageDataReceiver);

        pebbleAppMessageAckReceiver = new PebbleKit.PebbleAckReceiver(Constants.WATCHAPP_UUID) {
            @Override
            public void receiveAck(Context context, int transactionId) {
                Log.i(Constants.TAG, "Received ack for transaction " + transactionId);

                switch (transactionId) {
                    case Constants.START_COMMAND:
                        startFailCounter = 0;
                        break;
                    case Constants.STOP_COMMAND:
                        stopFailCounter = 0;
                        stop();
                        break;
                    case Constants.TIMESTAMP_COMMAND:
                        timestampFailCounter = 0;
                        break;
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        activityLabelFailCounter = 0;
                        break;
                    case Constants.MOOD_LABEL_COMMAND:
                        moodLabelFailCounter = 0;
                        break;
                }
            }
        };
        PebbleKit.registerReceivedAckHandler(this, pebbleAppMessageAckReceiver);

        pebbleAppMessageNackReceiver = new PebbleKit.PebbleNackReceiver(Constants.WATCHAPP_UUID) {
            @Override
            public void receiveNack(Context context, int transactionId) {
                Log.i(Constants.TAG, "Received nack for transaction " + transactionId);

                switch (transactionId) {
                    case Constants.START_COMMAND:
                        startFailCounter++;
                        if (startFailCounter < Constants.MAX_FAILS) {
                            sendCommand(Constants.START_COMMAND);
                        }
                        else {
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                            stop();
                        }
                        break;
                    case Constants.STOP_COMMAND:
                        stopFailCounter++;
                        if (stopFailCounter < Constants.MAX_FAILS) {
                            sendCommand(Constants.STOP_COMMAND);
                        }
                        else {
                            // Tell the user we stopped
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                            stop();
                        }
                        break;
                    case Constants.TIMESTAMP_COMMAND:
                        timestampFailCounter++;
                        if (timestampFailCounter < Constants.MAX_FAILS) {
                            sendCommand(Constants.TIMESTAMP_COMMAND);
                        }
                        else {
                            // Tell the user the Pebble is not responding
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        activityLabelFailCounter++;
                        if (activityLabelFailCounter < Constants.MAX_FAILS) {
                            sendCommand(Constants.ACTIVITY_LABEL_COMMAND);
                        }
                        else {
                            // Tell the user the Pebble is not responding
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case Constants.MOOD_LABEL_COMMAND:
                        moodLabelFailCounter++;
                        if (moodLabelFailCounter < Constants.MAX_FAILS) {
                            sendCommand(Constants.MOOD_LABEL_COMMAND);
                        }
                        else {
                            // Tell the user the Pebble is not responding
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        };
        PebbleKit.registerReceivedNackHandler(this, pebbleAppMessageNackReceiver);

        //------------------------------------------------------------------------------------------
        synchronizationLabellingHandler = new Handler();
        locationHandler = new Handler();
        startAlarms();

        //------------------------------------------------------------------------------------------
        serviceMessagesHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.d(Constants.TAG, String.format("Service received message: msg=%s", msg));

                switch (msg.what) {
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        logActivity(msg.obj.toString(), Task.Social.values()[msg.arg1]);
                        break;
                    case Constants.UPDATE_ACTIVITY_FILE:
                        writeActivitiesToFile();
                        break;
                    case Constants.STOP_COMMAND:
                        stop();
                        break;
                }
                super.handleMessage(msg);
            }
        };

        running = false;
    }

    private void createTimeline() {
        activityTimeline = Timeline.getInstance();
        activityTimeline.resetTimeline();
    }

    private void startAlarms() {

        //------------------------------------------------------------------------------------------
        runnableSynchronizationLabelling = new Runnable() {
            @Override
            public void run() {
                // ask for timestamp shift
                PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);
                timestampCounter++;

                if (timestampCounter <= Constants.NUMBER_OF_SYNCS) {
                    sendCommand(Constants.TIMESTAMP_COMMAND);                                       // ask for timestamp
                    synchronizationLabellingHandler.postDelayed(this, Constants.SYNCHRONIZATION_LABELLING_SHORT_PERIOD);
                }
                else {
                    timestampCounter = 0;
                    activityLabelCounter++;
                    moodLabelCounter++;

                    if (activityLabelCounter >= 10) {                                               // 50 minutes
                        if (random.nextFloat() < 0.5) {
                            sendCommand(Constants.ACTIVITY_LABEL_COMMAND);                          // ask for labels (activity)
                        }
                    }
                    else if (moodLabelCounter >= 34) {                                              // 170 minutes
                        if (random.nextFloat() < 0.5) {
                            sendCommand(Constants.MOOD_LABEL_COMMAND);                              // ask for labels (mood)
                        }
                    }

                    synchronizationLabellingHandler.postDelayed(this, Constants.SYNCHRONIZATION_LABELLING_LONG_PERIOD);
                }
            }
        };
        synchronizationLabellingHandler.postDelayed(runnableSynchronizationLabelling, Constants.SYNCHRONIZATION_LABELLING_SHORT_PERIOD);         // Request first timestamp

        //------------------------------------------------------------------------------------------
        runnableLocation = new Runnable() {
            @Override
            public void run() {
                Location location = fusedLocationService.getLocation();
                String line = String.valueOf(System.currentTimeMillis());
                if (location != null) {
                    line = line.concat("," + location.getLatitude()
                            + "," + location.getLongitude()
                            + "," + location.getAltitude()
                            + "," + location.getAccuracy()
                            + "," + location.getProvider()
                            + "\n");
                }
                else {
                    line = line.concat(",NA,NA,NA,NA,NA\n");
                }
                try {
                    bufferOutLocation.write(line.getBytes());
                    bufferOutLocation.flush();
                }
                catch (IOException ioe) {
                    Log.d(Constants.TAG, "Error writing location.");
                }

                locationHandler.postDelayed(this, Constants.LOCATION_PERIOD);
            }
        };
        locationHandler.postDelayed(runnableLocation, Constants.LOCATION_PERIOD);

    }

    private void startLoggers(boolean startPebble) {
            //dataloggingReceiver = new SmartDaysPebbleDataLogReceiver(Constants.WATCHAPP_UUID, bufferOutPebble, bufferOutPhoneSynced, phoneDataBuffer);    //log pebble data + synced phone data
            dataloggingReceiver = new SmartDaysPebbleDataLogReceiver(Constants.WATCHAPP_UUID, bufferOutPebble, null, null);                                 // log pebble data
            //phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer, bufferOutPhone);                                                     // log raw phone data + put data in the buffer for sync
            //phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer, null);                                                               // put phone data in the buffer
            phoneSensorEventListener = new PhoneSensorEventListener(null, bufferOutPhone);                                                                  // log raw phone data (no sync)

            startLoggingPebble(startPebble);
            startLoggingPhone();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        if (!running) {

            // Display a notification about us starting.  We put an icon in the status bar.
            Notification notification = showNotification();
            startForeground(NOTIFICATION, notification);

            startLoggers(true);
            running = true;

        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(Constants.TAG, "Destroying service");
        // Cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();

        running = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean openFiles() {
        try {
            appDir = new File(Environment.getExternalStorageDirectory() + "/smartdays");
            if (!appDir.exists()) {
                appDir.mkdir();
            }

            // Create the files
            String fileName;
            Date date = new Date();
            SharedPreferences.Editor editor = settings.edit();

            String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String dateString = sdf.format(date);

            Log.d(Constants.TAG, "Creating activity file...");
            fileName = "activity_" + deviceId + "_" + dateString + ".csv";
            bufferOutLabelActivity = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutLabelActivity.write(Constants.LABELS_FILE_HEADER.getBytes());
            editor.putString("activityFileName", fileName);

            Log.d(Constants.TAG, "Creating mood file...");
            fileName = "mood_" + deviceId + "_" + dateString + ".csv";
            bufferOutLabelMood = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutLabelMood.write(Constants.LABELS_FILE_HEADER.getBytes());
            editor.putString("moodFileName", fileName);

            Log.d(Constants.TAG, "Creating pebbleAccel file...");
            fileName = "pebbleAccel_" + deviceId + "_" + dateString + ".bin";
            bufferOutPebble = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            editor.putString("pebbleAccelFileName", fileName);

            Log.d(Constants.TAG, "Creating phoneAccel file...");
            fileName = "phoneAccel_" + deviceId + "_" + dateString + ".bin";
            bufferOutPhone = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            editor.putString("phoneAccelFileName", fileName);

            Log.d(Constants.TAG, "Creating phoneAccelSynced file...");
            fileName = "phoneAccelSynced_" + deviceId + "_" + dateString + ".bin";
            //bufferOutPhoneSynced = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutPhoneSynced = null;
            editor.putString("phoneAccelSyncedFileName", fileName);

            Log.d(Constants.TAG, "Creating location file...");
            fileName = "location_" + deviceId + "_" + dateString + ".csv";
            bufferOutLocation = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutLocation.write(Constants.LOCATION_FILE_HEADER.getBytes());
            editor.putString("locationFileName", fileName);

            editor.commit();
            askActivity(Constants.NEW_FILES_COMMAND);

            Log.d(Constants.TAG, "Files created...");
            return true;

        }
        catch (IOException ioe) {
            Log.d(Constants.TAG, "Error creating file...");
            return false;
        }
    }

    private void closeFiles() {
        try {
            bufferOutPebble.close();
            Log.d(Constants.TAG, "bufferOutPebble closed...");
        } catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutPebble...");
        } catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutPebble... null pointer");
        }

        try {
            bufferOutPhone.close();
            Log.d(Constants.TAG, "bufferOutPhone closed...");
        } catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutPhone...");
        } catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutPhone... null pointer");
        }

        try {
            bufferOutPhoneSynced.close();
            Log.d(Constants.TAG, "bufferOutPhoneSynced closed...");
        } catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutPhoneSynced...");
        } catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutPhoneSynced... null pointer");
        }

        try {
            bufferOutLabelActivity.close();
            Log.d(Constants.TAG, "File activity closed...");
        }
        catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutLabelActivity...");
        }
        catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutLabelActivity... null pointer");
        }

        try {
            bufferOutLabelMood.close();
            Log.d(Constants.TAG, "File mood closed...");
        }
        catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutLabelMood...");
        }
        catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutLabelMood... null pointer");
        }

        try {
            bufferOutLocation.close();
            Log.d(Constants.TAG, "File location closed...");
        }
        catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutLocation...");
        }
        catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutLocation... null pointer");
        }

    }

    private void stopLoggers() {
        try {
            unregisterReceiver(dataloggingReceiver);
            Log.d(Constants.TAG, "Unregistering receiver...");
        } catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Ending service... null pointer");
        } catch (IllegalArgumentException iae) {
            Log.d(Constants.TAG, "Unregistering receiver... already unregistered");
        }

        sensorManager.unregisterListener(phoneSensorEventListener);
        wakeLock.release();
    }

    private void stopAlarms() {
        synchronizationLabellingHandler.removeCallbacks(runnableSynchronizationLabelling);
        locationHandler.removeCallbacks(runnableLocation);
    }

    private void freeResources() {
        //------------------------------------------------------------------------------------------ Stop alarms
        stopAlarms();

        //------------------------------------------------------------------------------------------ Stop acquisition receivers
        stopLoggers();

        //------------------------------------------------------------------------------------------ Close files
        closeFiles();

        //------------------------------------------------------------------------------------------ Stop communication with the Pebble
        unregisterReceiver(pebbleAppMessageDataReceiver);
        unregisterReceiver(pebbleAppMessageAckReceiver);
        unregisterReceiver(pebbleAppMessageNackReceiver);

        PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);               // stop the Pebble application

    }

    private void writeActivitiesToFile() {
        String fileName = settings.getString("activityFileName", "");       // save the labels in the file
        try {
            bufferOutLabelActivity = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutLabelActivity.write(Constants.LABELS_FILE_HEADER.getBytes());

            ArrayList<ActivityBlock> activities = Timeline.getInstance().getActivities();
            for (ActivityBlock block : activities) {
                bufferOutLabelActivity.write(block.getTask().getName().getBytes());
                bufferOutLabelActivity.write(",".getBytes());
                bufferOutLabelActivity.write(block.getTask().getAlone().toString().getBytes());
                bufferOutLabelActivity.write(",".getBytes());
                bufferOutLabelActivity.write(String.valueOf(block.getBegin().getTime()).getBytes());
                bufferOutLabelActivity.write("\n".getBytes());
            }
            bufferOutLabelActivity.flush();
            bufferOutLabelActivity.close();
            Timeline.getInstance().setNeedingWrite(false);
        }
        catch (IOException ioe) {
        }
    }

    private void stop() {

        writeActivitiesToFile();

        freeResources();

        SharedPreferences settings = getSharedPreferences("smartdays", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("activityFileName", "");
        editor.putString("moodFileName", "");
        editor.putString("pebbleAccelFileName", "");
        editor.putString("phoneAccelFileName", "");
        editor.putString("phoneAccelSyncedFileName", "");
        editor.putString("locationFileName", "");
        editor.commit();
        askActivity(Constants.NEW_FILES_COMMAND);

        editor.putString("currentActivity", "No activity");
        editor.putString("currentAlone", Task.Social.NA.toString());
        editor.commit();
        askActivity("No activity");

        Timeline.getInstance().resetTimeline();

        fusedLocationService.stop();

        askActivity(Constants.SERVICE_STOPPED);

        // Then stop
        stopSelf();
    }

    /**
     * Show a notification while this service is running.
     */
    private Notification showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification.Builder(this)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();

        // Send the notification.
        notificationManager.notify(NOTIFICATION, notification);

        // Tell the user we started.
        Toast.makeText(this, R.string.local_service_started, Toast.LENGTH_SHORT).show();
        return notification;
    }

    private void startLoggingPebble(boolean startPebble) {
        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);

        if (startPebble) {
            sendCommand(Constants.START_COMMAND);
        }
    }

    private void startLoggingPhone() {
        sensorManager.registerListener(phoneSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        wakeLock.acquire();
    }

    private void sendCommand(int command) {
        Log.d(Constants.TAG, "Sending command: " + String.valueOf(command));
        PebbleDictionary data = new PebbleDictionary();
        data.addUint8(Constants.COMMAND_KEY, (byte) command);
        lastPhoneTimestamp = System.currentTimeMillis();
        PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), Constants.WATCHAPP_UUID, data, command);
    }

    private void askActivity(int command) {
        if(MainActivity.serviceMessagesHandler != null) {
            Message msg = Message.obtain();
            msg.what = command;
            MainActivity.serviceMessagesHandler.sendMessage(msg);
        }
    }

    private void askActivity(String label) {
        if(MainActivity.serviceMessagesHandler != null) {
            Message msg = Message.obtain();
            msg.what = Constants.ACTIVITY_LABEL_COMMAND;
            msg.obj = label;
            MainActivity.serviceMessagesHandler.sendMessage(msg);
        }
    }

    private void logActivity(String label, Task.Social alone) {

        SharedPreferences.Editor editor = settings.edit();
        editor.putString("currentActivity", label);
        editor.putString("currentAlone", alone.toString());
        editor.commit();

        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = null;
        for (ActivityBlock block : activityTimeline.getActivities()) {                  // find the end of this label
            Log.d(Constants.TAG, "Now: " + now.toString() + " block begin: " + block.getBegin().toString() + " block end: " + block.getEnd().toString());
            if ( (now.compareTo(block.getBegin()) >= 0)  && (now.compareTo(block.getEnd()) <= 0) ) {
                end = new Timestamp(block.getEnd().getTime());
                break;
            }
        }

        if (end != null) {
            Task temp = new Task(label);
            temp.setAlone(alone);
            ActivityBlock newBlock = new ActivityBlock(temp, now, end);
            newBlock.setUndefinedEnd(true);
            activityTimeline.addActivity(newBlock);
            activityLabelCounter = 0;
            writeActivitiesToFile();
        }
    }

    private void logMood(String label) {
        long timestamp = System.currentTimeMillis();
        String line = label.concat("," + timestamp);

        try {
            bufferOutLabelMood.write(line.getBytes());
            bufferOutLabelMood.flush();
            moodLabelCounter = 0;
        }
        catch (IOException ioe) {}
    }

}

