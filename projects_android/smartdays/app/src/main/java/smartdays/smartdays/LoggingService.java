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
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by hector on 01/12/14.
 */
public class LoggingService extends Service {

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    PhoneSensorEventListener phoneSensorEventListener;

    private BufferedOutputStream bufferOutPebble = null;
    private BufferedOutputStream bufferOutPhone = null;
    private PhoneDataBuffer phoneDataBuffer;

    private PebbleKit.PebbleDataLogReceiver dataloggingReceiver;

    private static boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        Log.d("SmartDAYS", "Creating...");
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        try {
            File root = Environment.getExternalStorageDirectory();

            // Create the file
            bufferOutPebble = new BufferedOutputStream(new FileOutputStream(new File(root, "testPebbleAccel")));
            bufferOutPhone = new BufferedOutputStream(new FileOutputStream(new File(root, "testPhoneAccel")));
            phoneDataBuffer = new PhoneDataBuffer(1000);
            Log.d("SmartDAYS", "Files created...");

            dataloggingReceiver = new SmartDaysPebbleDataLogReceiver(Constants.WATCHAPP_UUID, bufferOutPebble, bufferOutPhone, phoneDataBuffer);
            phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer);

        } catch (IOException ioe) {
            Log.d("SmartDAYS", "Error creating file...");
        }
/*
        PebbleKit.registerReceivedDataHandler(this, new PebbleKit.PebbleDataReceiver(Constants.WATCHAPP_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                Log.d("SmartDAYS", "Received value=" + data.getInteger(Constants.START_STOP_KEY));
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);

                if (data.getInteger(Constants.START_STOP_KEY) == Constants.STOP_MESSAGE) {
                    if (LoggingService.isRunning()) {
                        stopSelf();
                    }
                }
            }
        });*/
        running = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        if (!running) {
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

        try {
            unregisterReceiver(dataloggingReceiver);
            Log.d("SmartDAYS", "Unregistering receiver...");
        }
        catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Ending service... null pointer");
        }
        catch (IllegalArgumentException iae) {
            Log.d("SmartDAYS", "Unregistering receiver... already unregistered");
        }

        sensorManager.unregisterListener(phoneSensorEventListener);

        try {
            bufferOutPebble.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        }
        catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Pebble... null pointer");
        }

        try {
            bufferOutPhone.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        }
        catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Phone... null pointer");
        }

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
    }

    private void startLoggingPhone() {
        sensorManager.registerListener(phoneSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

}
