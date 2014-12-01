package smartdays.testlogging_5;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by hector on 01/12/14.
 */
public class LoggingService extends Service {

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;

    private BufferedOutputStream bufferOutPebble = null;
    private BufferedOutputStream bufferOutPhone = null;

    private static final UUID WATCHAPP_UUID = UUID.fromString("e92cf086-2ec1-4814-b360-340db0da9aa6");
    private static final int DATA_LOG_TAG_ACCEL = 51;
    private PebbleKit.PebbleDataLogReceiver dataloggingReceiver;

    private static final int accelPeriod = 10;
    private static final int packetSize = 16;
    private static boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        Log.d("PebbleLoggingService", "Creating...");
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        try {
            File root = Environment.getExternalStorageDirectory();

            // Create the file
            bufferOutPebble = new BufferedOutputStream(new FileOutputStream(new File(root, "testPebbleAccel")));
            bufferOutPhone = new BufferedOutputStream(new FileOutputStream(new File(root, "testPhoneAccel")));
            Log.d("PebbleLoggingService", "File testCapture created...");

        } catch (IOException ioe) {
            Log.d("PebbleLoggingService", "Error creating file...");
        }
        running = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        if (!isRunning()) {
            PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);
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
            Log.d("PebbleLoggingService", "Unregistering receiver...");
        }
        catch (NullPointerException iae) {
            Log.d("PebbleLoggingService", "Ending service... null pointer");
        }
        catch (IllegalArgumentException iae) {
            Log.d("PebbleLoggingService", "Unregistering receiver... already unregistered");
        }

        try {
            bufferOutPebble.close();
            Log.d("PebbleLoggingService", "File testCapture closed...");
        }
        catch (IOException ioe) {
            Log.d("PebbleLoggingService", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("PebbleLoggingService", "Closing file Pebble... null pointer");
        }

        try {
            bufferOutPhone.close();
            Log.d("PebbleLoggingService", "File testCapture closed...");
        }
        catch (IOException ioe) {
            Log.d("PebbleLoggingService", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("PebbleLoggingService", "Closing file Phone... null pointer");
        }

        running = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("PebbleLoggingService", "Accuracy changed");
    }

    public final void onSensorChanged(SensorEvent event) {
        long timestamp = event.timestamp;
        float[] meanAccel = new float[3];
        accelCurrent[0] += event.values[0];
        accelCurrent[1] += event.values[1];
        accelCurrent[2] += event.values[2];
        counterAccel++;

        if (counterAccel >= accelPeriod) {
            meanAccel[0] = accelCurrent[0] / accelPeriod;
            meanAccel[1] = accelCurrent[1] / accelPeriod;
            meanAccel[2] = accelCurrent[2] / accelPeriod;

            accelCurrent[0] = 0;
            accelCurrent[1] = 0;
            accelCurrent[2] = 0;
            counterAccel = 0;

            try {
                if (counterPackets == 0) {
                    bufferOutPhone.write(ByteBuffer.allocate(Long.SIZE).putLong(timestamp).array());
                }
                bufferOutPhone.write(ByteBuffer.allocate(Float.SIZE).putFloat(meanAccel[0]).array());
                bufferOutPhone.write(ByteBuffer.allocate(Float.SIZE).putFloat(meanAccel[1]).array());
                bufferOutPhone.write(ByteBuffer.allocate(Float.SIZE).putFloat(meanAccel[2]).array());
                counterPackets++;
            }
            catch (IOException ioe) {
            }

            if (counterPackets >= packetSize) {
                counterPackets = 0;
            }
        }
    }
    */

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
        // Define data reception behavior
        dataloggingReceiver = new PebbleKit.PebbleDataLogReceiver(WATCHAPP_UUID) {
            private int packetCounter = 0;

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                if (tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    try {
                        // Get the acceleration value
                        if (bufferOutPebble != null) {
                            bufferOutPebble.write(data);
                            packetCounter++;
                            Log.d("PebbleLoggingService", "Packets received: " + String.valueOf(packetCounter));
                        }
                    } catch (IOException ioe) {
                        Log.d("PebbleLoggingService", "Error writing data...");
                    }
                }
            }
        };

        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);
    }

    private void startLoggingPhone() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(new Runnable() {
            SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            @Override
            public void run() {
                sensorManager.registerListener(
                        new SensorEventListener() {

                            private int counterAccel = 0;
                            private int counterPackets = 0;
                            private float[] accelCurrent = new float[3];

                            @Override
                            public void onSensorChanged(SensorEvent sensorEvent) {
                                long timestamp = sensorEvent.timestamp / 1000000;
                                short[] meanAccel = new short[3];
                                accelCurrent[0] += sensorEvent.values[0];
                                accelCurrent[1] += sensorEvent.values[1];
                                accelCurrent[2] += sensorEvent.values[2];
                                counterAccel++;

                                if (counterAccel >= accelPeriod) {
                                    meanAccel[0] = (short)(100 * accelCurrent[0] / accelPeriod);
                                    meanAccel[1] = (short)(100 * accelCurrent[1] / accelPeriod);
                                    meanAccel[2] = (short)(100 * accelCurrent[2] / accelPeriod);

                                    accelCurrent[0] = 0;
                                    accelCurrent[1] = 0;
                                    accelCurrent[2] = 0;
                                    counterAccel = 0;

                                    try {
                                        if (counterPackets == 0) {
                                            bufferOutPhone.write(ByteBuffer.allocate(8).putLong(timestamp).array());
                                        }
                                        bufferOutPhone.write(ByteBuffer.allocate(2).putShort(meanAccel[0]).array());
                                        bufferOutPhone.write(ByteBuffer.allocate(2).putShort(meanAccel[1]).array());
                                        bufferOutPhone.write(ByteBuffer.allocate(2).putShort(meanAccel[2]).array());
                                        counterPackets++;
                                    }
                                    catch (IOException ioe) {
                                    }

                                    if (counterPackets >= packetSize) {
                                        counterPackets = 0;
                                    }
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int i) {

                            }
                        }, accelerometer, 1000
                );
            }
        });
    }

}
