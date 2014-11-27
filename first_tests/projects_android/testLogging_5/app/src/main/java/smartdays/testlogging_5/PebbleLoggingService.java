package smartdays.testlogging_5;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by hector on 26/11/14.
 */
public class PebbleLoggingService extends IntentService {

    private BufferedOutputStream bufferOut = null;
    private static boolean running;

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
                int counter = 0;
                File root = Environment.getExternalStorageDirectory();
                FileOutputStream f = new FileOutputStream(new File(root, "testCounting"));

                bufferOut = new BufferedOutputStream(f);
                Log.d("PebbleLoggingService", "File testCounting created...");

                while (running) {
                    Log.d("PebbleLoggingService", String.valueOf(counter));
                    bufferOut.write(String.valueOf(counter).getBytes());
                    android.os.SystemClock.sleep(200);
                    counter++;
                }
                bufferOut.close();
                Log.d("PebbleLoggingService", "File testCounting closed...");
            }
            catch (IOException ioe) {
                Log.d("PebbleLoggingService", "Error writing to file...");
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
