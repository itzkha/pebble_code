package smartdays.smartdays;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
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
import java.util.TimeZone;

/**
 * Created by hector on 01/12/14.
 */
public class LoggingService extends Service {

    private static LoggingService instance = null;

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    PhoneSensorEventListener phoneSensorEventListener;

    private BufferedOutputStream bufferOutPebble = null;
    private BufferedOutputStream bufferOutPhoneSynced = null;
    private BufferedOutputStream bufferOutPhone = null;
    private PhoneDataBuffer phoneDataBuffer;

    private SmartDaysPebbleDataLogReceiver dataloggingReceiver;
    private PebbleKit.PebbleDataReceiver pebbleAppMessageDataReceiver;
    private PebbleKit.PebbleAckReceiver pebbleAppMessageAckReceiver;
    private PebbleKit.PebbleNackReceiver pebbleAppMessageNackReceiver;
    private PowerManager.WakeLock wakeLock;

    private static boolean running = false;
    private int startCounter = 0;
    private int stopCounter = 0;


    public static boolean isRunning() {
        return running;
    }
    public static LoggingService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        Log.d("SmartDAYS", "Creating...");
        if (instance == null) {
            instance = this;
        }
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartDAYS");

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        pebbleAppMessageDataReceiver = new PebbleKit.PebbleDataReceiver(Constants.WATCHAPP_UUID) {
            private long ackTime = 0;
            private long deltaCom = 0;
            TimeZone tz = TimeZone.getDefault();
            long offsetFromUTC = tz.getOffset(System.currentTimeMillis());

            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {

                switch (data.getInteger(Constants.COMMAND_KEY).intValue()) {
                    case Constants.START_COMMAND:
                        Log.d("SmartDAYS", "START received");
                        /*if (!LoggingService.isRunning()) {
                            ackTime = System.currentTimeMillis();
                        }*/
                        break;
                    case Constants.STOP_COMMAND:
                        Log.d("SmartDAYS", "STOP received");
                        /*if (LoggingService.isRunning()) {
                            stopSelf();
                        }*/
                        break;
                    case Constants.TIMESTAMP_COMMAND:
                        Log.d("SmartDAYS", "TIMESTAMP received");
                        /*deltaCom = (System.currentTimeMillis() - ackTime) / 2;
                        ackTime = System.currentTimeMillis();

                        offsetFromUTC = (offsetFromUTC + ((ByteBuffer.wrap(data.getBytes(Constants.TIMESTAMP_KEY)).order(ByteOrder.LITTLE_ENDIAN).getLong() + deltaCom) - ackTime)) / 2;
                        Log.d("SmartDAYS", "deltaCom=" + String.valueOf(deltaCom) + " new offset=" + String.valueOf(offsetFromUTC));
                        dataloggingReceiver.setOffset(offsetFromUTC);
                        */
                        break;
                }
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
            }
        };
        PebbleKit.registerReceivedDataHandler(this, pebbleAppMessageDataReceiver);

        pebbleAppMessageAckReceiver = new PebbleKit.PebbleAckReceiver(Constants.WATCHAPP_UUID) {
            @Override
            public void receiveAck(Context context, int transactionId) {
                Log.i("SmartDAYS", "Received ack for transaction " + transactionId);

                switch (transactionId) {
                    case Constants.START_COMMAND:
                        startCounter = 0;
                        break;
                    case Constants.STOP_COMMAND:
                        stopCounter = 0;
                        stop();
                        break;
                }
            }
        };
        PebbleKit.registerReceivedAckHandler(this, pebbleAppMessageAckReceiver);

        pebbleAppMessageNackReceiver = new PebbleKit.PebbleNackReceiver(Constants.WATCHAPP_UUID) {
            @Override
            public void receiveNack(Context context, int transactionId) {
                Log.i("SmartDAYS", "Received nack for transaction " + transactionId);

                switch (transactionId) {
                    case Constants.START_COMMAND:
                        startCounter++;
                        if (startCounter < 5) {
                            sendCommand(Constants.START_COMMAND);
                        } else {
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                            stop();
                        }
                        break;
                    case Constants.STOP_COMMAND:
                        stopCounter++;
                        if (stopCounter < 5) {
                            sendCommand(Constants.STOP_COMMAND);
                        } else {
                            // Tell the user we stopped
                            Toast.makeText(instance, R.string.pebble_not_responding, Toast.LENGTH_SHORT).show();
                            stop();
                        }
                        break;
                }
            }
        };
        PebbleKit.registerReceivedNackHandler(this, pebbleAppMessageNackReceiver);

        try {
            File root = Environment.getExternalStorageDirectory();

            // Create the file
            bufferOutPebble = new BufferedOutputStream(new FileOutputStream(new File(root, "testPebbleAccel")));
            bufferOutPhoneSynced = new BufferedOutputStream(new FileOutputStream(new File(root, "testPhoneSyncedAccel")));
            bufferOutPhone = new BufferedOutputStream(new FileOutputStream(new File(root, "testPhoneAccel")));
//            bufferOutPhone = null;
            phoneDataBuffer = new PhoneDataBuffer(15000);       //5 minutes

            Log.d("SmartDAYS", "Files created...");

        } catch (IOException ioe) {
            Log.d("SmartDAYS", "Error creating file...");
        }
        running = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        if (!running) {
            dataloggingReceiver = new SmartDaysPebbleDataLogReceiver(Constants.WATCHAPP_UUID, bufferOutPebble, bufferOutPhoneSynced, phoneDataBuffer);
            phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer, bufferOutPhone);

            startLoggingPebble();
            startLoggingPhone();
            running = true;

        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();

        //------------------------------------------------------------------------------------------ Stop acquisition receivers
        try {
            unregisterReceiver(dataloggingReceiver);
            Log.d("SmartDAYS", "Unregistering receiver...");
        } catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Ending service... null pointer");
        } catch (IllegalArgumentException iae) {
            Log.d("SmartDAYS", "Unregistering receiver... already unregistered");
        }

        sensorManager.unregisterListener(phoneSensorEventListener);
        wakeLock.release();

        //------------------------------------------------------------------------------------------ Close files
        try {
            bufferOutPebble.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        } catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        } catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Pebble... null pointer");
        }

        try {
            bufferOutPhoneSynced.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        } catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        } catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Phone Synced... null pointer");
        }

        try {
            bufferOutPhone.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        } catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        } catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Phone... null pointer");
        }

        //------------------------------------------------------------------------------------------ Stop communication with the Pebble
        unregisterReceiver(pebbleAppMessageDataReceiver);
        unregisterReceiver(pebbleAppMessageAckReceiver);
        unregisterReceiver(pebbleAppMessageNackReceiver);

        PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.WATCHAPP_UUID);               // stop the Pebble application
        running = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
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
    }

    private void startLoggingPebble() {
        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);

        PebbleDictionary data = new PebbleDictionary();
        data.addUint8(Constants.COMMAND_KEY, (byte) Constants.START_COMMAND);
        PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), Constants.WATCHAPP_UUID, data, Constants.START_COMMAND);
    }

    private void startLoggingPhone() {
        sensorManager.registerListener(phoneSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        wakeLock.acquire();
    }

    private void sendCommand(int command) {
        PebbleDictionary data = new PebbleDictionary();
        data.addUint8(Constants.COMMAND_KEY, (byte) command);
        PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), Constants.WATCHAPP_UUID, data, command);
    }

    private void stop() {
        if (MainActivity.serviceMessagesHandler != null) {
            // Tell the user we stopped
            Message msg = Message.obtain();
            msg.what = Constants.SERVICE_STOPPED;
            MainActivity.serviceMessagesHandler.sendMessage(msg);
        }
        // Then stop
        stopSelf();
    }
}

