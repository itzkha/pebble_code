package smartdays.smartdays;

import java.util.UUID;

/**
 * Created by hector on 03/12/14.
 */
public class Constants {
    public static final UUID WATCHAPP_UUID = UUID.fromString("f3cdf03e-a639-4196-88a0-51fe502ab3d4");
    public static final String TAG = "SmartDAYS";
    public static final int DATA_LOG_TAG_ACCEL = 51;
    public static final int PEBBLE_BUFFER_SIZE = 25;                                                // 1 second
    public static final int PEBBLE_SAMPLING_PERIOD_MS = 40;                                         // 40 mS
    public static final int PACKET_SIZE = 14;                                                       // long + short + short + short
    public static final int PHONE_SAMPLING_PERIOD_MS = PEBBLE_SAMPLING_PERIOD_MS;                   // 40 mS
    public static final int PHONE_SAMPLING_PERIOD_NS = PEBBLE_SAMPLING_PERIOD_MS * 1000000;         // 40 mS
    public static final int SYNCHRONIZATION_LABELLING_LONG_PERIOD = 1000 * 60 * 10;                 // 10 minutes
    public static final int SYNCHRONIZATION_LABELLING_SHORT_PERIOD = 1000;                          // 1 second
    public static final int LOCATION_PERIOD = 1000 * 60 * 5;                                        // 5 minutes
    public static final int MAX_FAILS = 5;
    public static final int NUMBER_OF_SYNCS = 5;
    public static final int BUFFER_SIZE = (int) (5 * 60 / ((float)PHONE_SAMPLING_PERIOD_MS/1000));  //~ 5 minutes

    public static final int LABEL_KEY = 0xf00d50da;
    public static final int TIMESTAMP_KEY = 0xdeadbeef;
    public static final int COMMAND_KEY = 0xcafebabe;
    public static final int START_COMMAND = 5;
    public static final int STOP_COMMAND = 12;
    public static final int TIMESTAMP_COMMAND = 17;
    public static final int ACTIVITY_LABEL_COMMAND = 21;
    public static final int MOOD_LABEL_COMMAND = 28;
    public static final int NEW_FILES_COMMAND = 27;
    public static final int UPDATE_ACTIVITY_FILE = 23;

    public static final int SERVICE_STOPPED = 43;

    public static final String LABELS_FILE_HEADER = "label,timestamp\n";
    public static final String LOCATION_FILE_HEADER = "timestamp,latitute,longitude,altitute,accuracy,provider\n";

    public static final String IP_SERVER = "193.134.218.36:5000";
}
