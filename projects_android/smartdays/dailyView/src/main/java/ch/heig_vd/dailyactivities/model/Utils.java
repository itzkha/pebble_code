package ch.heig_vd.dailyactivities.model;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Some utility static methods.
 */
public class Utils {

    /**
     * Formats given hour and minute integers into HH:mm string,
     * @param hour The integer value of the hour
     * @param minute The integer value of the minute
     * @return The HH:mm string of the hour and minute integers
     */
    public static String formatHourMinute(int hour, int minute) {
        return String.format("%02d", hour) + ":"
                + String.format("%02d", minute);
    }

    /**
     * Formats given hour and minute integers into HH[sep]mm string,
     * @param hour The integer value of the hour
     * @param minute The integer value of the minute
     * @param sep The separator to use between hours and minutes
     * @return The HH[sep]mm string of the hour and minute integers
     */
    public static String formatHourMinute(int hour, int minute, char sep) {
        return String.format("%02d", hour) + sep
                + String.format("%02d", minute);
    }

    /**
     * Creates and returns a timestamp from given HH:mm string by adding the today date.
     * @param hourMin a HH:mm string
     * @return a timestamp from given HH:mm string by adding the today date
     */
    public static Timestamp createTimestampFromHourMin(String hourMin) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getDefault());
        String today = sdf.format(new Date().getTime());
        return Timestamp.valueOf(today + " " + hourMin + ":00.000000000");
    }

    /**
     * Creates and returns a timestamp from given HH:mm:ss string by adding the today date.
     * @param hourMinSec a HH:mm:ss string
     * @return a timestamp from given HH:mm:ss string by adding the today date
     */
    public static Timestamp createTimestampFromHourMinSec(String hourMinSec) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getDefault());
        String today = sdf.format(new Date().getTime());
        return Timestamp.valueOf(today + " " + hourMinSec + ".000000000");
    }

    /**
     * Creates and returns a HH:mm string from a given timestamp.
     * @param time a timestamp
     * @return a HH:mm string from a given timestamp
     */
    public static String createStringHourMinFromTimestamp(Timestamp time) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(time);
    }

    /**
     * Creates and returns an integer value of the minutes from a given timestamp.
     * @param time a timestamp
     * @return an integer value of the minutes from a given timestamp
     */
    public static int getHoursFromTimestamp(Timestamp time) {
        return Integer.parseInt(createStringHourMinFromTimestamp(time).split(":")[0]);
    }

    /**
     * Creates and returns an integer value of the minutes from a given timestamp.
     * @param time a timestamp
     * @return an integer value of the minutes from a given timestamp
     */
    public static int getMinutesFromTimestamp(Timestamp time) {
        return Integer.parseInt(createStringHourMinFromTimestamp(time).split(":")[1]);
    }
}
