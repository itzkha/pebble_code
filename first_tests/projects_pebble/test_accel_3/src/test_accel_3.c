//-------------------------------------------------------------------------------------------------
//  This application takes samples from tha accelerometer at 100Hz, computes the averaga every
//  WINDOW_SIZE (10) measures, and sends packets of PACKET_SIZE (10) messages [timestamp, x, y, z]
//  The application restart the communication session every time the buffer is full (PACKET_SIZE)
//-------------------------------------------------------------------------------------------------
#include <pebble.h>
#define WINDOW_SIZE 10
#define DATA_LOG_TAG_ACCELEROMETER 51
#define PACKET_SIZE 10

static Window *s_main_window;
static TextLayer *s_output_layer;

static DataLoggingSessionRef s_log_ref;
static bool s_is_logging = false;
static int counter = 0;

typedef struct message {
  uint64_t timestamp;
  int16_t x;
  int16_t y;
  int16_t z;
} accel_message;        //C appends some (here 2) bytes to perform memory packing (8 bytes)

static void data_handler(AccelRawData *data, uint32_t num_samples, uint64_t timestamp) {

  float x=0.0, y=0.0, z=0.0;
  accel_message xyz;
  uint16_t i;

  for (i = 0; i < num_samples; i++) {
    x += data[i].x;
    y += data[i].y;
    z += data[i].z;
  }
  x /= num_samples;
  y /= num_samples;
  z /= num_samples;
  
  xyz.x = (int16_t)x;
  xyz.y = (int16_t)y;
  xyz.z = (int16_t)z;
  xyz.timestamp = timestamp;

  DataLoggingResult result = data_logging_log(s_log_ref, &xyz, 1);
  static char s_accel_buffer[32];
  if (result != DATA_LOGGING_SUCCESS) {
//    APP_LOG(APP_LOG_LEVEL_ERROR, "Error datalogging");
    snprintf(s_accel_buffer, sizeof(s_accel_buffer), "Error datalogging\n%d" + (int)result);
    text_layer_set_text(s_output_layer, s_accel_buffer);
  }
  else {
    // Show to user
    snprintf(s_accel_buffer, sizeof(s_accel_buffer), "Logging...\n%d %d %d", (int)xyz.x, (int)xyz.y, (int)xyz.z);
    text_layer_set_text(s_output_layer, s_accel_buffer);
    counter++;
    if (counter >= PACKET_SIZE) {
        counter = 0;
        s_log_ref = data_logging_create(DATA_LOG_TAG_ACCELEROMETER, DATA_LOGGING_BYTE_ARRAY, sizeof(uint64_t) + (3 * sizeof(int16_t)), false);
    }
  }
}

static void toggle_logging() {
  if (s_is_logging) {
    s_is_logging = false;

    // Stop compass logging callbacks
    accel_data_service_unsubscribe();
    text_layer_set_text(s_output_layer, "Not logging.");

    // Stop logging
    data_logging_finish(s_log_ref);
  }
  else {
    s_is_logging = true;

    // Start logging
    s_log_ref = data_logging_create(DATA_LOG_TAG_ACCELEROMETER, DATA_LOGGING_BYTE_ARRAY, sizeof(uint64_t) + (3 * sizeof(int16_t)), false);
    counter = 0;

    // Start generating data
    // Use data service
    // Subscribe to the accelerometer data service
    accel_raw_data_service_subscribe(WINDOW_SIZE, data_handler);
    // Choose update rate
    accel_service_set_sampling_rate(ACCEL_SAMPLING_100HZ);
    
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
  text_layer_set_text(s_output_layer, "Use SELECT to toggle data collection.");
  text_layer_set_text_alignment(s_output_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_output_layer));
}

static void main_window_unload(Window *window) {
  // Destroy output TextLayer
  text_layer_destroy(s_output_layer);
}

static void init() {
  // Create main Window
  s_main_window = window_create();
  window_set_click_config_provider(s_main_window, click_config_provider);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);
}

static void deinit() {
  // Finish logging session
  data_logging_finish(s_log_ref);

  // Destroy main Window
  window_destroy(s_main_window);
  accel_data_service_unsubscribe();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

