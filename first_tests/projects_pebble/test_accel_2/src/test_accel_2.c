//-------------------------------------------------------------------------------------------------
//  This application takes samples from tha accelerometer at 100Hz, computes the averaga every
//  WINDOW_SIZE (10) measures, and plots the x, y and z values to the screen. Measures are stored
//  in a circular buffer of size MAX_BUFFER_SIZE (50)
//-------------------------------------------------------------------------------------------------

#include <pebble.h>
#define WINDOW_SIZE 10
#define MAX_BUFFER_SIZE 50
#define X_OFFSET 20
#define WINDOW_WIDTH 100
#define ACC_MAX_VALUE 4000

static Window *s_main_window;
static Layer *s_canvas_layer;

uint32_t index = 0;
int buffer_x[MAX_BUFFER_SIZE] = {0};
int buffer_y[MAX_BUFFER_SIZE] = {0};
int buffer_z[MAX_BUFFER_SIZE] = {0};

static void canvas_update_proc(Layer *this_layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(this_layer);
  int offset_x = bounds.size.h / 6;
  int offset_y = bounds.size.h / 2;
  int offset_z = (5 * bounds.size.h) / 6;
  float scale_x = (float)WINDOW_WIDTH / MAX_BUFFER_SIZE;
  float scale_y = (float)(bounds.size.h / 6) / ACC_MAX_VALUE;

  // Draw the axes
  GPoint p0;
  GPoint p1;
  graphics_context_set_stroke_color(ctx, GColorBlack);
  p0 = GPoint(X_OFFSET / 2, offset_x);
  p1 = GPoint(bounds.size.w - (X_OFFSET / 2), offset_x);
  graphics_draw_line(ctx, p0, p1);
  p0 = GPoint(X_OFFSET / 2, offset_y);
  p1 = GPoint(bounds.size.w - (X_OFFSET / 2), offset_y);
  graphics_draw_line(ctx, p0, p1);
  p0 = GPoint(X_OFFSET / 2, offset_z);
  p1 = GPoint(bounds.size.w - (X_OFFSET / 2), offset_z);
  graphics_draw_line(ctx, p0, p1);
  p0 = GPoint(X_OFFSET, 0);
  p1 = GPoint(X_OFFSET, bounds.size.h);
  graphics_draw_line(ctx, p0, p1);
  

  // Plot the points
  uint32_t counter;
  uint32_t i = index;
  GPoint x0 = GPoint(X_OFFSET, offset_x);
  GPoint x1;
  GPoint y0 = GPoint(X_OFFSET, offset_y);
  GPoint y1;
  GPoint z0 = GPoint(X_OFFSET, offset_z);
  GPoint z1;
  for (counter = 0; counter < MAX_BUFFER_SIZE; counter++) {
    i++;
    if (i >= MAX_BUFFER_SIZE) {
      i = 0;
    }
    
    x1 = GPoint((scale_x * counter) + X_OFFSET, (scale_y * buffer_x[i]) + offset_x);
    graphics_draw_line(ctx, x0, x1);
    y1 = GPoint((scale_x * counter) + X_OFFSET, (scale_y * buffer_y[i]) + offset_y);
    graphics_draw_line(ctx, y0, y1);
    z1 = GPoint((scale_x * counter) + X_OFFSET, (scale_y * buffer_z[i]) + offset_z);
    graphics_draw_line(ctx, z0, z1);
    x0 = x1;
    y0 = y1;
    z0 = z1;
  }

}

static void data_handler(AccelRawData *data, uint32_t num_samples, uint64_t timestamp) {

  float x=0.0, y=0.0, z=0.0;
  uint32_t i;

  for (i = 0; i < num_samples; i++) {
    x += data[i].x;
    y += data[i].y;
    z += data[i].z;
  }
  x /= num_samples;
  y /= num_samples;
  z /= num_samples;

  index++;
  if (index >= MAX_BUFFER_SIZE) {
    index = 0;
  }
  buffer_x[index] = (int)x;
  buffer_y[index] = (int)y;
  buffer_z[index] = (int)z;

  layer_mark_dirty(s_canvas_layer);
}

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);

  // Create Layer
  s_canvas_layer = layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h));
  layer_add_child(window_layer, s_canvas_layer);

  // Set the update_proc
  layer_set_update_proc(s_canvas_layer, canvas_update_proc);
}

static void main_window_unload(Window *window) {
  // Destroy output TextLayer
  layer_destroy(s_canvas_layer);
}

static void init() {
  // Create main Window
  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);

  // Use data service
  // Subscribe to the accelerometer data service
  int num_samples = WINDOW_SIZE;
  accel_raw_data_service_subscribe(num_samples, data_handler);
  // Choose update rate
  accel_service_set_sampling_rate(ACCEL_SAMPLING_100HZ);

}

static void deinit() {
  // Destroy main Window
  window_destroy(s_main_window);
  accel_data_service_unsubscribe();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

