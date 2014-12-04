package smartdays.smartdays;

/**
 * Created by hector on 02/12/14.
 */
public class PhoneData {

    private long timestamp;
    private short[] xyz;

    public PhoneData() {
        timestamp = 0;
        xyz = new short[3];
        for (int i = 0; i < 3; i++) {
            xyz[i] = 0;
        }
    }

    public PhoneData(long t, float[] values) {
        timestamp = t / 1000000;
        xyz = new short[3];
        for (int i = 0; i < 3; i++) {
            xyz[i] = (short) (100 * values[i]);
        }
    }

    public long getTimeStamp() {
        return timestamp;
    }

    public short[] getXYZ() {
        return xyz;
    }
}
