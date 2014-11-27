package smartdays.testlogging_5;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;
import com.getpebble.android.kit.PebbleKit;
import java.util.UUID;

/**
 * Created by hector on 26/11/14.
 */
public class PebbleLoggingService extends IntentService {

    private BufferedOutputStream bufferOut = null;
    private static boolean running;

    private static final UUID WATCHAPP_UUID = UUID.fromString("e92cf086-2ec1-4814-b360-340db0da9aa6");
    private static final int DATA_LOG_TAG_ACCEL = 51;
    private PebbleDataLogReceiver dataloggingReceiver;

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
    */
    public PebbleLoggingService() {
        super("PebbleLoggingService");
    }

    /**
     * The IntentService calls this method from the default worker thread with
     * the intent that started the service. When this method returns, IntentService
     * stops the service, as appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        if (!running) {
            running = true;

            try {
                File root = Environment.getExternalStorageDirectory();
                FileOutputStream f = new FileOutputStream(new File(root, "testCapture"));

                bufferOut = new BufferedOutputStream(f);
                Log.d("PebbleLoggingService", "File testCapture created...");
            }
            catch (IOException ioe) {
                Log.d("PebbleLoggingService", "Error creating to file...");
            }

            if (bufferOut != null) {
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

                while (running) {
                    android.os.SystemClock.sleep(500);
                }
                Log.d("PebbleLoggingService", "Ending loop service...");

                try {
                    bufferOut.close();
                    Log.d("PebbleLoggingService", "File testCapture closed...");
                } catch (IOException ioe) {
                    Log.d("PebbleLoggingService", "Error closing file...");
                }

                try {
                    unregisterReceiver(dataloggingReceiver);
                    Log.d("PebbleLoggingService", "Unregistering receiver...");
                }
                catch (NullPointerException iae) {
                    Log.d("PebbleLoggingService", "Unregistering receiver... null pointer");
                }
                catch (IllegalArgumentException iae) {
                    Log.d("PebbleLoggingService", "Unregistering receiver... already unregistered");
                }

            }

        }
    }

    @Override
    public void onDestroy() {
        running = false;
    }

    public static  boolean isRunning() {
        return running;
    }

}
