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

    private Timeline() {
        activities = new ArrayList();
        activities.add(new ActivityBlock(Task.getDefaultTask(),
                Task.getMinStartingTime(),
                Task.getMaxStoppingTime()));

        availableActivities = new ArrayList<Task>(20);
        availableActivities.add(new Task("Breakfast"));
        availableActivities.add(new Task("Car"));
        availableActivities.add(new Task("Clean"));
        availableActivities.add(new Task("Coffee"));
        availableActivities.add(new Task("Cook"));
        availableActivities.add(new Task("Dinner"));
        availableActivities.add(new Task("Hygiene"));
        availableActivities.add(new Task("Lunch"));
        availableActivities.add(new Task("No activity"));
        availableActivities.add(new Task("Rest"));
        availableActivities.add(new Task("Sports"));
        availableActivities.add(new Task("Walk"));
        availableActivities.add(new Task("Work"));
        //TODO read file
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

        if(newActivity.getBegin().compareTo(Utils.createTimestampFromHourMin(Task.getMinStartingTime())) < 0) {
            throw new RuntimeException("Invalid starting time!");
        }
        if (newActivity.getEnd() != null) {
            if (newActivity.getEnd().compareTo(Utils.createTimestampFromHourMin(Task.getMaxStoppingTime())) > 0) {
                throw new RuntimeException("Invalid ending time!");
            }
        }
        else {
            for (ActivityBlock block : activities) {
                if ( (newActivity.getBegin().compareTo(block.getBegin()) >= 0)  && (newActivity.getBegin().compareTo(block.getEnd()) <= 0) ) {
                    newActivity.setEnd(new Timestamp(block.getEnd().getTime()));
                    break;
                }
            }
        }

        ListIterator<ActivityBlock> iterator = activities.listIterator();
        while(iterator.hasNext()) {
            ActivityBlock oldActivity = iterator.next();
            if(newActivity.getBegin().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) <= 0) {
                Log.d("Timeline", "Is inside activity " + oldActivity);
                Timestamp old = oldActivity.getEnd();
                oldActivity.setEnd(newActivity.getBegin());
                iterator.add(newActivity);
                iterator.add(new ActivityBlock(oldActivity.getTask(), newActivity.getEnd(), old));
            } else if(newActivity.getBegin().compareTo(oldActivity.getBegin()) <= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) >= 0) {
                Log.d("Timeline", "Contains activity " + oldActivity);
                oldActivity.setEnd(oldActivity.getBegin());
            } else {
                if(newActivity.getBegin().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getBegin().compareTo(oldActivity.getEnd()) <= 0) {
                    Log.d("Timeline", "Start is inside of activity " + oldActivity);
                    oldActivity.setEnd(newActivity.getBegin());
                    iterator.add(newActivity);
                }
                if(newActivity.getEnd().compareTo(oldActivity.getBegin()) >= 0 && newActivity.getEnd().compareTo(oldActivity.getEnd()) <= 0) {
                    Log.d("Timeline", "End is inside of activity " + oldActivity);
                    oldActivity.setBegin(newActivity.getEnd());
                }
            }
        }

        ensureListStability();
        Log.d("Timeline", toString());
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
        }
    }

    public synchronized void removeAllActivities() {
        int i = 0;
        while (i < activities.size()) {
            if (activities.get(i).getTask().isDefaultTask()) {
                i++;
            }
            else {
                this.removeActivity(i);
                i = 0;
            }
        }
    }

    private synchronized void ensureListStability() {
        //Removes empty activities (0 length)
        ListIterator<ActivityBlock> iterator = activities.listIterator();
        while(iterator.hasNext()) {
            ActivityBlock currentActivity = iterator.next();
            if (currentActivity.getEnd().compareTo(currentActivity.getBegin()) <= 0) {
                Log.d("Timeline", currentActivity + " is removed (0 length)");
                iterator.remove();
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
}
