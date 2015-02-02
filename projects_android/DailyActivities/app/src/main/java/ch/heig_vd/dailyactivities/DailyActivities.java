package ch.heig_vd.dailyactivities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.Observable;
import java.util.Observer;

import ch.heig_vd.dailyactivities.model.Timeline;


public class DailyActivities extends ActionBarActivity implements ActionMode.Callback, Observer {

    private ActionMode mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_activities);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new DailyActivitiesFragment())
                    .commit();
        } else {
            if(savedInstanceState.getBoolean("isEditModeOn", false))
                DailyActivities.this.startSupportActionMode(DailyActivities.this);
        }
        Timeline.getInstance().subscribe(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Save isInActionMode value
        outState.putBoolean("isEditModeOn", isEditModeOn());

        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_daily_activities, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_add_activity) {
            Intent intent = new Intent(this, NewActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Called when the action mode is created; startActionMode() was called
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        this.mode = mode;
        // Inflate a menu resource providing context menu items
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.menu_summary_contextual, menu);

        updateTitle();
        return true;
    }

    // Called each time the action mode is shown. Always called after
    // onCreateActionMode, but
    // may be called multiple times if the mode is invalidated.
    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false; // Return false if nothing is done
    }

    // Called when the user selects a contextual menu item
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete_activity:
                Timeline.getInstance().deleteSelected();
                // Action picked, so close the CAB
                mode.finish();
                return true;
            default:
                return false;
        }
    }

    // Called when the user exits the action mode
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        this.mode = null;
        Timeline.getInstance().resetSelected();
    }

    public boolean isEditModeOn() {
        return mode != null;
    }

    public void startContextualMode() {
        startSupportActionMode(this);
    }

    private void updateTitle() {
        int i = Timeline.getInstance().getNumberSelected();
        if(i > 0) {
            this.mode.setTitle(i + " selected");
        } else {
            mode.finish();
        }
    }

    @Override
    public void update(Observable observable, Object data) {
        if(observable instanceof Timeline) {
            if(isEditModeOn()) {
                updateTitle();
            }
        }
    }
}
