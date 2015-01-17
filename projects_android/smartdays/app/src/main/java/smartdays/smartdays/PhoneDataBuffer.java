package smartdays.smartdays;


import android.util.Log;

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

    public int getIndexAt(int pos) {
        int newPos = position + pos;
        if (newPos < 0) {
            newPos = (size - (-newPos % size)) % size;
        }
        else {
            newPos = newPos % size;
        }
        //Log.d("SmartDAYS", "position=" + String.valueOf(position) + " pos=" + String.valueOf(pos) + " newPos=" + String.valueOf(newPos));
        return newPos;
    }

    public byte[] getMatchingData(long t) {
        int matchingPosition;
        int i;
        int steps = 0;
        long temp;
        long minDifference;
        long tempDifference;

        tempDifference = t - buffer[getIndexAt(-1)].getTimeStamp();                                 // compute the difference between required t and current time (-1)
        i = (int) (tempDifference / Constants.PHONE_SAMPLING_PERIOD_MS);                            // compute the approximated index in the buffer
        //Log.d("SmartDAYS", "tempDiff=" + String.valueOf(tempDifference) + " i=" + String.valueOf(i) + " t=" + String.valueOf(t) + " ts=" + String.valueOf(buffer[getIndexAt(-1)].getTimeStamp()));

        minDifference = Math.abs(t - buffer[getIndexAt(i)].getTimeStamp());                         // compute the difference at the approximated starting point
        tempDifference = Math.abs(t - buffer[getIndexAt(--i)].getTimeStamp());                      // compute the difference one position backwards
        steps++;

        if (tempDifference < minDifference) {                                                       // search backwards
            while (tempDifference < minDifference) {
                minDifference = tempDifference;
                tempDifference = Math.abs(t - buffer[getIndexAt(--i)].getTimeStamp());
                steps++;
            }
            matchingPosition = getIndexAt(++i);
        }
        else {                                                                                      // search forward
            i++;
            temp = minDifference;
            minDifference = tempDifference;
            tempDifference = temp;
            while (tempDifference < minDifference) {
                minDifference = tempDifference;
                tempDifference = Math.abs(t - buffer[getIndexAt(++i)].getTimeStamp());
                steps++;
            }
            matchingPosition = getIndexAt(--i);
        }

        //Log.d("SmartDAYS", "required: " + String.valueOf(t) + " found: " + String.valueOf(buffer[matchingPosition].getTimeStamp()) + " diff:" + String.valueOf(minDifference) + " relPos:" + i + " steps:" + String.valueOf(steps));


        ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.PACKET_SIZE);
        //byteBuffer.putLong(buffer[matchingPosition].getTimeStamp());
        short[] tempValues = buffer[matchingPosition].getXYZ();
        for (i = 0; i < 3; i++) {
            byteBuffer.putShort(tempValues[i]);
        }
        return byteBuffer.array();
    }

}
