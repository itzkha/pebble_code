//-------------------------------------------------------------------------------------------------
//  This application takes samples from the accelerometer at 25Hz, and sends timestamped packets of
//  BUFFER_SIZE (25) measures [x, y, z]. The application (worker) sends packets ~every minute
//-------------------------------------------------------------------------------------------------

#include <pebble.h>

#define TIMESTAMP_KEY 0xdeadbeef
#define LABEL_KEY 0xf00d50da
#define MENU_ITEM_KEY 0xc0cac01a

#define COMMAND_KEY 0xcafebabe
#define START_COMMAND 5
#define STOP_COMMAND 12
#define TIMESTAMP_COMMAND 17
#define SYNC_MENU_ITEM_COMMAND 7
#define LABEL_COMMAND 21

#define MAX_MENU_ITEMS 10
#define MAX_MENU_ITEM_LENGTH 15

#define N_PROGRESS_SYMBOLS 4
#define PROGRESS_KEY 10

#define FROM_WATCH_APP_KEY 98


static Window *s_main_window;
static TextLayer *s_title_layer;
static TextLayer *s_progress_layer;
static TextLayer *s_status_layer;
static char* s_progress[4] = {"|", "/", "--", "\\"};

static char* menu_items[MAX_MENU_ITEMS];
static uint8_t menu_items_counter = 0;
static MenuLayer *s_menu_layer;

static const uint32_t const segments[] = { 50, 200, 50 };
VibePattern pat = {
  .durations = segments,
  .num_segments = ARRAY_LENGTH(segments),
};


DictionaryIterator iter;
DictionaryIterator* p_iter = &iter;
static int current_command;


void send_command(int command);
void send_label(char* label);


//------------------------------------------------------------------------------------------------- Communication with the background worker
static void worker_message_handler(uint16_t type, AppWorkerMessage *data) {
  if (type == PROGRESS_KEY) { 
    text_layer_set_text(s_progress_layer, s_progress[data->data0]);
    
    APP_LOG(APP_LOG_LEVEL_INFO, "Packet pushed...%d", data->data0);
    
    switch (data->data1) {
      case DATA_LOGGING_SUCCESS:
        APP_LOG(APP_LOG_LEVEL_INFO, "Log OK");
        //text_layer_set_text(s_status_layer, "Log OK");
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

static void start_worker() {
  AppWorkerResult result;

  // Check whether the worker is currently active
  if (!app_worker_is_running()) {
    persist_write_bool(FROM_WATCH_APP_KEY, true);                                                  // Tell the worker that it was started from the watch app
    result = app_worker_launch();

    if (result == APP_WORKER_RESULT_SUCCESS) {
      APP_LOG(APP_LOG_LEVEL_INFO, "Worker launched!");
      text_layer_set_text(s_status_layer, "Worker launched!");
    } else {
      APP_LOG(APP_LOG_LEVEL_INFO, "Error launching worker!");
      text_layer_set_text(s_status_layer, "Error launching worker!");
    }
  }
  else {
    APP_LOG(APP_LOG_LEVEL_INFO, "The worker is already running!");
    text_layer_set_text(s_status_layer, "The worker is already running!");
  }
}

static void stop_worker() {
  AppWorkerResult result;

  // Check whether the worker is currently active
  if (app_worker_is_running()) {
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
    APP_LOG(APP_LOG_LEVEL_INFO, "The worker is not running!");
    text_layer_set_text(s_status_layer, "The worker is not running!");
  }
}

//------------------------------------------------------------------------------------------------- Menu functions
static void append_to_menu(char* item) {
  strncpy(menu_items[menu_items_counter % MAX_MENU_ITEMS], item, MAX_MENU_ITEM_LENGTH);
  menu_items_counter++;
  menu_layer_reload_data(s_menu_layer);
}

// A callback is used to specify the amount of sections of menu items
// With this, you can dynamically add and remove sections
static uint16_t menu_get_num_sections_callback(MenuLayer *menu_layer, void *data) {
  return 1;
}

// Each section has a number of items;  we use a callback to specify this
// You can also dynamically add and remove items using this
static uint16_t menu_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  return menu_items_counter < MAX_MENU_ITEMS ? menu_items_counter : MAX_MENU_ITEMS;
}

// A callback is used to specify the height of the section header
static int16_t menu_get_cell_height_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *callback_context) {
  // This is a define provided in pebble.h that you may use for the default height
  return 30;
}

// Here we draw what each header is
static void menu_draw_header_callback(GContext* ctx, const Layer *cell_layer, uint16_t section_index, void *data) {
  menu_cell_basic_header_draw(ctx, cell_layer, "Activities");
}

// This is the menu item draw callback where you specify what each item should look like
static void menu_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item we'll draw
  menu_cell_title_draw(ctx, cell_layer, menu_items[cell_index->row]);
}

// Here we capture when a user selects a menu item
void menu_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item will receive the select action
  text_layer_set_text(s_status_layer, "Thank you!" );
  send_label(menu_items[cell_index->row]);
}

//------------------------------------------------------------------------------------------------- Buttons configuration



//------------------------------------------------------------------------------------------------- Communication with the android application

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

void send_label(char* label) {
  current_command = LABEL_COMMAND;
  app_message_outbox_begin(&p_iter);
  dict_write_int8(p_iter, COMMAND_KEY, current_command);
  dict_write_cstring(p_iter, LABEL_KEY, label);
  app_message_outbox_send();
}

void delayed_stop(void *data) {
  window_stack_pop(true);
}

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message received!");
  // Get the first pair
  Tuple *t = dict_read_first(iterator);
  int command;
  char* text;

  while (t != NULL) {
    switch (t->key) {
      case COMMAND_KEY:
        command = (int)t->value->int8;
        APP_LOG(APP_LOG_LEVEL_INFO, "COMMAND_KEY received with value %d", command);
        switch (command) {
          case START_COMMAND:
            start_worker();
            break;
          case STOP_COMMAND:
            stop_worker();
            break;
          case TIMESTAMP_COMMAND:
            text_layer_set_text(s_status_layer, "Synchronizing..." );
            send_command(TIMESTAMP_COMMAND);
            break;
          case LABEL_COMMAND:
            vibes_enqueue_custom_pattern(pat);
            text_layer_set_text(s_status_layer, "What are you doing?" );
            send_command(SYNC_MENU_ITEM_COMMAND);
            app_timer_register(20000, delayed_stop, NULL);
            break;
          case SYNC_MENU_ITEM_COMMAND:
            // do something during the next iteration
            break;
        }
        break;
      case MENU_ITEM_KEY:
        text = t->value->cstring;
        APP_LOG(APP_LOG_LEVEL_INFO, "MENU_ITEM_KEY received with value %s", text);
        append_to_menu(text);
        break;
    }
    
    t = dict_read_next(iterator);
  }
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped!");
}

static void outbox_failed_callback(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Outbox send failed!");
  switch (current_command) {
    case START_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Start command sent failed");
      break;
    case STOP_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Stop command sent failed");
      break;
    case TIMESTAMP_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Timestamp command sent failed");
      break;
    case SYNC_MENU_ITEM_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Sync command sent failed");
      break;
    case LABEL_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Label command sent failed");
      break;
  }
}

static void outbox_sent_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Outbox send success!");
  switch (current_command) {
    case START_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Start command sent");
      break;
    case STOP_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Stop command sent");
      break;
    case TIMESTAMP_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Timestamp sent");
      break;
    case SYNC_MENU_ITEM_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Sync command sent");
      break;
    case LABEL_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Label command sent");
      break;
  }
}

//------------------------------------------------------------------------------------------------- Mandatory structure - init - deinit
static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);
  
  // Create TextLayers
  s_title_layer = text_layer_create(GRect(0, 0, 3*window_bounds.size.w/4, window_bounds.size.h/8));
  text_layer_set_text(s_title_layer, "SmartDAYS");
  text_layer_set_text_alignment(s_title_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_title_layer));

  s_progress_layer = text_layer_create(GRect(3*window_bounds.size.w/4, 0, window_bounds.size.w/4, window_bounds.size.h/8));
  text_layer_set_text(s_progress_layer, "|");
  text_layer_set_text_alignment(s_progress_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_progress_layer));

  s_status_layer = text_layer_create(GRect(0, 7*window_bounds.size.h/8, window_bounds.size.w, window_bounds.size.h/8));
  if (!app_worker_is_running()) {
    text_layer_set_text(s_status_layer, "Waiting command...");
  }
  else {
    text_layer_set_text(s_status_layer, "What are you doing?");
  }
  text_layer_set_text_alignment(s_status_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_status_layer));

  // Create MenuLayer
  s_menu_layer = menu_layer_create(GRect(0, window_bounds.size.h/8, window_bounds.size.w, 3*window_bounds.size.h/4));
  menu_layer_set_callbacks(s_menu_layer, NULL, (MenuLayerCallbacks){
    .get_num_sections = menu_get_num_sections_callback,
    .get_num_rows = menu_get_num_rows_callback,
    .get_cell_height = menu_get_cell_height_callback,
    .draw_header = menu_draw_header_callback,
    .draw_row = menu_draw_row_callback,
    .select_click = menu_select_callback
  });
  menu_layer_set_click_config_onto_window(s_menu_layer, window);
  layer_add_child(window_layer, menu_layer_get_layer(s_menu_layer));
  
  // Populate menu
  switch (launch_reason()) {
    case APP_LAUNCH_USER:
    case APP_LAUNCH_QUICK_LAUNCH:
      send_command(SYNC_MENU_ITEM_COMMAND);
      break;
    case APP_LAUNCH_SYSTEM:
    case APP_LAUNCH_PHONE:
    case APP_LAUNCH_WAKEUP:
    case APP_LAUNCH_WORKER:
      break;
  }
}

static void main_window_unload(Window *window) {
  // Destroy the menu layer
  menu_layer_destroy(s_menu_layer);
  
  // Destroy TextLayers
  text_layer_destroy(s_title_layer);
  text_layer_destroy(s_progress_layer);
  text_layer_destroy(s_status_layer);
}

static void init() {
  uint8_t i;
  for (i = 0; i < MAX_MENU_ITEMS; i++) {
    menu_items[i] = malloc(MAX_MENU_ITEM_LENGTH);
  }

  // Register callbacks
  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_register_outbox_sent(outbox_sent_callback);

  // Open AppMessage
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());
  
  // Create main Window
  s_main_window = window_create();
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);
  
  // Subscribe to Worker messages
  app_worker_message_subscribe(worker_message_handler);
}

static void deinit() {
  APP_LOG(APP_LOG_LEVEL_INFO, "Quitting application!");
  // Deregister callbacks
  app_message_deregister_callbacks();
  // No more worker updates
  app_worker_message_unsubscribe();
  // Destroy main Window
  window_destroy(s_main_window);

  persist_write_bool(FROM_WATCH_APP_KEY, false);
  
  uint8_t i;
  for (i = 0; i < MAX_MENU_ITEMS; i++) {
    free(menu_items[i]);
  }
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

