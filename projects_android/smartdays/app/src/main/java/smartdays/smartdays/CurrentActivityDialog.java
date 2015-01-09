package smartdays.smartdays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by hector on 08/01/15.
 */
public class CurrentActivityDialog extends DialogFragment {

    private ArrayList<String> activities;
    private ListView listView;
    private int selectedActivity;
    private ActionMode mActionMode;

    public void setActivities(ArrayList<String> a) {
        activities = a;
    }

    public ListView getListView() {
        return listView;
    }

    public interface NoticeDialogListener {
        public void onDialogPositiveClick(String activity);
    }
    NoticeDialogListener mListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (NoticeDialogListener) activity;
        }
        catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement NoticeDialogListener");
        }
    }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.activities_list_row, R.id.text1, activities);

        final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
            // Called when the action mode is created; startActionMode() was called
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate a menu resource providing context menu items
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.context_menu, menu);
                mode.setTitle(adapter.getItem(selectedActivity));
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false; // Return false if nothing is done
            }

            // Called when the user selects a contextual menu item
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_delete:
                        if (adapter.getItem(selectedActivity) != "No activity") {
                            //Log.d("SmartDAYS", "Delete!!!");
                            adapter.remove(adapter.getItem(selectedActivity));
                        }
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            // Called when the user exits the action mode
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }
        };

        final View rootView = getActivity().getLayoutInflater().inflate(R.layout.activities_list, null);
        listView = (ListView) rootView.findViewById(R.id.listViewCurrent);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        //listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
                if (mActionMode != null) {
                    return false;
                }
                selectedActivity = pos;
                mActionMode = rootView.startActionMode(mActionModeCallback);
                v.setSelected(true);
                return true;
            }
        });

        final EditText newActivity = (EditText) rootView.findViewById(R.id.editTextNewLabel);
        Button buttonAddActivity = (Button) rootView.findViewById(R.id.buttonNewLabel);
        buttonAddActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                String temp = newActivity.getText().toString().trim();
                //Log.d("SmartDAYS", "You entered: " + temp);
                if (temp.length() > 0) {
                    adapter.add(temp);
                    Collections.sort(activities, new Comparator<String>() {
                        @Override
                        public int compare(String s1, String s2) {
                            return s1.compareToIgnoreCase(s2);
                        }
                    });
                    newActivity.getText().clear();
                    adapter.notifyDataSetChanged();
                    listView.smoothScrollToPosition(activities.indexOf(temp));
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Current activity")
               .setView(rootView)
               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                       if (listView.getCheckedItemCount() > 0) {
                           mListener.onDialogPositiveClick((String) listView.getItemAtPosition(listView.getCheckedItemPosition()));
                       }
                   }
               })
               .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                   }
               });

        return builder.create();
    }
}
