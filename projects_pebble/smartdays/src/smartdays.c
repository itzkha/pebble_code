//-------------------------------------------------------------------------------------------------
//  This application takes samples from the accelerometer at 25Hz, and sends timestamped packets of
//  BUFFER_SIZE (25) measures [x, y, z]. The application (worker) sends packets ~every minute
//-------------------------------------------------------------------------------------------------

#include <pebble.h>

#define COMMAND_KEY 0xcafebabe
#define START_COMMAND 5
#define STOP_COMMAND 12
#define TIMESTAMP_COMMAND 17

#define TIMESTAMP_KEY 0xdeadbeef
#define N_SYNC 5

#define WORKER_DOTS 10

static Window *s_main_window;
static TextLayer *s_menu_layer;
static TextLayer *s_status_layer;
static TextLayer *s_dots_layer;
static char s_dots[11] = {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '\0'};
static char s_status[50];



//DictionaryIterator iter;
//DictionaryIterator* p_iter = &iter;
//static int current_command;

//static int8_t sync_counter = 0;




/*
static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message received!");
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped!");
}

void send_command(int command) {
  time_t seconds;
  uint16_t miliseconds;
  time_ms(&seconds, &miliseconds);
  uint64_t timestamp = (1000 * (uint64_t)seconds) + miliseconds;

  current_command = command;
  app_message_outbox_begin(&p_iter);
  dict_write_int8(p_iter, COMMAND_KEY, command);
  dict_write_data(p_iter, TIMESTAMP_KEY, (uint8_t*)&timestamp, sizeof(timestamp));
  app_message_outbox_send();
}

void delayed_start(void *data) {
  // Send command to start logging
  text_layer_set_text(s_status_layer, "Resending request...");
  send_command(START_COMMAND);
}

static void outbox_failed_callback(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Outbox send failed!");
  
  switch (current_command) {
    case START_COMMAND:
      text_layer_set_text(s_status_layer, "Phone did not reply..." );
      app_timer_register(1000, delayed_start, NULL);
      break;
    case STOP_COMMAND:
      text_layer_set_text(s_status_layer, "I'll be back...");
      window_stack_pop(true);
      break;
    case TIMESTAMP_COMMAND:
      break;
  }
}

static void outbox_sent_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Outbox send success!");
  
  switch (current_command) {
    case START_COMMAND:
      // Send pebble timestamp
      send_command(TIMESTAMP_COMMAND);
      sync_counter = 0;
      text_layer_set_text(s_status_layer, "Phone is OK" );
      // start logging after 1 second
      app_timer_register(1000, delayed_logging, NULL);
      break;
    case STOP_COMMAND:
      text_layer_set_text(s_status_layer, "I'll be back...");
      window_stack_pop(true);
      break;
    case TIMESTAMP_COMMAND:
      if (sync_counter < N_SYNC) {
        sync_counter++;
        send_command(TIMESTAMP_COMMAND);
      }
      break;
  }
}

*/
static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  // Check to see if the worker is currently active
  bool running = app_worker_is_running();

  // Toggle running state
  AppWorkerResult result;
  if (running) {
    result = app_worker_kill();

    if (result == APP_WORKER_RESULT_SUCCESS) {
      APP_LOG(APP_LOG_LEVEL_INFO, "Worker stopped!");
      text_layer_set_text(s_status_layer, "Worker stopped!");
    } else {
      APP_LOG(APP_LOG_LEVEL_INFO, "Error killing worker!");
      text_layer_set_text(s_status_layer, "Error killing worker!");
    }
  }
  else {
    result = app_worker_launch();

    if (result == APP_WORKER_RESULT_SUCCESS) {
      APP_LOG(APP_LOG_LEVEL_INFO, "Worker launched!");
      text_layer_set_text(s_status_layer, "Worker launched!");
    } else {
      APP_LOG(APP_LOG_LEVEL_INFO, "Error launching worker!");
      text_layer_set_text(s_status_layer, "Error launching worker!");
    }
  }
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
}

static void worker_message_handler(uint16_t type, AppWorkerMessage *data) {
  if (type == WORKER_DOTS) { 
    for (uint8_t i = 0; i < 10; i++) {
      s_dots[i] = ' ';
    }
    s_dots[data->data0] = '.';
    text_layer_set_text(s_dots_layer, s_dots);
    
    APP_LOG(APP_LOG_LEVEL_INFO, "Packet pushed...%d", data->data0);
    
    switch (data->data1) {
      case DATA_LOGGING_SUCCESS:
        APP_LOG(APP_LOG_LEVEL_INFO, "Log OK");
        text_layer_set_text(s_status_layer, "Log OK");
        break;
      case DATA_LOGGING_BUSY:
        APP_LOG(APP_LOG_LEVEL_INFO, "Someone else is writing to this log!");
        text_layer_set_text(s_status_layer, "Someone else is writing to this log!");
        break;
      case DATA_LOGGING_FULL:
        APP_LOG(APP_LOG_LEVEL_INFO, "No more space to save data!");
        text_layer_set_text(s_status_layer, "No more space to save data!");
        break;
      case DATA_LOGGING_NOT_FOUND:
        APP_LOG(APP_LOG_LEVEL_INFO, "The log does not exist!");
        text_layer_set_text(s_status_layer, "The log does not exist!");
        break;
      case DATA_LOGGING_CLOSED:
        APP_LOG(APP_LOG_LEVEL_INFO, "The log was made inactive!");
        text_layer_set_text(s_status_layer, "The log was made inactive!");
        break;
      case DATA_LOGGING_INVALID_PARAMS:
        APP_LOG(APP_LOG_LEVEL_INFO, "Invalid parameters!");
        text_layer_set_text(s_status_layer, "Invalid parameters!");
        break;
    }
  }
}

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);
  
  // Create TextLayers
  s_menu_layer = text_layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h/2));
  text_layer_set_text(s_menu_layer, "SmartDAYS");
  text_layer_set_text_alignment(s_menu_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_menu_layer));

  s_status_layer = text_layer_create(GRect(0, window_bounds.size.h/2, window_bounds.size.w, window_bounds.size.h/4));
  text_layer_set_text(s_status_layer, "Initializing...");
  text_layer_set_text_alignment(s_status_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_status_layer));

  s_dots_layer = text_layer_create(GRect(0, 3*window_bounds.size.h/4, window_bounds.size.w, window_bounds.size.h/4));
  text_layer_set_text(s_dots_layer, "");
  text_layer_set_text_alignment(s_dots_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_dots_layer));
}

static void main_window_unload(Window *window) {
  // Destroy TextLayers
  text_layer_destroy(s_menu_layer);
  text_layer_destroy(s_status_layer);
  text_layer_destroy(s_dots_layer);
}

static void init() {
  // Register callbacks
//  app_message_register_inbox_received(inbox_received_callback);
//  app_message_register_inbox_dropped(inbox_dropped_callback);
//  app_message_register_outbox_failed(outbox_failed_callback);
//  app_message_register_outbox_sent(outbox_sent_callback);

  // Open AppMessage
//  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());
  
  // Create main Window
  s_main_window = window_create();
  window_set_click_config_provider(s_main_window, click_config_provider);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);
  
  // Subscribe to Worker messages
  app_worker_message_subscribe(worker_message_handler);
  
  // Send command to start logging
//  send_command(START_COMMAND);

}

static void deinit() {
  // Deregister callbacks
//  app_message_deregister_callbacks();
  APP_LOG(APP_LOG_LEVEL_INFO, "Quitting application!");
  // No more worker updates
  app_worker_message_unsubscribe();
  
  // Destroy main Window
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

