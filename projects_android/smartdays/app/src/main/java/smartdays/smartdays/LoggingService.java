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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;

/**
 * Created by hector on 01/12/14.
 */
public class LoggingService extends Service {

    private static LoggingService instance = null;
    public static Handler serviceMessagesHandler = null;

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PhoneSensorEventListener phoneSensorEventListener;
    private FusedLocationService fusedLocationService;

    private BufferedOutputStream bufferOutPebble = null;
    private BufferedOutputStream bufferOutPhoneSynced = null;
    //private BufferedOutputStream bufferOutPhone = null;
    //private BufferedOutputStream bufferOutSync = null;
    private BufferedOutputStream bufferOutLabelActivity = null;
    private BufferedOutputStream bufferOutLabelMood = null;
    private PhoneDataBuffer phoneDataBuffer;

    private SmartDaysPebbleDataLogReceiver dataloggingReceiver;
    private PebbleKit.PebbleDataReceiver pebbleAppMessageDataReceiver;
    private PebbleKit.PebbleAckReceiver pebbleAppMessageAckReceiver;
    private PebbleKit.PebbleNackReceiver pebbleAppMessageNackReceiver;
    private PowerManager.WakeLock wakeLock;
    private Handler synchronizationLabellingHandler;
    private Runnable runnableSynchronizationLabelling;


    private long lastPhoneTimestamp = 0;
    private static boolean running = false;
    private int startFailCounter = 0;
    private int stopFailCounter = 0;
    private int timestampFailCounter = 0;
    private int timestampCounter = 0;
    private int syncMenuFailCounter = 0;
    private int activityLabelFailCounter = 0;
    private int moodLabelFailCounter = 0;

    private Random random;


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
        random = new Random();
        lastPhoneTimestamp = System.currentTimeMillis();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.TAG);

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
                        //if (bufferOutSync != null) {
                        //    try {
                        //        bufferOutSync.write(ByteBuffer.allocate(8).putLong(offsetFromUTC).array());
                        //    }
                        //    catch (IOException ioe) {
                        //    }
                        //}
                        timestampCounter++;
                        Log.d(Constants.TAG, "counter: " + String.valueOf(timestampCounter));
                        break;
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        Log.d(Constants.TAG, "Receiving request for label items");
                        askActivity(Constants.SYNC_MENU_ITEM_COMMAND);
                        break;
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        label = data.getString(Constants.LABEL_KEY);
                        Log.d(Constants.TAG, "Received activity label: " + label);
                        logLabel(label, command);
                        askActivity(label);
                        break;
                    case Constants.MOOD_LABEL_COMMAND:
                        label = data.getString(Constants.LABEL_KEY);
                        Log.d(Constants.TAG, "Received mood label: " + label);
                        logLabel(label, command);
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
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        syncMenuFailCounter = 0;
                        askActivity(Constants.SYNC_MENU_ITEM_COMMAND);
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
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        syncMenuFailCounter++;
                        if (syncMenuFailCounter < Constants.MAX_FAILS) {
                            askActivity(Constants.SYNC_MENU_ITEM_COMMAND);
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
        runnableSynchronizationLabelling = new Runnable() {
            @Override
            public void run() {
                // ask for timestamp shift
                PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);

                if (timestampCounter <= Constants.NUMBER_OF_SYNCS) {
                    sendCommand(Constants.TIMESTAMP_COMMAND);                                       // ask for timestamp
                    synchronizationLabellingHandler.postDelayed(this, Constants.SYNCHRONIZATION_LABELLING_SHORT_PERIOD);
                }
                else {
                    timestampCounter = 0;
                    if (random.nextFloat() > Constants.MOOD_ACTIVITY_RATIO) {
                        sendCommand(Constants.ACTIVITY_LABEL_COMMAND);                              // ask for labels (activity)
                    }
                    else {
                        sendCommand(Constants.MOOD_LABEL_COMMAND);                                  // ask for labels (mood)
                    }
                    synchronizationLabellingHandler.postDelayed(this, Constants.SYNCHRONIZATION_LABELLING_LONG_PERIOD);
                }
            }
        };
        synchronizationLabellingHandler.postDelayed(runnableSynchronizationLabelling, Constants.SYNCHRONIZATION_LABELLING_SHORT_PERIOD);         // Request first timestamp
        //------------------------------------------------------------------------------------------

        fusedLocationService = new FusedLocationService(getApplicationContext());

        //------------------------------------------------------------------------------------------
        try {
            File appDir = new File(Environment.getExternalStorageDirectory() + "/smartdays");
            if (!appDir.exists()) {
                appDir.mkdir();
            }

            // Create the files
            String fileName;
            Date date = new Date();
            SharedPreferences settings = getSharedPreferences("smartdays", 0);
            SharedPreferences.Editor editor = settings.edit();

            String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

            fileName = "activity_" + deviceId + "_" + sdf.format(date) + ".csv";
            bufferOutLabelActivity = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutLabelActivity.write(Constants.labelsFileHeader.getBytes());
            logLabel("No activity", Constants.ACTIVITY_LABEL_COMMAND);
            editor.putString("activityFileName", fileName);

            fileName = "mood_" + deviceId + "_" + sdf.format(date) + ".csv";
            bufferOutLabelMood = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            bufferOutLabelMood.write(Constants.labelsFileHeader.getBytes());
            logLabel("Don't know", Constants.MOOD_LABEL_COMMAND);
            editor.putString("moodFileName", fileName);

            fileName = "pebbleAccel_" + deviceId + "_" + sdf.format(date) + ".bin";
            bufferOutPebble = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            editor.putString("pebbleAccelFileName", fileName);

            fileName = "phoneAccel_" + deviceId + "_" + sdf.format(date) + ".bin";
            bufferOutPhoneSynced = new BufferedOutputStream(new FileOutputStream(new File(appDir, fileName)));
            editor.putString("phoneAccelFileName", fileName);

            editor.commit();
            askActivity(Constants.NEW_FILES_COMMAND);

            phoneDataBuffer = new PhoneDataBuffer(Constants.BUFFER_SIZE);

            Log.d(Constants.TAG, "Files created...");

        } catch (IOException ioe) {
            Log.d(Constants.TAG, "Error creating file...");
        }

        //------------------------------------------------------------------------------------------
        serviceMessagesHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Log.d(Constants.TAG, String.format("Service received message: msg=%s", msg));

                switch (msg.what) {
                    case Constants.ACTIVITY_LABEL_COMMAND:
                        logLabel(msg.obj.toString(), Constants.ACTIVITY_LABEL_COMMAND);
                        break;
                    case Constants.SYNC_MENU_ITEM_COMMAND:
                        sendCommand(msg.obj.toString());
                        break;
                }
                super.handleMessage(msg);
            }
        };

        running = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        if (!running) {

            // Display a notification about us starting.  We put an icon in the status bar.
            Notification notification = showNotification();
            startForeground(NOTIFICATION, notification);

            dataloggingReceiver = new SmartDaysPebbleDataLogReceiver(Constants.WATCHAPP_UUID, bufferOutPebble, bufferOutPhoneSynced, phoneDataBuffer);
            //phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer, bufferOutPhone);
            phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer);

            startLoggingPebble();
            startLoggingPhone();
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

    private void freeResources() {
        //------------------------------------------------------------------------------------------ Stop alarms
        synchronizationLabellingHandler.removeCallbacks(runnableSynchronizationLabelling);
        synchronizationLabellingHandler = null;

        //------------------------------------------------------------------------------------------ Stop acquisition receivers
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

        //------------------------------------------------------------------------------------------ Close files
        try {
            bufferOutPebble.close();
            Log.d(Constants.TAG, "bufferOutPebble closed...");
        } catch (IOException ioe) {
            Log.d(Constants.TAG, "Error closing bufferOutPebble...");
        } catch (NullPointerException iae) {
            Log.d(Constants.TAG, "Closing bufferOutPebble... null pointer");
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

        //try {
        //    bufferOutPhone.close();
        //    Log.d(Constants.TAG, "bufferOutPhone closed...");
        //} catch (IOException ioe) {
        //    Log.d(Constants.TAG, "Error closing bufferOutPhone...");
        //} catch (NullPointerException iae) {
        //    Log.d(Constants.TAG, "Closing bufferOutPhone... null pointer");
        //}

        //try {
        //    bufferOutSync.close();
        //   Log.d(Constants.TAG, "bufferOutSync closed...");
        //} catch (IOException ioe) {
        //    Log.d(Constants.TAG, "Error closing bufferOutSync...");
        //} catch (NullPointerException iae) {
        //    Log.d(Constants.TAG, "Closing bufferOutSync... null pointer");
        //}

        //------------------------------------------------------------------------------------------ Stop communication with the Pebble
        unregisterReceiver(pebbleAppMessageDataReceiver);
        unregisterReceiver(pebbleAppMessageAckReceiver);
        unregisterReceiver(pebbleAppMessageNackReceiver);

        PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);               // stop the Pebble application

    }

    private void stop() {
        freeResources();

        SharedPreferences settings = getSharedPreferences("smartdays", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("activityFileName", "");
        editor.putString("moodFileName", "");
        editor.putString("pebbleAccelFileName", "");
        editor.putString("phoneAccelFileName", "");
        editor.commit();
        askActivity(Constants.NEW_FILES_COMMAND);


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

    private void startLoggingPebble() {
        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);

        sendCommand(Constants.START_COMMAND);
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

    private void sendCommand(String item) {
        Log.d(Constants.TAG, "Sending command: " + String.valueOf(Constants.SYNC_MENU_ITEM_COMMAND) + " menuItem: " + item);
        PebbleDictionary data = new PebbleDictionary();
        data.addString(Constants.MENU_ITEM_KEY, item);
        PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), Constants.WATCHAPP_UUID, data, Constants.SYNC_MENU_ITEM_COMMAND);
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

    private void logLabel(String label, int command) {
        try {
            String line = label.concat("," + String.valueOf(System.currentTimeMillis()));
            Location location = fusedLocationService.getLocation();
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
            switch (command) {
                case Constants.ACTIVITY_LABEL_COMMAND:
                    bufferOutLabelActivity.write(line.getBytes());
                    bufferOutLabelActivity.flush();
                    break;
                case Constants.MOOD_LABEL_COMMAND:
                    bufferOutLabelMood.write(line.getBytes());
                    bufferOutLabelMood.flush();
                    break;
            }

        }
        catch (IOException ioe) {
        }
    }

}

