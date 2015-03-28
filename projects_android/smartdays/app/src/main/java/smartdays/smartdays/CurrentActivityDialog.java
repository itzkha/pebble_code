package smartdays.smartdays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

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
        public void onDialogPositiveClick(String activity, Task.Social social);
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

        final LabelAdapter adapter = new LabelAdapter(getActivity(), R.layout.activities_list_row, activities);

        final View rootView = getActivity().getLayoutInflater().inflate(R.layout.activities_list, null);
        listView = (ListView) rootView.findViewById(R.id.listViewCurrent);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                for (int j = 0; j < listView.getChildCount(); j++) {
                    listView.getChildAt(j).findViewById(R.id.spinnerSocial).setVisibility(View.INVISIBLE);
                }
                view.findViewById(R.id.spinnerSocial).setVisibility(View.VISIBLE);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Current activity")
               .setView(rootView)
               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                       if (listView.getCheckedItemCount() > 0) {
                           mListener.onDialogPositiveClick(activities.get(listView.getCheckedItemPosition()).getName(), activities.get(listView.getCheckedItemPosition()).getSocial());
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
                holder.social = (Spinner) convertView.findViewById(R.id.spinnerSocial);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            final Task current = activities.get(position);
            holder.name.setText(current.getName());
            holder.examples.setText(current.getExamples());

            ArrayList<String> socialArray = new ArrayList<String>(3);
            socialArray.add("Alone");
            socialArray.add("With others");
            socialArray.add("NA");
            ArrayAdapter<Task.Social> adapterSocial = new ArrayAdapter(getActivity(), R.layout.spinner_item_social_small, socialArray);
            adapterSocial.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            holder.social.setAdapter(adapterSocial);
            holder.social.setSelection(current.getSocial().ordinal());
            holder.social.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    current.setSocial(Task.Social.values()[i]);
                }
                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    current.setSocial(Task.Social.NA);
                }
            });

            // Display the alone spinner only on currently checked activity
            if (listView.getCheckedItemPosition() == position) {
                holder.social.setVisibility(View.VISIBLE);
            }
            else {
                holder.social.setVisibility(View.INVISIBLE);
            }

            return convertView;
        }

    }

    static class ViewHolder {
        public TextView name;
        public TextView examples;
        public Spinner social;
    }
}
