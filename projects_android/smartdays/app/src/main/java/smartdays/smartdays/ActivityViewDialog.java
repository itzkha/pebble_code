package smartdays.smartdays;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

/**
 * Created by hector on 23/01/15.
 */
public class ActivityViewDialog extends DialogFragment {
    private ArrayList<String> time;
    private ArrayList<String> options;
    private ListView listViewTime;
    private ListView listViewOptions;

    public void setOptions(ArrayList<String> a) {
        options = a;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        time = new ArrayList<String>(288);
        for (int i = 0; i < 288; i++) {
            if ((i % 12) == 0) {
                time.add("-");
            }
            else {
                time.add("");
            }
        }

        final ArrayAdapter<String> adapterTime = new ArrayAdapter<String>(getActivity(), R.layout.activity_view_row, R.id.text1, time);
        final ArrayAdapter<String> adapterOptions = new ArrayAdapter<String>(getActivity(), R.layout.activity_view_row, R.id.text1, options);

        final View rootView = getActivity().getLayoutInflater().inflate(R.layout.activity_view, null);

        listViewTime = (ListView) rootView.findViewById(R.id.listViewTime);
        listViewTime.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listViewTime.setAdapter(adapterTime);

        listViewOptions = (ListView) rootView.findViewById(R.id.listViewOptions);
        listViewOptions.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listViewOptions.setAdapter(adapterOptions);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Current activity").setView(rootView);

        return builder.create();
    }



}