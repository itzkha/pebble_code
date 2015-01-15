#include <pebble.h>
#include <inttypes.h>
static Window *window;

static const uint32_t ACCEL_LOG_TAG = 0xd5;

static const uint32_t ACCEL_SAMPLES_PER_UPDATE = 10;


typedef struct {
  // the accelerometer data
  // 16 bytes with x, y, z, did_vibrate, timestamp
  AccelData data;
} LoggingData;

DataLoggingSessionRef accel_logging_session;

static TextLayer *time_layer;
static TextLayer *date_layer;
static TextLayer *accel_layer;


static void accel_data_handler(AccelData *data, uint32_t num_samples) {
  APP_LOG(APP_LOG_LEVEL_DEBUG, "accel handler is called");
    
  LoggingData *logging_data = malloc(sizeof(LoggingData) * num_samples);
  
  for (uint32_t i = 0; i < num_samples; i++) {
    //APP_LOG(APP_LOG_LEVEL_DEBUG, "Time %"PRIu64", X %hd, Y %hd, Z %hd, isvibrate %d", data[i].timestamp, data[i].x, data[i].y, data[i].z, data[i].did_vibrate);
      memcpy(&logging_data[i].data, &data[i], sizeof(AccelData));
	APP_LOG(APP_LOG_LEVEL_DEBUG, "Time %"PRIu64", X %hd, Y %hd, Z %hd, isvibrate %d", logging_data[i].data.timestamp, logging_data[i].data.x, logging_data[i].data.y, logging_data[i].data.z, logging_data[i].data.did_vibrate);
      }
  
  DataLoggingResult r = data_logging_log(accel_logging_session, logging_data, num_samples);
  //free(logging_data);
}

// ---- ACCEL INIT AND DE-INIT ----
static void init_accel(void){
    // Initiate the logging session for accel data
    //accel_logging_session = data_logging_create(ACCEL_LOG_TAG, DATA_LOGGING_BYTE_ARRAY, sizeof(LoggingData), true);
    accel_data_service_subscribe(ACCEL_SAMPLES_PER_UPDATE, &accel_data_handler);
    accel_service_set_sampling_rate(ACCEL_SAMPLING_50HZ);
    
    APP_LOG(APP_LOG_LEVEL_DEBUG, "Init_accel is called");
}

static void deinit_accel(void) {
    
    accel_data_service_unsubscribe();
    data_logging_finish(accel_logging_session);
    
    APP_LOG(APP_LOG_LEVEL_DEBUG, "De-init_accel is called");
}

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {

}

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  init_accel();
  text_layer_set_text(accel_layer, "Accel is: ON");
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  deinit_accel();
  text_layer_set_text(accel_layer, "Accel is: OFF");
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
}

static void display_time(struct tm *tick_time) {
  uint8_t len = snprintf(NULL, 0, "%02i:%02i",tick_time->tm_hour, tick_time->tm_min);
  
  char * time_string = malloc(len+1);
  snprintf(time_string, len+1, "%02i:%02i",tick_time->tm_hour, tick_time->tm_min);

  text_layer_set_text(time_layer, time_string);
}


static void handle_minute_tick(struct tm *tick_time, TimeUnits units_changed) {
  display_time(tick_time);
}

static void display_date(struct tm *tick_time) {

  const char * months[] = { "Jan", "Feb", "Mar", "Apr",
			    "May", "Jun", "Jul", "Aug",
			    "Sep", "Oct", "Nov", "Dec"};
  uint8_t len = snprintf(NULL, 0, "Date: %s. %i",months[tick_time->tm_mon],tick_time->tm_mday);

  char * date_string = malloc(len+1);
  snprintf(date_string, len+1, "Date: %s. %i",months[tick_time->tm_mon],tick_time->tm_mday);
  
  text_layer_set_text(date_layer, date_string);
}

static void handle_day_tick(struct tm *tick_time, TimeUnits units_changed) {
  display_date(tick_time);
}

void handle_tick(struct tm *tick_time, TimeUnits units_changed) {
  if (units_changed & MINUTE_UNIT) {
    handle_minute_tick(tick_time, units_changed);
  }
  
  if (units_changed & DAY_UNIT) {
    handle_day_tick(tick_time, units_changed);
  }
}
static void on_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(window_layer);

  time_layer = text_layer_create(GRect(0,0,bounds.size.w,50));
  date_layer = text_layer_create(GRect(0,50, bounds.size.w,30));
  accel_layer = text_layer_create(GRect(0,80,bounds.size.w,30));
  time_t now = time(NULL);
  struct tm *tick_time = localtime(&now);

  display_time(tick_time);
  display_date(tick_time);

  // subscribe for time and date ticker
  tick_timer_service_subscribe(MINUTE_UNIT | DAY_UNIT, &handle_tick);
  

  // set parameters and draw text
  text_layer_set_font(time_layer, fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD));
  text_layer_set_font(date_layer, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD));  
  text_layer_set_font(accel_layer, fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD));
  
  text_layer_set_text_alignment(time_layer, GTextAlignmentCenter);
  text_layer_set_text_alignment(date_layer, GTextAlignmentCenter);
  text_layer_set_text_alignment(accel_layer, GTextAlignmentCenter);

  layer_add_child(window_layer, text_layer_get_layer(time_layer));
  layer_add_child(window_layer, text_layer_get_layer(date_layer));
  layer_add_child(window_layer, text_layer_get_layer(accel_layer));


  // subscribe for accel data logging
  text_layer_set_text(accel_layer, "Accel is: ON");
  init_accel();
}


static void on_window_unload(Window *window) {
  text_layer_destroy(time_layer);
  text_layer_destroy(date_layer);
  text_layer_destroy(accel_layer);

  // unsubscribe for accel data logging
  //deinit_accel();
  window_destroy(window);

}

int main(void) {
  window = window_create();
  window_set_click_config_provider(window, click_config_provider);
  window_set_window_handlers(window, (WindowHandlers) {
    .load = on_window_load,
    .unload = on_window_unload,
  });
    
  window_stack_push(window, true /* Animated */);
  app_event_loop();
}
