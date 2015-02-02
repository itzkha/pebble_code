package ch.heig_vd.dailyactivities.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;

public class Timeline extends Observable {
    private static Timeline instance = null;

    private List<ActivityBlock> activities;

    private Timeline() {
        activities = new ArrayList<>();
        activities.add(new ActivityBlock(Task.getDefaultTask(),
                Task.getMinStartingTime(),
                Task.getMaxStoppingTime()));

        //TODO read file
    }

    public static synchronized Timeline getInstance() {
        if(instance == null) {
            instance = new Timeline();
        }
        return instance;
    }

    public synchronized void addActivity(ActivityBlock newActivity) {
        // Log.d("Timeline", "State=" + toString());
        // Log.d("Timeline", "Trying to add " + newActivity + "...");

        if(newActivity.getBegin().compareTo(Utils.createTimestampFromHourMin(Task.getMinStartingTime())) < 0) {
            throw new RuntimeException("Invalid starting time!");
        }
        if(newActivity.getEnd().compareTo(Utils.createTimestampFromHourMin(Task.getMaxStoppingTime())) > 0) {
            throw new RuntimeException("Invalid ending time!");
        }

        ListIterator<ActivityBlock> iterator = activities.listIterator();
        while(iterator.hasNext()) {
            ActivityBlock oldActivity = iterator.next();
            if(newActivity.getBegin().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) <= 0) {
                // Log.d("Timeline", "|          Is inside activity " + oldActivity);
                Timestamp old = oldActivity.getEnd();
                oldActivity.setEnd(newActivity.getBegin());
                iterator.add(newActivity);
                iterator.add(new ActivityBlock(oldActivity.getTask(), newActivity.getEnd(), old));
            } else if(newActivity.getBegin().compareTo(oldActivity.getBegin()) <= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) >= 0) {
                // Log.d("Timeline", "|          Contains activity " + oldActivity);
                oldActivity.setEnd(oldActivity.getBegin());
                iterator.add(newActivity);
            } else {
                if(newActivity.getBegin().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getBegin().compareTo(oldActivity.getEnd()) <= 0) {
                    // Log.d("Timeline", "|          Start is inside of activity " + oldActivity);
                    oldActivity.setEnd(newActivity.getBegin());
                    iterator.add(newActivity);
                }
                if(newActivity.getEnd().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) <= 0) {
                    // Log.d("Timeline", "|          End is inside of activity " + oldActivity);
                    oldActivity.setBegin(newActivity.getEnd());
                }
            }
        }

        ensureListStability();
    }

    public synchronized void removeActivity(int index) {
        // Log.d("Timeline", "State=" + toString());
        ActivityBlock activity = activities.get(index);
        if(!activity.getTask().isDefaultTask()) {
            // Log.d("Timeline", "Removing " + activity);
            activity.setTask(Task.getDefaultTask());
            ensureListStability();
        }
    }

    private synchronized void ensureListStability() {
        //Removes empty activities (0 length)
        // Log.d("Timeline", "Ensuring stability of " + toString() + "...");
        ListIterator<ActivityBlock> iterator = activities.listIterator();
        while(iterator.hasNext()) {
            ActivityBlock currentActivity = iterator.next();
            if (currentActivity.getEnd().compareTo(currentActivity.getBegin()) <= 0) {
                // Log.d("Timeline", "|          " + currentActivity + " is removed (0 length).");
                iterator.remove();
            }
        }

        //Merges adjacent same activities in a bigger block
        iterator = activities.listIterator();
        ActivityBlock previous = iterator.next();
        while(iterator.hasNext()) {
            ActivityBlock block = iterator.next();
            if(previous.getTask().equals(block.getTask())) {
                // Log.d("Timeline", "|          " + previous + " and " + block + " are merged.");
                previous.setEnd(block.getEnd());
                iterator.remove();
            } else {
                previous = block;
            }
        }
        // Log.d("Timeline", "Stability ensured for=" + toString());
    }

    public synchronized ArrayList<ActivityBlock> getActivities() {
        ArrayList<ActivityBlock> blocks = new ArrayList<>();
        for(ActivityBlock block : activities) {
            blocks.add(block);
        }
        return blocks;
    }

    public String toString() {
        StringBuilder stringRepresentation = new StringBuilder();
        stringRepresentation.append("[");
        String prefix = "";
        for(ActivityBlock block : activities) {
            stringRepresentation.append(prefix);
            prefix = ";";
            stringRepresentation.append(block.toString());
        }
        stringRepresentation.append("]");
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
        // Log.d("Timeline", "Removing selected blocks...");
        for (ActivityBlock block : activities) {
            if(block.isSelected() && !block.getTask().equals(Task.getDefaultTask())) {
                // Log.d("Timeline", "|          " + block + " removed.");
                block.setTask(Task.getDefaultTask());
                block.setSelected(false);
            }
        }
        ensureListStability();
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
}
