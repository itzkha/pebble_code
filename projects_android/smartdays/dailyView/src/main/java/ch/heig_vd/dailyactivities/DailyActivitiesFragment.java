package ch.heig_vd.dailyactivities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import ch.heig_vd.dailyactivities.model.ActivityBlock;
import ch.heig_vd.dailyactivities.model.Task;
import ch.heig_vd.dailyactivities.model.Timeline;

public class DailyActivitiesFragment extends Fragment {

    private DailyActivities parent;
    private Button addActivity;
    private ActivityAdapter mActivitiesAdapter;

    public DailyActivitiesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parent= ((DailyActivities)getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_daily_activities, container, false);

        ArrayList<ActivityBlock> sample = Timeline.getInstance().getActivities();

        mActivitiesAdapter = new ActivityAdapter(getActivity(), R.layout.list_item_activity, sample);

        // Get a reference to the ListView, and attach this adapter to it.
        ListView listActivities;listActivities = (ListView) rootView.findViewById(R.id.list_activities);
        listActivities.setAdapter(mActivitiesAdapter);

        listActivities.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                if(parent.isEditModeOn()) {
                    Timeline.getInstance().setSelected(position);
                } else {
                    Intent intent = new Intent(getActivity(), NewActivity.class)
                            .putExtra("activity", position);
                    startActivity(intent);
                }
            }
        });

        listActivities.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                if(parent.isEditModeOn()) {
                    return false;
                } else {
                    Timeline.getInstance().setSelected(position);
                    parent.startContextualMode();
                    addActivity.setVisibility(View.GONE);
                    return true;
                }
            }
        });

        addActivity = (Button) rootView.findViewById(R.id.btn_add_new_activity);
        addActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!parent.isEditModeOn()) {
                    Intent intent = new Intent(getActivity(), NewActivity.class);
                    startActivity(intent);
                }
            }
        });

        return rootView;
    }

    @Override
    public void onResume() {
        mActivitiesAdapter.notifyDataSetChanged();
        super.onResume();
    }

    class ActivityAdapter extends ArrayAdapter<ActivityBlock> implements Observer {
        private Context context;
        private LayoutInflater inflater;
        ArrayList<ActivityBlock> activities;

        public ActivityAdapter(Context context, int resource) {
            super(context, resource);
            activities = new ArrayList();
        }

        public ActivityAdapter(Context context, int resource, ArrayList<ActivityBlock> items) {
            super(context, resource, items);
            this.context = context;
            this.activities = items;
            Timeline.getInstance().subscribe(this);
        }

        @Override
        public void update(Observable observable, Object data) {
            if(observable instanceof Timeline){
                if(!parent.isEditModeOn()) {
                    addActivity.setVisibility(View.VISIBLE);
                }
                activities = Timeline.getInstance().getActivities();
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
            return activities.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup group) {
            ViewHolder holder;
            if(convertView==null){
                holder=new ViewHolder();
                inflater = ((Activity) context).getLayoutInflater();
                convertView = inflater.inflate(R.layout.list_item_activity, group, false);
                holder.task = (TextView) convertView.findViewById(R.id.txt_task);
                holder.begin = (TextView) convertView.findViewById(R.id.txt_begin);
                holder.end = (TextView) convertView.findViewById(R.id.txt_end);
                holder.img = (ImageView) convertView.findViewById(R.id.edit_image);
                convertView.setTag(holder);
            }
            else{
                holder=(ViewHolder) convertView.getTag();
            }

            ActivityBlock currentBlock = activities.get(position);

            holder.task.setText(currentBlock.getTask().getName());
            holder.begin.setText(currentBlock.getBeginningString());
            holder.end.setText(currentBlock.getEndingString());

            if(!parent.isEditModeOn()) {
                holder.img.setVisibility(View.VISIBLE);
            } else {
                holder.img.setVisibility(View.GONE);
            }

            int height = currentBlock.getLengthInMinutes();
            if(height < convertView.getMinimumHeight())
                height = convertView.getMinimumHeight();
            if(currentBlock.getTask().equals(Task.getDefaultTask()))
                height = convertView.getMinimumHeight();
            convertView.setLayoutParams(new ViewGroup.LayoutParams(convertView.getLayoutParams().width, height));

            if(currentBlock.isSelected()) {
                convertView.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            } else {
                convertView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            }

            return convertView;
        }
    }

    static class ViewHolder {
        public TextView task, begin, end;
        public ImageView img;
    }
}
