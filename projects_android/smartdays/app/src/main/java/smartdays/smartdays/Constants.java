package smartdays.smartdays;

import java.util.UUID;

/**
 * Created by hector on 03/12/14.
 */
public class Constants {
    public static final UUID WATCHAPP_UUID = UUID.fromString("f3cdf03e-a639-4196-88a0-51fe502ab3d4");
    public static final int DATA_LOG_TAG_ACCEL = 51;
    public static final int PEBBLE_BUFFER_SIZE = 25;                                    // 1 second
    public static final int PEBBLE_SAMPLING_PERIOD = 40;                                // mS
    public static final int PACKET_SIZE = 14;                                           // long + short + short + short
    public static final int TOO_FREQUENT_MEASURES = PEBBLE_SAMPLING_PERIOD / 2;         // 20 mS

    public static final int COMMAND_KEY = 0xcafebabe;
    public static final int START_COMMAND = 5;
    public static final int STOP_COMMAND = 12;
    public static final int TIMESTAMP_COMMAND = 17;

    public static final int TIMESTAMP_KEY = 0xdeadbeef;

}
