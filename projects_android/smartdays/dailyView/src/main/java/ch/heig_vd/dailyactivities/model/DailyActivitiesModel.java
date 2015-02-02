/*
package ch.heig_vd.dailyactivities.model;

import java.util.ArrayList;

public class DailyActivitiesModel {

    private static DailyActivitiesModel instance = null;

    private Task[] activities;
    int sizeOfSliceInMin = 5;

    private DailyActivitiesModel() {
        int numberOfSlicesPerDay = 24 * 60 / sizeOfSliceInMin;
        activities = new Task[numberOfSlicesPerDay];
    }

    public static synchronized DailyActivitiesModel getInstance() {
        if(instance == null) {
            instance = new DailyActivitiesModel();
        }
        return instance;
    }

    public ArrayList<ActivityBlock> getActivitiesAsBlocks() {
        ArrayList<ActivityBlock> blocks = new ArrayList<>();
        Task previousActivity = null;
        int previousActivityStart = 0;
        for(int i = 0; i < activities.length; i++) {
            Task currentActivity = activities[i];
            if(currentActivity != null) {
                if(previousActivity == null) {
                    previousActivity = currentActivity;
                    previousActivityStart = i;
                } else if(previousActivity.equals(currentActivity)) {
                    // nothing to do, we are still doing the same activity
                } else {
                    blocks.add(new ActivityBlock(previousActivity,
                            formatIndexToString(previousActivityStart),
                            formatIndexToString(i)));
                    previousActivityStart = i;
                    previousActivity = currentActivity;
                }
            } else {
                if(previousActivity != null) {
                    blocks.add(new ActivityBlock(previousActivity,
                            formatIndexToString(previousActivityStart),
                            formatIndexToString(i)));
                    previousActivity = null;
                }
            }
        }
        return blocks;
    }

    public void setActivities(String[] activities) {
        this.activities = activities;
    }

    public void addActivity(int start, int end, String activity) {
        for(int i = start; i < end; i++) {
            activities[i] = activity;
        }
    }

    private String formatIndexToString(int index) {
        int totalMin = sizeOfSliceInMin * index;
        return totalMin/60 + "h" + totalMin%60;
    }
}
*/