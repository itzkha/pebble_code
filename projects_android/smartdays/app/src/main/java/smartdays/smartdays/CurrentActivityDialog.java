package smartdays.smartdays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ch.heig_vd.dailyactivities.model.ActivityBlock;
import ch.heig_vd.dailyactivities.model.Task;

/**
 * Created by hector on 08/01/15.
 */
public class CurrentActivityDialog extends DialogFragment {

    private ArrayList<Task> activities;
    private ListView listView;

    public void setActivities(ArrayList<Task> a) {
        activities = a;
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

//        final ArrayAdapter<Task> adapter = new ArrayAdapter<Task>(getActivity(), R.layout.activities_list_row, R.id.textActivityLabel, activities);
        final LabelAdapter adapter = new LabelAdapter(getActivity(), R.layout.activities_list_row, activities);

        final View rootView = getActivity().getLayoutInflater().inflate(R.layout.activities_list, null);
        listView = (ListView) rootView.findViewById(R.id.listViewCurrent);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Current activity")
               .setView(rootView)
               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                       if (listView.getCheckedItemCount() > 0) {
                           mListener.onDialogPositiveClick((String) listView.getItemAtPosition(listView.getCheckedItemPosition()).toString());
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

    class LabelAdapter extends ArrayAdapter<Task> {
        private Context context;
        private LayoutInflater inflater;
        ArrayList<Task> activities;

        public LabelAdapter(Context context, int resource) {
            super(context, resource);
            this.context = context;
            activities = new ArrayList();
        }

        public LabelAdapter(Context context, int resource, ArrayList<Task> items) {
            super(context, resource, items);
            this.context = context;
            activities = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup group) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                inflater = ((Activity) context).getLayoutInflater();
                convertView = inflater.inflate(R.layout.activities_list_row, group, false);
                holder.name = (TextView) convertView.findViewById(R.id.textActivityLabel);
                holder.examples = (TextView) convertView.findViewById(R.id.textActivityExamples);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            Task selected = activities.get(position);
            holder.name.setText(selected.getName());
            holder.examples.setText(selected.getExamples());

            return convertView;
        }

    }

    static class ViewHolder {
        public TextView name;
        public TextView examples;
    }
}
