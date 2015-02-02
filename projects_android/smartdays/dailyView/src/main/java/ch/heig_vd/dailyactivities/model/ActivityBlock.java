package ch.heig_vd.dailyactivities.model;

import java.sql.Timestamp;

public class ActivityBlock {
    private Task activity;
    private Timestamp begin;
    private Timestamp end;
    private boolean selected = false;

    /**
     * Creates a new activity block from an activity name and the corresponding Timestamps values
     * of starting and ending time of the activity.
     * @param activity The activity name
     * @param begin The starting time timestamp of the activity
     * @param end The ending time timestamp of the activity
     */
    public ActivityBlock(Task activity, Timestamp begin, Timestamp end) {
        this.activity = activity;
        setBegin(begin);
        setEnd(end);
    }

    /**
     * Creates a new activity block from an activity name and the corresponding Timestamps values
     * of starting and ending time of the activity.
     * @param activity The activity name
     * @param begin The starting time timestamp of the activity
     */
    public ActivityBlock(Task activity, Timestamp begin) {
        this.activity = activity;
        setBegin(begin);
        end = null;
    }

    /**
     * Creates a new activity block from an activity name and the corresponding Timestamps values
     * of starting and ending time of the activity.
     * @param activity The activity name
     * @param begin The starting time timestamp of the activity
     * @param end The ending time timestamp of the activity
     */
    public ActivityBlock(Task activity, Timestamp begin, String end) {
        this.activity = activity;
        setBegin(begin);
        setEnd(end);
    }

    /**
     * Creates a new activity block from an activity name and the corresponding HH:mm formatted
     * string values of starting and ending time of the activity.
     * @param activity The activity name
     * @param begin The HH:mm string starting time of the activity
     * @param end The HH:mm string ending time of the activity
     */
    public ActivityBlock(Task activity, String begin, String end) {
        this.activity = activity;
        setBegin(begin);
        setEnd(end);
    }

    /**
     * Creates a new activity block from an activity name and the corresponding integer values of
     * begin hour, begin minute, end hour, end minute.
     * @param activity The activity name
     * @param beginH The start hour of the activity
     * @param beginM The start minute of the activity
     * @param endH The end hour of the activity
     * @param endM The end minute of the activity
     */
    public ActivityBlock(Task activity, int beginH, int beginM, int endH, int endM) {
        this.activity = activity;
        setBegin(beginH, beginM);
        setEnd(endH, endM);
    }
/*
    /**
     * Returns the string name of the activity.
     * @return The string name of the activity
     /
    public String getActivityName() {
        return activity.getName();
    }
*/
    /**
     * Returns the activity.
     * @return The activity
     */
    public Task getTask() {
        return activity;
    }

    /**
     * Returns the starting timestamp of the activity.
     * @return The starting timestamp of the activity
     */
    public Timestamp getBegin() {
        return begin;
    }

    /**
     * Returns the ending timestamp of the activity.
     * @return The ending timestamp of the activity
     */
    public Timestamp getEnd() {
        return end;
    }

    /**
     * Returns the starting hour integer value of the activity.
     * @return The starting hour integer value of the activity
     */
    public int getStartHour() {
        return Utils.getHoursFromTimestamp(begin);
    }

    /**
     * Returns the starting minute integer value of the activity.
     * @return The starting minute integer value of the activity
     */
    public int getStartMinute() {
        return Utils.getMinutesFromTimestamp(begin);
    }

    /**
     * Returns the ending hour integer value of the activity.
     * @return The ending hour integer value of the activity
     */
    public int getEndHour() {
        return Utils.getHoursFromTimestamp(end);
    }

    /**
     * Returns the ending minute integer value of the activity.
     * @return The ending minute integer value of the activity
     */
    public int getEndMinute() {
        return Utils.getMinutesFromTimestamp(end);
    }


    /**
     * Returns a friendly HH:mm string version of the beginning timestamp.
     * @return a friendly HH:mm string version of the beginning timestamp
     */
    public String getBeginningString() {
        return Utils.createStringHourMinFromTimestamp(begin);
    }

    /**
     * Returns a friendly HH:mm string version of the ending timestamp.
     * @return a friendly HH:mm string version of the ending timestamp
     */
    public String getEndingString() {
        return Utils.createStringHourMinFromTimestamp(end);
    }

    /**
     * Returns the length in minutes of this activity block.
     * @return the length in minutes of this activity block
     */
    public int getLengthInMinutes() {
        return (int) ((end.getTime() - begin.getTime()) / 60000);
    }

    /**
     * Changes the activity to a new one.
     * @param activity the new activity
     */
    public void setTask(Task activity) {
        this.activity = activity;
    }

    /**
     * Sets the starting timestamp to be today at the provided timestamp.
     * @param begin the starting timestamp
     */
    public void setBegin(Timestamp begin) {
        this.begin = begin;
    }

    /**
     * Sets the starting timestamp to be today at the provided hour and minute integer values.
     * @param begin the HH:mm value of the starting time
     */
    public void setBegin(String begin) {
        setBegin(Utils.createTimestampFromHourMin(begin));
    }

    /**
     * Sets the starting timestamp to be today at the provided hour and minute integer values.
     * @param hour the starting hour integer value
     * @param minute the starting minute integer value
     */
    public void setBegin(int hour, int minute) {
        setBegin(Utils.formatHourMinute(hour, minute));
    }

    /**
     * Sets the ending timestamp to be today at the provided timestamp.
     * @param end the ending timestamp
     */
    public void setEnd(Timestamp end) {
        this.end = end;
    }
    /**
     * Sets the ending timestamp to be today at the provided hour and minute integer values.
     * @param end the HH:mm value of the ending time
     */
    public void setEnd(String end) {
        setEnd(Utils.createTimestampFromHourMin(end));
    }

    /**
     * Sets the ending timestamp to be today at the provided hour and minute integer values.
     * @param hour the ending hour integer value
     * @param minute the ending minute integer value
     */
    public void setEnd(int hour, int minute) {
        setEnd(Utils.formatHourMinute(hour, minute));
    }

    /**
     * Checks if the activity block has a name and a correct length.
     * @return true if the name is not null and the length in minute is greater than zero
     */
    public boolean checkValid() {
        if(this.end != null && this.begin != null) {
            if (activity.getName() == null) {
                throw new RuntimeException(getNameExceptionString());
            } else {
                int length = getLengthInMinutes();
                if (length < 0) {
                    throw new RuntimeException(getLengthExceptionString());
                } else if (length == 0) {
                    throw new RuntimeException(getZeroLengthExceptionString());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns a custom exception message for empty name
     * @return a custom exception message for empty name
     */
    public static String getNameExceptionString() {
        return "Error: the activity has an empty name!";
    }

    /**
     * Returns a custom exception message for negative length
     * @return a custom exception message for negative length
     */
    public static String getLengthExceptionString() {
        return "Error: the activity length is negative!";
    }

    /**
     * Returns a custom exception message for zero length
     * @return a custom exception message for zero length
     */
    public static String getZeroLengthExceptionString() {
        return "Error: the activity length is zero!";
    }

    /**
     * Returns a [ActivityName: HH:mm - HH:mm] formatted string.
     * @return a [ActivityName: HH:mm - HH:mm] formatted string
     */
    public String toString() {
        if (getEnd() == null) {
            return activity.getName() + "=" + getBeginningString() + "-?";
        }
        else {
            return activity.getName() + "=" + getBeginningString() + "-" + getEndingString();
        }
    }

    /**
     * Checks if two ActivityBlocks are equivalent.
     * @return true if two ActivityBlocks are equivalent
     */
    @Override
    public boolean equals(Object o) {
        if(o == null) {
            return false;
        } else if(o.getClass() == ActivityBlock.class) {
            ActivityBlock temp = (ActivityBlock) o;
            return temp.activity.equals(activity) &&
                    temp.begin.compareTo(begin) == 0 &&
                    temp.end.compareTo(end) == 0;
        }
        return super.equals(o);
    }

    /**
     * Returns a new ActivityBlock from a [ActivityName: HH:mm - HH:mm] formatted string.
     * @return a new ActivityBlock from a [ActivityName: HH:mm - HH:mm] formatted string
     */
    public static ActivityBlock fromString(String activityBlock) {
        String[] container = activityBlock.split("=");
        String name = container[0];
        container = container[1].split("-");
        String begin = container[0];
        String end = container[1];

        return new ActivityBlock(new Task(name), begin, end);
    }

    protected void setSelected(boolean selected) {
        this.selected = selected;
    }
    public boolean isSelected() {
        return selected;
    }

}
