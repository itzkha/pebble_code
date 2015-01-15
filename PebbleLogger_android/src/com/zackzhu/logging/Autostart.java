package com.zackzhu.logging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;



public class Autostart extends BroadcastReceiver {


	@Override
	public void onReceive(Context context, Intent intent) {
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {  

		    Intent pushIntent = new Intent(context, MainActivity.class); 
		    pushIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    context.startActivity(pushIntent);
		}
		
	}
}
