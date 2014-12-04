package smartdays.smartdays;


import java.nio.ByteBuffer;

/**
 * Created by hector on 02/12/14.
 */
public class PhoneDataBuffer {

    private PhoneData[] buffer;
    private int size;
    private int position;

    public PhoneDataBuffer(int n) {
        size = n;
        position = 0;
        buffer = new PhoneData[size];

        for (int i = 0; i < size; i++) {
            buffer[i] = new PhoneData();
        }
    }

    public void putData(PhoneData pd) {
        buffer[position] = pd;
        position++;
        if (position >= size) {
            position = 0;
        }
    }

    public byte[] getMatchingData(long t) {
        int newPos;
        int matchingPosition = 0;
        long matchingDifference = Long.MAX_VALUE;
        long tempDifference;

        for (int i = 1; i <= size; i++) {
            newPos = position - i;
            if (newPos < 0) {
                newPos += size;
            }
            tempDifference = Math.abs(buffer[newPos].getTimeStamp() - t);
            if (tempDifference < matchingDifference) {
                matchingPosition = newPos;
                matchingDifference = tempDifference;
                if (matchingDifference < Constants.MAX_MATCHING_DIFFERENCE) {
                    break;
                }
            }
        }
        //Log.d("SmartDAYS", "TimeStamp phone: " + String.valueOf(buffer[matchingPosition].getTimeStamp()) + " - Diff: " + String.valueOf(matchingDifference));

        ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.PACKET_SIZE);
        byteBuffer.putLong(buffer[matchingPosition].getTimeStamp());
        short[] tempValues = buffer[matchingPosition].getXYZ();
        for (int i = 0; i < 3; i++) {
            byteBuffer.putShort(tempValues[i]);
        }
        return byteBuffer.array();
    }

}
