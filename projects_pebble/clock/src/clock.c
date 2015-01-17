#include <pebble.h>

static Window *s_main_window;
static TextLayer *s_watch_layer;
static TextLayer *s_uptime_layer;
static TextLayer *s_time_layer;
static TextLayer *s_ticktime_layer;

static int s_uptime = 0;

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  // Use a long-lived buffer
  static char s_watch_buffer[32];
  static char s_uptime_buffer[32];
  static char s_time_buffer[32];
  static char s_ticktime_buffer[32];

  int seconds;
  int minutes;
  int hours;
  time_t seconds_time;
  struct tm* local;
  
  
// watch time -------------------------------------------------------------------------------------
  clock_copy_time_string(s_watch_buffer, sizeof(s_watch_buffer));
  text_layer_set_text(s_watch_layer, s_watch_buffer);

// Uptime -----------------------------------------------------------------------------------------
  seconds = s_uptime % 60;
  minutes = (s_uptime % 3600) / 60;
  hours = s_uptime / 3600;
  snprintf(s_uptime_buffer, sizeof(s_uptime_buffer), "Up: %02d:%02d:%02d", hours, minutes, seconds);
  text_layer_set_text(s_uptime_layer, s_uptime_buffer);
  s_uptime++;

// time.h -----------------------------------------------------------------------------------------
  seconds_time = time(NULL);
  local = localtime(&seconds_time);
  strftime(s_time_buffer, sizeof(s_time_buffer), "Time: %H:%M:%S", local);
  text_layer_set_text(s_time_layer, s_time_buffer);
  
// tick time --------------------------------------------------------------------------------------
  seconds = tick_time->tm_sec;
  minutes = tick_time->tm_min;
  hours = tick_time->tm_hour;
  snprintf(s_ticktime_buffer, sizeof(s_ticktime_buffer), "Tick: %02d:%02d:%02d", hours, minutes, seconds);
  text_layer_set_text(s_ticktime_layer, s_ticktime_buffer);
  
}

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);

  // Create output TextLayer
  s_watch_layer = text_layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h/4));
  text_layer_set_text_alignment(s_watch_layer, GTextAlignmentCenter);
  text_layer_set_text(s_watch_layer, "");
  layer_add_child(window_layer, text_layer_get_layer(s_watch_layer));

  // Create output TextLayer
  s_uptime_layer = text_layer_create(GRect(0, window_bounds.size.h/4, window_bounds.size.w, window_bounds.size.h/4));
  text_layer_set_text_alignment(s_uptime_layer, GTextAlignmentCenter);
  text_layer_set_text(s_uptime_layer, "Uptime: 0h 0m 0s");
  layer_add_child(window_layer, text_layer_get_layer(s_uptime_layer));
  
  // Create output TextLayer
  s_time_layer = text_layer_create(GRect(0, window_bounds.size.h/2, window_bounds.size.w, window_bounds.size.h/4));
  text_layer_set_text_alignment(s_time_layer, GTextAlignmentCenter);
  text_layer_set_text(s_time_layer, "");
  layer_add_child(window_layer, text_layer_get_layer(s_time_layer));

  // Create output TextLayer
  s_ticktime_layer = text_layer_create(GRect(0, 3*window_bounds.size.h/4, window_bounds.size.w, window_bounds.size.h/4));
  text_layer_set_text_alignment(s_ticktime_layer, GTextAlignmentCenter);
  text_layer_set_text(s_ticktime_layer, "");
  layer_add_child(window_layer, text_layer_get_layer(s_ticktime_layer));

}

static void main_window_unload(Window *window) {
  // Destroy output TextLayer
  text_layer_destroy(s_uptime_layer);
  text_layer_destroy(s_watch_layer);
  text_layer_destroy(s_time_layer);
  text_layer_destroy(s_ticktime_layer);
}

static void init(void) {
  // Create main Window
  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload,
  });
  window_stack_push(s_main_window, true);

  // Subscribe to TickTimerService
  tick_timer_service_subscribe(SECOND_UNIT, tick_handler);
}

static void deinit(void) {
  // Destroy main Window
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

