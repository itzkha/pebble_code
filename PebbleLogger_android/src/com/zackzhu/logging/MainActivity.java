package com.zackzhu.logging;


import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import org.apache.http.protocol.HTTP;

import android.app.Activity;
import android.app.AlarmManager;

import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class MainActivity extends Activity {
	ToggleButton mToggle; 
	
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		final Handler handler = new Handler();
		private final StringBuilder mDisplayText = new StringBuilder();
		private int mInstanceCounter;
		
		private void updateUi() {
	        TextView textView = (TextView) findViewById(R.id.log_data_text_view);
	        textView.setText(mDisplayText.toString());
	        
	    }
		  @Override
		  public void onReceive(Context context, Intent intent) {
			  
		    // Get extra data included in the Intent
			  if (mInstanceCounter < 15) {
				  mDisplayText.append(intent.getStringExtra("message")+"\n");
				  mInstanceCounter++;
			  } else {
				  mDisplayText.setLength(0);
				  mDisplayText.append(intent.getStringExtra("message")+"\n");
				  mInstanceCounter = 0;
				  mInstanceCounter++;
			  }
				  
		    Log.d("receiver", "Got message: " + mDisplayText);
		    
		    
		    handler.post(new Runnable() {
                @Override
                public void run() {
                		mToggle.setChecked(true);
                    updateUi();
                }
            });

		  }
		};
	
	@Override
	protected void onDestroy() {
		// Unregister since the activity is about to be closed.
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
		super.onDestroy();
	}
		
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_counter);
        mToggle = (ToggleButton) findViewById(R.id.logger_toggle);
        
        // Start service right away
        startService(new Intent(getBaseContext(), LoggerService.class));
        
        // Intent receiver for updating write status
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
        	      new IntentFilter("Write-Update"));
        
        
        // Set alarm to start service every 5 minutes
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 10);
        
        Intent intent = new Intent(this, LoggerService.class);

        PendingIntent pintent = PendingIntent.getService(this, 0, intent, 0);
       
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                     5*60*1000, pintent);
 

        
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.email:
	            	Intent sendIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
	            	
	            	// The intent does not have a URI, so declare the "text/plain" MIME type
	            	sendIntent.setType(HTTP.PLAIN_TEXT_TYPE);
	            	sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"zackzhu@gmail.com"}); // recipients
	            	
	            	Calendar calendar = Calendar.getInstance();
	            	sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Pebble Data: " +
	            			calendar.getDisplayName(Calendar.MONTH,Calendar.SHORT,Locale.US) + "-" + calendar.get(Calendar.DAY_OF_MONTH));
	            	
	            	ArrayList<Uri> uris = new ArrayList<Uri>();
	            	uris.add(0,Uri.parse("file://"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/PebbleAccel.csv"));
	            	uris.add(1,Uri.parse("file://"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/PebbleLabels.csv"));
	            	
	            	sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
	            	
	            	sendIntent.putExtra(Intent.EXTRA_TEXT, "Hi Zack, please see my Pebble accelerometer data attached.");
	            	startActivity(Intent.createChooser(sendIntent,"Send Labels:"));
	            	
	            	// Rename the file that was sent 
//	            	String original_name = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/PebbleAccel.csv";
//	            	String new_name = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/arch_" + 
//	            			calendar.getDisplayName(Calendar.MONTH,Calendar.SHORT,Locale.US) + calendar.get(Calendar.DAY_OF_MONTH) + "PebbleAccel.csv";
//	            	
//	            	File original_file = new File(original_name);
//	            	File new_file = new File(new_name);
//	            	
//	            	original_file.renameTo(new_file);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.option_menu, menu);
        return true;
    }
    
    public void onToggleClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();
        
        if (on) {
        		startService(new Intent(getBaseContext(), LoggerService.class));
        } else {
        		stopService(new Intent(getBaseContext(), LoggerService.class));
        }
    }

   
}
