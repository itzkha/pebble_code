package ch.heig_vd.dailyactivities;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;

import ch.heig_vd.dailyactivities.model.ActivityBlock;
import ch.heig_vd.dailyactivities.model.Task;
import ch.heig_vd.dailyactivities.model.Timeline;

public class NewActivityFragment extends Fragment {
    private ActivityBlock oldActivityBlock;
    private ActivityBlock activityBlock;
    private boolean setBegin = false;
    private boolean setEnd = false;

    private Spinner activityName;
    private TextView txtFrom;
    private TextView txtTo;
    private Button btnAdd;

    public NewActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Intent intent = getActivity().getIntent();
        View rootView = inflater.inflate(R.layout.fragment_new, container, false);

        activityName = (Spinner) rootView.findViewById(R.id.spin_activity_name);
        txtFrom = (TextView) rootView.findViewById(R.id.txt_from);
        txtTo = (TextView) rootView.findViewById(R.id.txt_to);
        btnAdd = (Button) rootView.findViewById(R.id.btn_add_activity);

        ArrayList<Task> activities = Timeline.getInstance().getAvailableActivities();
        if (activities == null ) {
            activities = new ArrayList<Task>(1);
            activities.add(Task.getDefaultTask());
        }

        txtFrom.setText(Task.getMinStartingTime());
        txtTo.setText(Task.getMaxStoppingTime());
        activityBlock = new ActivityBlock(new Task(""), txtFrom.getText().toString(), txtTo.getText().toString());

        // Create an ArrayAdapter using the task array and a default spinner layout
        ArrayAdapter<Task> adapter = new ArrayAdapter(getActivity(), R.layout.spinner_item_activity, activities);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        activityName.setAdapter(adapter);

        final TimePickerDialog.OnTimeSetListener timePickerListener =
                new TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(TimePicker view, int selectedHour,
                                          int selectedMinute) {
                        if(setBegin) {
                            setFromTime(selectedHour, selectedMinute);
                        } else if (setEnd) {
                            setToTime(selectedHour, selectedMinute);
                        } else {
                            Log.e("TimePicker", "Invalid State...");
                        }
                    }
                };

        txtFrom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBegin = true;
                TimePickerDialog tpd = new TimePickerDialog(getActivity(),
                        timePickerListener,
                        activityBlock.getStartHour(),
                        activityBlock.getStartMinute(),
                        true);
                tpd.show();
            }
        });

        txtTo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEnd = true;
                TimePickerDialog tpd = new TimePickerDialog(getActivity(),
                        timePickerListener,
                        activityBlock.getEndHour(),
                        activityBlock.getEndMinute(),
                        true);
                tpd.show();
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkActivityBlock()) {
                    activityBlock.setTask((Task) (activityName.getSelectedItem()));
                    activityBlock.setBegin(txtFrom.getText().toString());
                    activityBlock.setEnd(txtTo.getText().toString());
                    Timeline.getInstance().addActivity(activityBlock);
                    navigateBack();
                }
            }
        });

        if (intent.hasExtra("activity"))
            populateWindow(intent);

        return rootView;
    }

    private void populateWindow(Intent intent) {
        final int index = intent.getIntExtra("activity", 0);
        oldActivityBlock = Timeline.getInstance().getActivities().get(index);
        activityBlock = new ActivityBlock(oldActivityBlock.getTask(),
                oldActivityBlock.getBegin(), oldActivityBlock.getEnd());
        boolean found = false;
        int max = activityName.getAdapter().getCount();
        for(int i = 0; i < max; i++) {
            if(activityName.getItemAtPosition(i).equals(activityBlock.getTask())) {
                activityName.setSelection(i);
                found = true;
            }
        }
        if(!found) {
            Toast.makeText(getActivity(), "Activity not found in dropdown list.", Toast.LENGTH_LONG).show();
            getActivity().finish();
        }

        setFromTime(activityBlock.getStartHour(), activityBlock.getStartMinute());
        setToTime(activityBlock.getEndHour(), activityBlock.getEndMinute());

        getActivity().setTitle("Edit Activity");
        btnAdd.setText("Edit");
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activityBlock.setTask((Task) (activityName.getSelectedItem()));
                activityBlock.setBegin(txtFrom.getText().toString());
                activityBlock.setEnd(txtTo.getText().toString());
                if(!oldActivityBlock.equals(activityBlock)) {
                    if (checkActivityBlock()) {
                        Timeline.getInstance().removeActivity(index);
                        Timeline.getInstance().addActivity(activityBlock);
                        navigateBack();
                    }
                } else {
                    Log.d("EditActivity", "You didn't change any parameters... Nothing to do.");
                    navigateBack();
                }
            }
        });
    }

    private void setFromTime(int selectedHour, int selectedMinute) {
        activityBlock.setBegin(selectedHour, selectedMinute);
        txtFrom.setText(String.format("%02d", selectedHour) + ":"
                + String.format("%02d", selectedMinute));
        setBegin = false;
    }

    private void setToTime(int selectedHour, int selectedMinute) {
        activityBlock.setEnd(selectedHour, selectedMinute);
        txtTo.setText(String.format("%02d", selectedHour) + ":"
                + String.format("%02d", selectedMinute));
        setEnd = false;
    }

    private boolean checkActivityBlock() {
        boolean ok = false;
        try {
            ok = activityBlock.checkValid();
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message.equals(ActivityBlock.getLengthExceptionString())) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                int tempH = activityBlock.getStartHour();
                                int tempM = activityBlock.getStartMinute();
                                setFromTime(activityBlock.getEndHour(), activityBlock.getEndMinute());
                                setToTime(tempH, tempM);
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage("Maybe the \"From\" time and the \"To\" time are reversed. Would you like to swap them ?")
                        .setPositiveButton("Yes", dialogClickListener).setIcon(android.R.drawable.ic_dialog_alert)
                        .setNegativeButton("No", dialogClickListener).setCancelable(false)
                        .setTitle(message).show();
            } else if (message.equals(ActivityBlock.getNameExceptionString()) || message.equals(ActivityBlock.getZeroLengthExceptionString())) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            } else {
                throw e;
            }
        }
        return ok;
    }

    private void navigateBack() {
        //Intent intent = new Intent(getActivity(), DailyActivities.class);
        //startActivity(intent);
        getActivity().finish();
    }
}