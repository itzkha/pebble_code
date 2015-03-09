package ch.heig_vd.dailyactivities.model;

import java.sql.Timestamp;

/**
 * A simple wrapper class for the String class.
 * The only change is to compare the string in a case insensitive manner.
 */
public class Task {
    public enum Social {ALONE, WITH_OTHERS, NA}

    private String name;
    private String examples;
    private Social social = Social.NA;
    private static final Task DEFAULT_TASK = new Task("No activity");


    /**
     * Creates a new activity from a name.
     * @param name The activity name
     */
    public Task(String name) {
        this.name = name;
        this.examples = "";
        this.social = Social.NA;
    }

    /**
     * Creates a new activity from a name.
     * @param name The activity name
     */
    public Task(String name, String examples) {
        this.name = name;
        this.examples = examples;
        this.social = Social.NA;
    }

    /**
     * Returns the string name of the activity.
     * @return The string name of the activity
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the string name of the activity.
     * @return The string name of the activity
     */
    public String getExamples() {
        return examples;
    }

    public Social getSocial() {
        return social;
    }

    public void setSocial(Social social) {
        this.social = social;
    }

    /**
     * Compares two activity names in lowercase.
     * @param o an activity
     * @return true if the other activity name is the same (case insensitive) as the current one
     */
    @Override
    public boolean equals(Object o) {
        if(o == null) {
            return false;
        } else if(o.getClass() == Task.class) {
            return ((Task)o).name.concat(String.valueOf(((Task) o).getSocial())).toLowerCase().equals(name.concat(String.valueOf(social)).toLowerCase());
        }
        return super.equals(o);
    }

    public static Timestamp getMinStartingTimestamp() {
        return Utils.createTimestampFromHourMinSec("00:00:00");
    }

    public static Timestamp getMaxStoppingTimestamp() {
        return Utils.createTimestampFromHourMinSec("23:59:59");
    }

    public static String getMinStartingTimeString() {
        return "00:00";
    }

    public static String getMaxStoppingTimeString() {
        return "23:59";
    }

    public static Task getDefaultTask() {
        return DEFAULT_TASK;
    }

    public boolean isDefaultTask() {
        return this.equals(DEFAULT_TASK);
    }

    /**
     * Returns the name of the activity.
     * @return the name of the activity
     */
    @Override
    public String toString() {
        return name;
    }

}