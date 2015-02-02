package ch.heig_vd.dailyactivities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;


public class NewActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new NewActivityFragment())
                    .commit();
        }
    }
}
