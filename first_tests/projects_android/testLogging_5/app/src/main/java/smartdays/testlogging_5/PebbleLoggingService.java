package smartdays.testlogging_5;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;
import com.getpebble.android.kit.PebbleKit;
import java.util.UUID;

/**
 * Created by hector on 26/11/14.
 */
public class PebbleLoggingService extends Service {

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;

    private BufferedOutputStream bufferOut = null;

    private static final UUID WATCHAPP_UUID = UUID.fromString("e92cf086-2ec1-4814-b360-340db0da9aa6");
    private static final int DATA_LOG_TAG_ACCEL = 51;
    private PebbleDataLogReceiver dataloggingReceiver;

    @Override
    public void onCreate() {
        Log.d("PebbleLoggingService", "Creating...");
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        try {
            File root = Environment.getExternalStorageDirectory();
            FileOutputStream f = new FileOutputStream(new File(root, "testCapture"));

            // Create the file
            bufferOut = new BufferedOutputStream(f);
            Log.d("PebbleLoggingService", "File testCapture created...");

            // Define data reception behavior
            dataloggingReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID) {
                private int packetCounter = 0;

                @Override
                public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                    if (tag.intValue() == DATA_LOG_TAG_ACCEL) {
                        try {
                            // Get the acceleration value
                            if (bufferOut != null) {
                                bufferOut.write(data);
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

        } catch (IOException ioe) {
            Log.d("PebbleLoggingService", "Error creating to file...");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        // Cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();

        try {
            bufferOut.close();
            Log.d("PebbleLoggingService", "File testCapture closed...");

            unregisterReceiver(dataloggingReceiver);
            Log.d("PebbleLoggingService", "Unregistering receiver...");
        }
        catch (IOException ioe) {
            Log.d("PebbleLoggingService", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("PebbleLoggingService", "Ending service... null pointer");
        }
        catch (IllegalArgumentException iae) {
            Log.d("PebbleLoggingService", "Unregistering receiver... already unregistered");
        }
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


}
