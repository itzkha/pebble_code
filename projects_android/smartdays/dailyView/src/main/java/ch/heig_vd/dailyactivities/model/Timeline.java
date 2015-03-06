package ch.heig_vd.dailyactivities.model;

import android.util.Log;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;

public class Timeline extends Observable {
    private static Timeline instance = null;

    private List<ActivityBlock> activities;
    private ArrayList<Task> availableActivities = null;

    private boolean needingWrite = false;

    private Timeline() {
        activities = new ArrayList();
        resetTimeline();

        availableActivities = new ArrayList<Task>(11);
        availableActivities.add(new Task("Commuting", "foot, bike, train, car"));
        availableActivities.add(new Task("Eat/Drink", "lunch, dinner, beer"));
        availableActivities.add(new Task("Education", "in lecture, talks, hw"));
        availableActivities.add(new Task("Household", "cook, clean, laundry"));
        availableActivities.add(new Task("Personal care", "sleep, shower, toilet"));
        availableActivities.add(new Task("Prof. services", "bank, doctor's, haircut"));
        availableActivities.add(new Task("Shopping", "grocery, store, mall"));
        availableActivities.add(new Task("Social/Leisure", "party, movies, museum"));
        availableActivities.add(new Task("Sports/Active", "gym, skiing, biking, hiking"));
        availableActivities.add(new Task("Working", "day-job, work-related"));
        availableActivities.add(new Task("No activity"));
    }

    public static synchronized Timeline getInstance() {
        if(instance == null) {
            instance = new Timeline();
        }
        return instance;
    }

    public synchronized void addActivity(ActivityBlock newActivity) {
        Log.d("Timeline", toString());
        Log.d("Timeline", "Trying to add " + newActivity);

        if(newActivity.getBegin().compareTo(Task.getMinStartingTimestamp()) < 0) {
            throw new RuntimeException("Invalid starting time!");
        }
        if (newActivity.getEnd().compareTo(Task.getMaxStoppingTimestamp()) > 0) {
            throw new RuntimeException("Invalid ending time!");
        }

        ListIterator<ActivityBlock> iterator = activities.listIterator();
        while (iterator.hasNext()) {
            ActivityBlock oldActivity = iterator.next();
            if (newActivity.getBegin().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) <= 0) {
                Log.d("Timeline", "Is inside activity " + oldActivity);
                Timestamp oldEnd = oldActivity.getEnd();
                oldActivity.setEnd(newActivity.getBegin());
                iterator.add(newActivity);

                ActivityBlock activityTail;
                if (oldActivity.isUndefinedEnd()) {
                    activityTail = new ActivityBlock(Task.getDefaultTask(), newActivity.getEnd(), oldEnd);
                    activityTail.setUndefinedEnd(true);
                }
                else {
                    activityTail = new ActivityBlock(oldActivity.getTask(), newActivity.getEnd(), oldEnd);
                    activityTail.setUndefinedEnd(false);
                }
                iterator.add(activityTail);

                oldActivity.setUndefinedEnd(false);
            }
            else if (newActivity.getBegin().compareTo(oldActivity.getBegin()) <= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) >= 0) {
                Log.d("Timeline", "Contains activity " + oldActivity);
                oldActivity.setEnd(oldActivity.getBegin());
            }
            else {
                if (newActivity.getBegin().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getBegin().compareTo(oldActivity.getEnd()) <= 0) {
                    Log.d("Timeline", "Start is inside of activity " + oldActivity);
                    oldActivity.setEnd(newActivity.getBegin());
                    oldActivity.setUndefinedEnd(false);
                    iterator.add(newActivity);
                }
                if (newActivity.getEnd().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) <= 0) {
                    Log.d("Timeline", "End is inside of activity " + oldActivity);
                    oldActivity.setBegin(newActivity.getEnd());
                }
            }
        }

        ensureListStability();
        Log.d("Timeline", toString());

        needingWrite = true;
    }

    public synchronized void removeActivity(int index) {
        ActivityBlock activity = activities.get(index);
        if(!activity.getTask().isDefaultTask()) {
            Log.d("Timeline", toString());
            Log.d("Timeline", "Removing " + activity);
            activity.setTask(Task.getDefaultTask());
            activity.setSelected(false);
            ensureListStability();
            Log.d("Timeline", toString());
            needingWrite = true;
        }
    }

    public synchronized void resetTimeline() {
        activities.clear();
        ActivityBlock defaultBlock = new ActivityBlock(new Task(Task.getDefaultTask().getName()), Task.getMinStartingTimestamp(), Task.getMaxStoppingTimestamp());
        defaultBlock.setUndefinedEnd(true);
        activities.add(defaultBlock);
    }

    private synchronized void ensureListStability() {
        //Removes empty activities (0 length)
        ListIterator<ActivityBlock> iterator = activities.listIterator();
        while(iterator.hasNext()) {
            ActivityBlock currentActivity = iterator.next();
            if (currentActivity.getEnd().compareTo(currentActivity.getBegin()) <= 0) {
                Log.d("Timeline", currentActivity + " is removed (0 length)");
                iterator.remove();
                needingWrite = true;
            }
        }

        //Merges adjacent same activities in a bigger block
        iterator = activities.listIterator();
        ActivityBlock previous = iterator.next();
        while(iterator.hasNext()) {
            ActivityBlock block = iterator.next();
            if(previous.getTask().equals(block.getTask())) {
                Log.d("Timeline", previous + " and " + block + " are merged.");
                previous.setEnd(block.getEnd());
                iterator.remove();
                needingWrite = true;
            } else {
                previous = block;
            }
        }
    }

    public synchronized ArrayList<ActivityBlock> getActivities() {
        ArrayList<ActivityBlock> blocks = new ArrayList();
        for(ActivityBlock block : activities) {
            blocks.add(block);
        }
        return blocks;
    }

    public String toString() {
        StringBuilder stringRepresentation = new StringBuilder();
        for(ActivityBlock block : activities) {
            stringRepresentation.append(block.toString() + ',');
        }
        stringRepresentation.append("END");
        return stringRepresentation.toString();
    }

    /******** This part is a observer-observable design for the views ********/

    public synchronized void subscribe(Observer o) {
        addObserver(o);
    }

    public synchronized void setSelected(int position) {
        ActivityBlock block = activities.get(position);
        block.setSelected(!block.isSelected());
        setChanged();
        notifyObservers();
    }

    public synchronized int getNumberSelected() {
        int count = 0;
        for (ActivityBlock block : activities) {
            if(block.isSelected()) {
                count += 1;
            }
        }
        return count;
    }

    public synchronized void deleteSelected() {
        for (int i = 0; i < activities.size(); i++) {
            ActivityBlock block = activities.get(i);
            if(block.isSelected()) {
                removeActivity(i);
            }
        }
        setChanged();
        notifyObservers();
    }

    public synchronized void resetSelected() {
        for (ActivityBlock block : activities) {
            if(block.isSelected()) {
                block.setSelected(false);
            }
        }
        setChanged();
        notifyObservers();
    }

    public synchronized void setAvailableActivities(ArrayList<Task> list) {
        availableActivities = list;
    }

    public ArrayList<Task> getAvailableActivities() {
        return availableActivities;
    }

    public boolean isNeedingWrite() {
        return needingWrite;
    }

    public void setNeedingWrite(boolean nw) {
        needingWrite = nw;
    }
}
