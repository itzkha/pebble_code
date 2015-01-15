#include <pebble.h>
#include <inttypes.h>

#include "entry.h"
static Window *window;

static ActionBarLayer *action_bar_layer;

static const uint32_t ACCEL_LOG_TAG = 0xd5;

static const uint32_t ACCEL_SAMPLES_PER_UPDATE = 25; //MAX 25

static uint32_t num_samples_counter = 0;
//static uint32_t fail_count = 0;

static uint16_t start_stop_flag = 0; // 1 for start, 2 for stop, 3 for cancelled start, 4 for calcelled stop, 0 for failed

static uint8_t in_marked_activity = 0;

static char date_string[13]; //"Date: MON. DA"
static char time_string[6]; //"HH:MM"
static char last_marker_string[27]; //"Last Marker: HH:MM(!start)"

static uint32_t last_accel_call_time;

static uint8_t current_activity_index; //To display currently marked activity
const char *act_in_progress[] = {
  "You're eating/drinking...",
  "You're working...",
  "You're studying...",
  "Taking care of yourself...",
  "You're playing sports...",
  "You're relaxing/socializing...",
  "Doing household activities...",
  "You're shopping...",
  "You're travelling...",
  "You're receiving a service...",
  "Doing religious activities...",
  "You're volunteering...",
  "Fulfilling gvt/civic obligations..."
};

static const int RESOURCE_IDS[2] = {
  RESOURCE_ID_IMAGE_ACTION_ICON_PLAY,
  RESOURCE_ID_IMAGE_ACTION_ICON_STOP
};

static const uint8_t ACCEL_BUFFER_SIZE = 100;
typedef struct {
  bool did_vibrate[100];
  uint64_t timestamp[100];
  int16_t x[100];
  int16_t y[100];
  int16_t z[100];
} AccelDataArray; //Container to hold 100 samples

static AccelDataArray accel_data_buffer;

typedef struct {
  int32_t avg_x;
  int32_t avg_y;
  int32_t avg_z;

  uint32_t var_x;
  uint32_t var_y;
  uint32_t var_z;

  time_t time_now;
} LoggingData;

DataLoggingSessionRef accel_logging_session;

static TextLayer *time_layer;
static TextLayer *date_layer;
static TextLayer *status_layer;
static TextLayer *info_layer;

/* Occasionally, accelerometer stops returning value. Need to restart Pebble entirely */
static void accel_stuck_handler() {
  // vibrate upon unloading
  vibes_long_pulse();  

  //set visual text
  text_layer_set_text(status_layer, "UH OH!");
  text_layer_set_text(info_layer, "Please restart your Pebble");

}
/* static void accel_data_handler(AccelData *data, uint32_t num_samples) { */
/*   DataLoggingResult r = data_logging_log(accel_logging_session, data, num_samples); */
/* } */
static void accel_data_handler(AccelData *data, uint32_t num_samples) {
  if (num_samples_counter < ACCEL_BUFFER_SIZE) {
    /* Load data into the buffer if the buffer has not been filled*/
    for (uint8_t i = 0; i < num_samples; i++) {
      accel_data_buffer.x[num_samples_counter + i] = data[i].x;
      accel_data_buffer.y[num_samples_counter + i] = data[i].y;
      accel_data_buffer.z[num_samples_counter + i] = data[i].z;

      accel_data_buffer.did_vibrate[num_samples_counter + i] = data[i].did_vibrate;
      accel_data_buffer.timestamp[num_samples_counter + i] = data[i].timestamp;
    }
    num_samples_counter = num_samples_counter + num_samples;
  } else {
    /* When buffer is filled, calculate the features and send it to data logging api*/
    
    LoggingData *logging_data = malloc(sizeof(LoggingData));

    time_t time_now = time(NULL);
  
    int32_t avg_x = 0;
    int32_t avg_y = 0;
    int32_t avg_z = 0;
    
    uint32_t avg_x_sq = 0;
    uint32_t avg_y_sq = 0;
    uint32_t avg_z_sq = 0;
    
    uint32_t var_x = 0;
    uint32_t var_y = 0;
    uint32_t var_z = 0;

    AccelDataArray* dx = &accel_data_buffer;
    uint8_t vibrate_counter = 0;
    for (uint8_t i = 0; i < ACCEL_BUFFER_SIZE; i++) {
      if (dx->did_vibrate[i] == true) {
	vibrate_counter++;
	continue;
      }
      avg_x = avg_x + dx->x[i];
      avg_y = avg_y + dx->y[i];
      avg_z = avg_z + dx->z[i];
    }
    avg_x = avg_x / (int) (ACCEL_BUFFER_SIZE - vibrate_counter);
    avg_y = avg_y / (int) (ACCEL_BUFFER_SIZE - vibrate_counter);
    avg_z = avg_z / (int) (ACCEL_BUFFER_SIZE - vibrate_counter);
    /* APP_LOG(APP_LOG_LEVEL_INFO, "avg_z: %ld", avg_z); */
    for (uint8_t i = 0; i < ACCEL_BUFFER_SIZE; i++) {
      avg_x_sq = avg_x_sq + (dx->x[i] - avg_x)*(dx->x[i] - avg_x);
      avg_y_sq = avg_y_sq + (dx->y[i] - avg_y)*(dx->y[i] - avg_y);
      avg_z_sq = avg_z_sq + (dx->z[i] - avg_z)*(dx->z[i] - avg_z);
      /* APP_LOG(APP_LOG_LEVEL_INFO, "%hd,%ld,%ld",dx->x[i],avg_x,((dx->x[i] - avg_x)*(dx->x[i] - avg_x))); */
    }

    var_x = (uint32_t) avg_x_sq / (int) (ACCEL_BUFFER_SIZE - vibrate_counter);
    var_y = (uint32_t) avg_y_sq / (int) (ACCEL_BUFFER_SIZE - vibrate_counter);
    var_z = (uint32_t) avg_z_sq / (int) (ACCEL_BUFFER_SIZE - vibrate_counter);

    logging_data->avg_x = avg_x;
    logging_data->avg_y = avg_y;
    logging_data->avg_z = avg_z;

    logging_data->var_x = var_x;
    logging_data->var_y = var_y;
    logging_data->var_z = var_z;
  
    logging_data->time_now = time_now;
  
    /* Debugging logs to verify data stuffing and processing */
    /* APP_LOG(APP_LOG_LEVEL_INFO, "number of samples: %ld, %ld", num_samples_counter, time_now); */
    /* for (uint8_t i = 0; i<ACCEL_BUFFER_SIZE; i++) { */
    /*   APP_LOG(APP_LOG_LEVEL_INFO,"timestamp: %hd,%hd,%hd,%lld", */
    /* 	      accel_data_buffer.x[i], */
    /* 	      accel_data_buffer.y[i], */
    /* 	      accel_data_buffer.z[i], */
    /* 	      accel_data_buffer.timestamp[i]); */
    /* } */
    /* APP_LOG(APP_LOG_LEVEL_INFO, "sending data: %ld, %ld, %ld, %ld, %ld, %ld, %ld", */
    /* 	    logging_data->avg_x,logging_data->avg_y,logging_data->avg_z, */
    /* 	    logging_data->var_x,logging_data->var_y,logging_data->var_z, */
    /* 	    logging_data->time_now); */
    DataLoggingResult r = data_logging_log(accel_logging_session, logging_data, 1);
    free(logging_data);
    num_samples_counter = 0;

    /* reset last accel call time */
    last_accel_call_time = time(NULL);
  }
    
 
    //APP_LOG(APP_LOG_LEVEL_DEBUG, "Time %"PRIu64", X %hd, Y %hd, Z %hd, isvibrate %d", data[num_samples-1].timestamp, data[num_samples-1].x, data[num_samples-1].y, data[num_samples-1].z, data[num_samples-1].did_vibrate);
  //DataLoggingResult r = data_logging_log(accel_logging_session, logging_data, num_samples);
  //APP_LOG(APP_LOG_LEVEL_INFO, "response from datalogginglog: %d", r);
  
}



// ---- ACCEL INIT AND DE-INIT ----

static void init_accel(void){
    // Initiate the logging session for accel data
  accel_logging_session = data_logging_create(ACCEL_LOG_TAG, DATA_LOGGING_BYTE_ARRAY, sizeof(LoggingData), true);

  accel_data_service_subscribe(ACCEL_SAMPLES_PER_UPDATE, &accel_data_handler);  
  accel_service_set_sampling_rate(ACCEL_SAMPLING_100HZ); 
  
  
  //  text_layer_set_text(status_layer, "Accel is: ON");
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Init_accel is called");
}

static void deinit_accel(void) {
    
    accel_data_service_unsubscribe();
    data_logging_finish(accel_logging_session);
    
    APP_LOG(APP_LOG_LEVEL_DEBUG, "De-init_accel is called");
    //text_layer_set_text(status_layer, "Accel is: OFF");
}

// ---- AppMessage Handlers ----
 void out_sent_handler(DictionaryIterator *sent, void *context) {
   // outgoing message was delivered
   
   if(start_stop_flag == 1) {
     // show text confirming the start marker was set
     /* text_layer_set_text(info_layer, "Logging start marker..."); */
     in_marked_activity = 1;
   } else if (start_stop_flag == 2) {
     /* text_layer_set_text(info_layer, "Logging stop marker..."); */
     in_marked_activity = 0;
   } else if (start_stop_flag == 3) {
     /* text_layer_set_text(info_layer, "Logging marker..."); */
   } else if (start_stop_flag == 4) {
     /* text_layer_set_text(info_layer, "Logging fuzzy stop marker..."); */
     in_marked_activity = 0;
   }

   // show text confirming the end marker was set
 }


 void out_failed_handler(DictionaryIterator *failed, AppMessageResult reason, void *context) {
   // outgoing message failed
   /* APP_LOG(APP_LOG_LEVEL_ERROR, "Out failed %d", reason); */
   /* start_stop_flag = 0; */
   text_layer_set_text(info_layer, "Oops, marking failed! Try again?");
 }


 void in_received_handler(DictionaryIterator *received, void *context) {
   // incoming message received
   Tuple *server_confirmation = dict_find(received, 5); //serverConfirmed = 5
   Tuple *phone_confirmation = dict_find(received, 7); //phoneConfirmed = 7

   if (server_confirmation) {   /* check for null pointer */
     if (server_confirmation->value->int32 == 1) {
       /* text_layer_set_text(info_layer, "Marker logged onto server!"); */
     
       /* Update last marker timestamp */
       time_t now;
       struct tm* ts;
       time(&now);
       ts = localtime(&now);
       strftime(last_marker_string, sizeof(last_marker_string), "Last: %H:%M", ts);
     
       char * display_string;
       if (start_stop_flag== 1) {
   	 snprintf(last_marker_string, sizeof(last_marker_string), "%s (start)",last_marker_string);
       } else if (start_stop_flag == 2) {
   	 snprintf(last_marker_string, sizeof(last_marker_string), "%s (stop)",last_marker_string);
       } else if (start_stop_flag == 3) {
   	 snprintf(last_marker_string, sizeof(last_marker_string), "%s (!start)",last_marker_string);
       } else if (start_stop_flag == 4) {
   	 snprintf(last_marker_string, sizeof(last_marker_string), "%s (!stop)",last_marker_string);
       }
       text_layer_set_text(status_layer, last_marker_string);
     } /* else { */
     /*   text_layer_set_text(info_layer, "Oops, server didn't confirm, try again?"); */
     /* } */
   }
   
   if (phone_confirmation) {
     APP_LOG(APP_LOG_LEVEL_INFO, "phone_confirmation %hd", phone_confirmation->value->int8);
     
     if (phone_confirmation->value->int8 == 1) {
       text_layer_set_text(info_layer, "Marker logged onto phone!");
     } else {
       text_layer_set_text(info_layer, "Oops, phone didn't confirm, try again?");
     }
   }
 }

 void in_dropped_handler(AppMessageResult reason, void *context) {
   // incoming message dropped
   text_layer_set_text(info_layer, "Oops, not sure if we got that one, try again?");
 }

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  //APP_LOG(APP_LOG_LEVEL_INFO, "selected row: %hd", activity_index);
  //init_accel();
  text_layer_set_text(info_layer, "");
}

static void select_long_click_handler(ClickRecognizerRef recognizer, void *context) {
  //deinit_accel();
  text_layer_set_text(info_layer, "");
}


static void send_end_marker(){
  DictionaryIterator *iter;
  Tuplet value = TupletInteger(0, time(NULL));

  app_message_outbox_begin(&iter);
  
  dict_write_tuplet(iter, &value);
  dict_write_end(iter);
  
  app_message_outbox_send();
  in_marked_activity = 0;
}

/* Note: only called by start marker, end marker sends off message directly */
static void send_activity(uint8_t activity_index, uint8_t callingMarker) {
  current_activity_index = activity_index;

/* Send a stop marker first if still in an activity */
  
  
  Tuplet actIndex = TupletInteger(10, activity_index);
  Tuplet value = TupletInteger(callingMarker, time(NULL));
  
  DictionaryIterator *iter;
  app_message_outbox_begin(&iter);
  
  if (iter == NULL) {
    return;
  }
  
  dict_write_tuplet(iter, &actIndex);
  dict_write_tuplet(iter, &value);

  /* Send an artificial stop marker for one second before if still in an activity */
  if (in_marked_activity == 1) {
    Tuplet artificialEndMarker = TupletInteger(0, time(NULL)-1);
    dict_write_tuplet(iter, &artificialEndMarker);
    
  }

  dict_write_end(iter);
  app_message_outbox_send();
  in_marked_activity = 1; // for tick second handler to use
  /* Initiate accelerometer logging */
  init_accel();
}

// Message keys: 1 -> startAct, 0 -> stopAct, 3 -> cancelStartAct, 2 -> cancelStopAct
static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  text_layer_set_text(info_layer, "");
  /* Request activity label from user */
  uint8_t markerType = 1;
  entry_get_name(markerType,send_activity); //1 for markerType = startAct
  

  start_stop_flag = 1;
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  text_layer_set_text(info_layer, "");
  /* De-initiate accelerometer logging */
  deinit_accel();
  in_marked_activity = 0;
  
  /* Send activity marker to phone */
  send_end_marker();

  start_stop_flag = 2;
}

static void up_long_click_handler(ClickRecognizerRef recognizer, void *context) {
  /* /\* Initiate accelerometer logging *\/ */
  /* init_accel(); */
  /* in_marked_activity = 1; */

  /*  DictionaryIterator *iter; */
  /*  Tuplet value = TupletInteger(3, time(NULL)); */

  /*  app_message_outbox_begin(&iter); */
   
  /*  dict_write_tuplet(iter, &value); */

  /*  app_message_outbox_send(); */
  /*  start_stop_flag = 3; */
}

static void down_long_click_handler(ClickRecognizerRef recognizer, void *context) {
  /* De-initiate accelerometer logging */
  deinit_accel();
  in_marked_activity = 0;

   DictionaryIterator *iter;
   Tuplet value = TupletInteger(2, time(NULL));

   app_message_outbox_begin(&iter);
   
   dict_write_tuplet(iter, &value);

   app_message_outbox_send();
   start_stop_flag = 4;
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
  window_long_click_subscribe(BUTTON_ID_SELECT, 0, select_long_click_handler, NULL);

  window_long_click_subscribe(BUTTON_ID_UP, 0, up_long_click_handler, NULL);
  window_long_click_subscribe(BUTTON_ID_DOWN, 0,down_long_click_handler, NULL);
}

static void display_time(struct tm *tick_time) {
  /* uint8_t len = snprintf(NULL, 0, "%02i:%02i",tick_time->tm_hour, tick_time->tm_min); */
  
  /* char * time_string = malloc(len+1); */
  /* snprintf(time_string, len+1, "%02i:%02i",tick_time->tm_hour, tick_time->tm_min); */
  strftime(time_string, sizeof(time_string), "%H:%M",tick_time);
  text_layer_set_text(time_layer, time_string);
}

static void handle_minute_tick(struct tm *tick_time, TimeUnits units_changed) {
  display_time(tick_time);

  // Display current activity
  if (tick_time->tm_min % 5 == 0) {
    if (in_marked_activity == 1){
      text_layer_set_text(info_layer, act_in_progress[current_activity_index]);
    } else {
      text_layer_set_text(info_layer, "Idling...accel is off");
    }
  }


  //Check if the last time accel handler was called is more than 10 minutes ago   
  if (in_marked_activity == 1 && (time(NULL)-last_accel_call_time) > 600) {
    accel_stuck_handler();
  }
  

  
  /* if (data_logging_flip == 0) { */
  /*   deinit_accel(); */
  /*   data_logging_flip = 1; */
  /* } else { */
  /*   init_accel(); */
  /*   data_logging_flip = 0; */
  /* } */
  
  /* char msg[30]; */
  /* snprintf(msg, sizeof(msg), "Acc count %lu:",acc_count); */
  /* text_layer_set_text(status_layer, msg); */
  
  //TODO: would be nice to remind the user when the last marker was set from the pebble
  //display_last_marker();

  /*
  static uint32_t last_count = 0;
  static char msg[30];
  static char fail_msg[30];
  
  if (acc_count>last_count) {
    //Accelerometer handler function increasing as intended
    snprintf(msg, sizeof(msg), "Acc count %lu:", acc_count);
    text_layer_set_text(status_layer, msg);
    last_count = acc_count;
  }
  else {
    if (last_count!=0) {
      deinit_accel();
      fail_count++;
      acc_count = 0; last_count = 0;
      init_accel();
    }
  }
  snprintf(fil_msg, sizeof(fail_msg), "Fails:%lu  last acc_count: %lu", fail_count, acc_count);
  text_layer_set_text(info_layer, fail_msg);
  */
}

static void display_date(struct tm *tick_time) {

  /* const char * months[] = { "Jan", "Feb", "Mar", "Apr", */
  /* 			    "May", "Jun", "Jul", "Aug", */
  /* 			    "Sep", "Oct", "Nov", "Dec"}; */
  /* uint8_t len = snprintf(NULL, 0, "Date: %s. %i",months[tick_time->tm_mon],tick_time->tm_mday); */

  /* char * date_string = malloc(len+1); */
  /* snprintf(date_string, len+1, "Date: %s. %i",months[tick_time->tm_mon],tick_time->tm_mday); */
  strftime(date_string, sizeof(date_string), "Date: %b %d", tick_time);
  text_layer_set_text(date_layer, date_string);
}

static void handle_day_tick(struct tm *tick_time) {
  display_date(tick_time);
}

static void handle_second_tick(struct tm *tick_time){

  /* Log 45 seconds for every 60 seconds when activity markers are set */
  /* if (in_marked_activity==1) { */
  /*   if (tick_time->tm_sec == 45) { */
  /*     deinit_accel(); */
  /*   } */
    
  /*   if (tick_time->tm_sec == 0) { */
  /*     init_accel(); */
  /*   } */
  /* } */
}
void handle_tick(struct tm *tick_time, TimeUnits units_changed) {
  if (units_changed & MINUTE_UNIT) {
    handle_minute_tick(tick_time, units_changed);
  }
  
  if (units_changed & DAY_UNIT) {
    handle_day_tick(tick_time);
  }

  if (units_changed & SECOND_UNIT) {
    handle_second_tick(tick_time);
  }
}

static void action_bar_init(Window *window) {
  GBitmap *bitmap_play = gbitmap_create_with_resource(RESOURCE_IDS[0]);
  GBitmap *bitmap_stop = gbitmap_create_with_resource(RESOURCE_IDS[1]);

  action_bar_layer = action_bar_layer_create();
  action_bar_layer_add_to_window(action_bar_layer, window);
  action_bar_layer_set_click_config_provider(action_bar_layer, click_config_provider);
  action_bar_layer_set_icon(action_bar_layer, BUTTON_ID_UP, bitmap_play);
  //action_bar_layer_set_icon(action_bar_layer, BUTTON_ID_SELECT, s_animal_datas[1].bitmap);
  action_bar_layer_set_icon(action_bar_layer, BUTTON_ID_DOWN, bitmap_stop);
}


static void on_window_load(Window *window) {
  
  Layer *window_layer = window_get_root_layer(window);
  //GRect bounds = layer_get_bounds(window_layer);

  time_layer = text_layer_create(GRect(0,0,120,50));
  date_layer = text_layer_create(GRect(0,50, 120,30));
  status_layer = text_layer_create(GRect(0,80,120,30));
  info_layer = text_layer_create(GRect(0,110,120,30));
  time_t now = time(NULL);
  struct tm *tick_time = localtime(&now);

  display_time(tick_time);
  display_date(tick_time);

  // subscribe for time and date ticker
  tick_timer_service_subscribe(SECOND_UNIT | MINUTE_UNIT | DAY_UNIT, &handle_tick);
  
  // draw action bar icons
  action_bar_init(window);

  // set parameters and draw text
  text_layer_set_font(time_layer, fonts_get_system_font(FONT_KEY_BITHAM_42_LIGHT));
  text_layer_set_font(date_layer, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD));  
  text_layer_set_font(status_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
  text_layer_set_font(info_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));

  text_layer_set_text_alignment(time_layer, GTextAlignmentCenter);
  text_layer_set_text_alignment(date_layer, GTextAlignmentCenter);
  text_layer_set_text_alignment(status_layer, GTextAlignmentCenter);
  text_layer_set_text_alignment(info_layer, GTextAlignmentCenter);

  layer_add_child(window_layer, text_layer_get_layer(time_layer));
  layer_add_child(window_layer, text_layer_get_layer(date_layer));
  layer_add_child(window_layer, text_layer_get_layer(status_layer));
  layer_add_child(window_layer, text_layer_get_layer(info_layer));

  // subscribe for accel reading
  //text_layer_set_text(status_layer, "Accel is: ON");
  //  init_accel();

  // initialization for appmessage
  app_message_register_inbox_received(in_received_handler);
  app_message_register_inbox_dropped(in_dropped_handler);
  app_message_register_outbox_sent(out_sent_handler);
  app_message_register_outbox_failed(out_failed_handler);

   const uint32_t inbound_size = 64;
   const uint32_t outbound_size = 64;
   app_message_open(inbound_size, outbound_size);

}

static void on_window_unload(Window *window) {
  /* Make sure to end an on-going activity if turning off the app */
  if (in_marked_activity == 1){
    send_end_marker();
  }

  text_layer_destroy(time_layer);
  text_layer_destroy(date_layer);
  text_layer_destroy(status_layer);
  text_layer_destroy(info_layer);
  // unsubscribe for accel data logging
  deinit_accel();
  
  window_destroy(window);

}

int main(void) {
  window = window_create();
  entry_init();
  window_set_click_config_provider(window, click_config_provider);
  window_set_window_handlers(window, (WindowHandlers) {
    .load = on_window_load,
    .unload = on_window_unload,
  });
    
  window_stack_push(window, true /* Animated */);
  app_event_loop();

}
