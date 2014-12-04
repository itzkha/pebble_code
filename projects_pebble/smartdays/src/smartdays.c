//-------------------------------------------------------------------------------------------------
//  This application takes samples from the accelerometer at 25Hz, and sends timestamped packets of
//  BUFFER_SIZE (25) measures [x, y, z]. The application restarts the communication session every
//  PACKETS_PER_SESSION (10) packets
//-------------------------------------------------------------------------------------------------

#include <pebble.h>
#define DATA_LOG_TAG_ACCELEROMETER 51
#define BUFFER_SIZE 25
#define BUFFER_SIZE_BYTES sizeof(uint64_t)+(3*BUFFER_SIZE*sizeof(int16_t))
#define PACKETS_PER_SESSION 10

#define START_STOP_KEY 7
#define START_MESSAGE 5
#define STOP_MESSAGE 12

static Window *s_main_window;
static TextLayer *s_output_layer;
static char s_points[10] = {'.', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
static char s_errors[15];
static char s_app_message[20];
static char s_message_buffer[64];

static DataLoggingSessionRef s_log_ref;
static DataLoggingResult result = DATA_LOGGING_SUCCESS;
static bool s_is_logging = false;
static int counter_packet = 0;
static int packets_sent = 0;

typedef struct packet {
  uint64_t timestamp;
  int16_t xyz[BUFFER_SIZE][3];
} accel_packet;                 //if BUFFER_SIZE_BYTES is not a multiple of 8, C appends some bytes to perform memory packing (8 bytes)

static accel_packet to_send;

static const uint32_t MESSAGE_DATA_KEY = 0xcafebabe;
DictionaryIterator iter;
DictionaryIterator* p_iter = &iter;



static void data_handler(AccelRawData *data, uint32_t num_samples, uint64_t timestamp) {
  uint16_t i;

  to_send.timestamp = timestamp;
  for (i = 0; i < num_samples; i++) {
    to_send.xyz[i][0] = (int16_t)data[i].x;                                                                         //save the measures
    to_send.xyz[i][1] = (int16_t)data[i].y;
    to_send.xyz[i][2] = (int16_t)data[i].z;
  }
  result = data_logging_log(s_log_ref, &to_send, 1);                                                                //send the data
  s_points[packets_sent % 10] = ' ';

  if (result != DATA_LOGGING_SUCCESS) {
    snprintf(s_errors, sizeof(s_errors), "Error log %d", (int)result);
  }
  else {
    snprintf(s_errors, sizeof(s_errors), "Log OK");
    packets_sent++;
    counter_packet++;
  }

  if (counter_packet >= PACKETS_PER_SESSION) {
    s_log_ref = data_logging_create(DATA_LOG_TAG_ACCELEROMETER, DATA_LOGGING_BYTE_ARRAY, BUFFER_SIZE_BYTES, false); //prepare the next session
    counter_packet = 0;
  }

  s_points[packets_sent % 10] = '.';
  snprintf(s_message_buffer, sizeof(s_message_buffer), "%s\nPackets sent %d\n%s\n\n%s", s_errors, packets_sent, s_points, s_app_message);
  text_layer_set_text(s_output_layer, s_message_buffer);
}

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  // Get the first pair
  Tuple *t = dict_read_first(iterator);

  // Process all pairs present
  while (t != NULL) {
    // Process this pair's key
    switch (t->key) {
      case START_STOP_KEY:
        // Copy value and display
        snprintf(s_app_message, sizeof(s_app_message), "Received '%s'", t->value->cstring);
        //text_layer_set_text(s_output_layer, s_buffer);
        break;
    }

    // Get next pair, if any
    t = dict_read_next(iterator);
  }
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped!");
  snprintf(s_app_message, sizeof(s_app_message), "%s", "Message dropped!");
}

void send_command(int command) {
  app_message_outbox_begin(&p_iter);
  dict_write_int8(p_iter, START_STOP_KEY, command);
  app_message_outbox_send();
}

void timer_callback(void *data) {
  // Send command to start logging
  text_layer_set_text(s_output_layer, "Resending request...");
  send_command(START_MESSAGE);
}

static void outbox_failed_callback(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Outbox send failed!");
  
  text_layer_set_text(s_output_layer, "Phone did not reply..." );
  app_timer_register(1000, timer_callback, NULL);
}

static void outbox_sent_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Outbox send success!");
  text_layer_set_text(s_output_layer, "Phone is OK" );
//  snprintf(s_app_message, sizeof(s_app_message), "%s", "Outbox success!");

  // Start logging if application sends ACK
  s_log_ref = data_logging_create(DATA_LOG_TAG_ACCELEROMETER, DATA_LOGGING_BYTE_ARRAY, BUFFER_SIZE_BYTES, true);

  // Subscribe to the accelerometer data service
  accel_raw_data_service_subscribe(BUFFER_SIZE, data_handler);
  // Choose update rate
  accel_service_set_sampling_rate(ACCEL_SAMPLING_25HZ);
    
  text_layer_set_text(s_output_layer, "Logging..." );
}

static void toggle_logging() {
  if (s_is_logging) {
    //-------------------------------------------
    app_message_outbox_begin(&p_iter);
    dict_write_int8(p_iter, START_STOP_KEY, STOP_MESSAGE);
    app_message_outbox_send();
    //-------------------------------------------

    s_is_logging = false;

    // Stop accelerometer logging callbacks
    accel_data_service_unsubscribe();
    text_layer_set_text(s_output_layer, "Not logging.");

    // Stop logging
    data_logging_finish(s_log_ref);
  }
  else {
    //-------------------------------------------
    app_message_outbox_begin(&p_iter);
    dict_write_int8(p_iter, START_STOP_KEY, START_MESSAGE);
    app_message_outbox_send();
    //-------------------------------------------

    s_is_logging = true;

    // Start logging
    s_log_ref = data_logging_create(DATA_LOG_TAG_ACCELEROMETER, DATA_LOGGING_BYTE_ARRAY, BUFFER_SIZE_BYTES, true);

    // Start generating data
    // Use data service
    // Subscribe to the accelerometer data service
    accel_raw_data_service_subscribe(BUFFER_SIZE, data_handler);
    // Choose update rate
    accel_service_set_sampling_rate(ACCEL_SAMPLING_25HZ);
    
    text_layer_set_text(s_output_layer, "Logging..." );
  }
}

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  // Toggle Data Logging ON/OFF
  toggle_logging();
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
}

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);
  
  // Create output TextLayer
  s_output_layer = text_layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h));
  text_layer_set_text(s_output_layer, "Waiting for phone...");
  text_layer_set_text_alignment(s_output_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_output_layer));
}

static void main_window_unload(Window *window) {
  // Destroy output TextLayer
  text_layer_destroy(s_output_layer);
}

static void init() {
  // Register callbacks
  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_register_outbox_sent(outbox_sent_callback);

  // Open AppMessage
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());
  snprintf(s_app_message, sizeof(s_app_message), "%s", "");
  
  // Create main Window
  s_main_window = window_create();
//  window_set_click_config_provider(s_main_window, click_config_provider);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);
  
  // Send command to start logging
  send_command(START_MESSAGE);

}

static void deinit() {
  // Finish logging session
  data_logging_finish(s_log_ref);
  accel_data_service_unsubscribe();
  
  // Try to stop the acquisition on the phone
  send_command(START_MESSAGE);
  
  // Deregister callbacks
  app_message_deregister_callbacks();

  // Destroy main Window
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

