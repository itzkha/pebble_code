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
    public static final int MAX_MATCHING_DIFFERENCE = PEBBLE_SAMPLING_PERIOD / 2;
    public static final int PACKET_SIZE = 14;                                           // long + short + short + short
    public static final int TOO_FREQUENT_MEASURES = 16000000;                           // 16 mS

}
