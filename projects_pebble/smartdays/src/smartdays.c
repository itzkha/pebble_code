//-------------------------------------------------------------------------------------------------
//  This application takes samples from the accelerometer at 25Hz, and sends timestamped packets of
//  BUFFER_SIZE (25) measures [x, y, z]. The application (worker) sends packets ~every minute
//-------------------------------------------------------------------------------------------------

#include <pebble.h>

#define TIMESTAMP_KEY 0xdeadbeef
#define LABEL_KEY 0xf00d50da
#define SOCIAL_KEY 0xc0cac01a

#define COMMAND_KEY 0xcafebabe
#define START_COMMAND 5
#define STOP_COMMAND 12
#define TIMESTAMP_COMMAND 17
#define SYNC_MENU_ITEM_COMMAND 7
#define ACTIVITY_LABEL_COMMAND 21
#define MOOD_LABEL_COMMAND 28

#define MAX_MENU_ITEMS_ACTIVITY 11
#define MAX_MENU_ITEMS_MOOD 8

#define N_PROGRESS_SYMBOLS 4
#define PROGRESS_KEY 10

#define FROM_WATCH_APP_KEY 98

#define MAX_TIME_WITHOUT_USER 15

static uint8_t s_ticks = 0;

static Window *s_main_window;
static TextLayer *s_title_layer;
static TextLayer *s_progress_layer;
static TextLayer *s_status_layer;
static char* s_progress[4] = {"|", "/", "--", "\\"};
static MenuLayer *s_menu_layer;

static Window *s_activity_window;
static MenuLayer *s_menu_activity_layer;
static char* menu_activity_items[MAX_MENU_ITEMS_ACTIVITY] =
            {"Commuting",
            "Eat/Drink",
            "Education",
            "Household",
            "Personal care",
            "Prof. services",
            "Shopping",
            "Social/Leisure",
            "Sports/Active",
            "Working",
            "No activity"};
static char* menu_activity_subs[MAX_MENU_ITEMS_ACTIVITY] = 
            {"foot, bike, train, car",  
            "lunch, dinner, beer",
            "in lecture, talks, hw",
            "cook, clean, laundry",
            "sleep, shower, toilet",
            "bank, doctor's, haircut",
            "grocery, store, mall",
            "party, movies, museum",
            "gym, skiing, biking, hiking",
            "day-job, work-related",
            NULL};
static char current_activity[20];
static bool must_send = false;

static Window *s_social_window;
static MenuLayer *s_menu_social_layer;
static GBitmap *social_icons[2];

static Window *s_mood_window;
static MenuLayer *s_menu_mood_layer;
static char* menu_mood_items[MAX_MENU_ITEMS_MOOD] = 
            {"Bored",
            "Excited",
            "Happy",
            "Relaxed",
            "Stressed",
            "Tense",
            "Tired",
            "Upset"};
static GBitmap *mood_icons[MAX_MENU_ITEMS_MOOD];

static const uint32_t const segments[] = { 100, 200, 100, 200, 100, 200, 100, 200, 100 };
VibePattern pat = {
  .durations = segments,
  .num_segments = ARRAY_LENGTH(segments),
};


DictionaryIterator iter;
DictionaryIterator* p_iter = &iter;
static int current_command;
static bool error_flag = false;

void send_command(int command);
void send_activity(char* label, char* social);
void send_mood(char* label);


//------------------------------------------------------------------------------------------------- Communication with the background worker
static void worker_message_handler(uint16_t type, AppWorkerMessage *data) {
  if (type == PROGRESS_KEY) { 
    text_layer_set_text(s_progress_layer, s_progress[data->data0]);
    
    APP_LOG(APP_LOG_LEVEL_INFO, "Packet pushed...%d", data->data0);
    
    switch (data->data1) {
      case DATA_LOGGING_SUCCESS:
        APP_LOG(APP_LOG_LEVEL_INFO, "Log OK");
        if (!error_flag) {
          text_layer_set_text(s_status_layer, "Log OK");
        }
        break;
      case DATA_LOGGING_BUSY:
        APP_LOG(APP_LOG_LEVEL_INFO, "Someone else is writing to this log!");
        text_layer_set_text(s_status_layer, "Log error");
        error_flag = true;
        break;
      case DATA_LOGGING_FULL:
        APP_LOG(APP_LOG_LEVEL_INFO, "No more space to save data!");
        text_layer_set_text(s_status_layer, "Log error");
        error_flag = true;
        break;
      case DATA_LOGGING_NOT_FOUND:
        APP_LOG(APP_LOG_LEVEL_INFO, "The log does not exist!");
        text_layer_set_text(s_status_layer, "Log error");
        error_flag = true;
        break;
      case DATA_LOGGING_CLOSED:
        APP_LOG(APP_LOG_LEVEL_INFO, "The log was made inactive!");
        text_layer_set_text(s_status_layer, "Log error");
        error_flag = true;
        break;
      case DATA_LOGGING_INVALID_PARAMS:
        APP_LOG(APP_LOG_LEVEL_INFO, "Invalid parameters!");
        text_layer_set_text(s_status_layer, "Log error");
        error_flag = true;
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
    }
    else {
      APP_LOG(APP_LOG_LEVEL_INFO, "Error launching worker!");
      text_layer_set_text(s_status_layer, "Worker error!");
      error_flag = true;
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
    }
    else {
      APP_LOG(APP_LOG_LEVEL_INFO, "Error killing worker!");
      text_layer_set_text(s_status_layer, "Error killing worker!");
      error_flag = true;
    }
  }
  else {
    APP_LOG(APP_LOG_LEVEL_INFO, "The worker is not running!");
    text_layer_set_text(s_status_layer, "The worker is not running!");
  }
}


//------------------------------------------------------------------------------------------------- Main menu
// A callback is used to specify the amount of sections of menu items
// With this, you can dynamically add and remove sections
static uint16_t menu_get_num_sections_callback(MenuLayer *menu_layer, void *data) {
  return 1;
}

// Each section has a number of items;  we use a callback to specify this
// You can also dynamically add and remove items using this
static uint16_t menu_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  return 2;
}

// A callback is used to specify the height of the section header
static int16_t menu_get_cell_height_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *callback_context) {
  // This is a define provided in pebble.h that you may use for the default height
  return 56;
}

// This is the menu item draw callback where you specify what each item should look like
static void menu_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item we'll draw
  uint8_t reason = launch_reason();
  switch (cell_index->row) {
    case 0:
      if ((reason == APP_LAUNCH_USER)  || (reason == APP_LAUNCH_QUICK_LAUNCH) ) {
        menu_cell_basic_draw(ctx, cell_layer, "Activities", "Enter next activity", NULL);
      }
      else {
        menu_cell_basic_draw(ctx, cell_layer, "Activities", "What are you doing?", NULL);
      }
      break;
    case 1:
      menu_cell_basic_draw(ctx, cell_layer, "Mood", "How do you feel?", NULL);
      break;
  }
}

void menu_selection_changed_callback(MenuLayer *menu_layer, MenuIndex new_index, MenuIndex old_index, void *data) {
  s_ticks = 0;
}

// Here we capture when a user selects a menu item
void menu_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item will receive the select action
  s_ticks = 0;

  switch (cell_index->row) {
    case 0:
      window_stack_push(s_activity_window, true);
      break;
    case 1:
      window_stack_push(s_mood_window, true);
      break;
  }
}

//------------------------------------------------------------------------------------------------- Activities menu
// A callback is used to specify the amount of sections of menu items
// With this, you can dynamically add and remove sections
static uint16_t menu_activity_get_num_sections_callback(MenuLayer *menu_layer, void *data) {
  return 1;
}

// Each section has a number of items;  we use a callback to specify this
// You can also dynamically add and remove items using this
static uint16_t menu_activity_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  return MAX_MENU_ITEMS_ACTIVITY;
}

// A callback is used to specify the height of the section header
static int16_t menu_activity_get_cell_height_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *callback_context) {
  // This is a define provided in pebble.h that you may use for the default height
  return 50;
}

// A callback is used to specify the height of the section header
static int16_t menu_activity_get_header_height_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  // This is a define provided in pebble.h that you may use for the default height
  return MENU_CELL_BASIC_HEADER_HEIGHT;
}

// Here we draw what each header is
static void menu_activity_draw_header_callback(GContext* ctx, const Layer *cell_layer, uint16_t section_index, void *data) {
  uint8_t reason = launch_reason();
  if ((reason == APP_LAUNCH_USER)  || (reason == APP_LAUNCH_QUICK_LAUNCH) ) {
    menu_cell_basic_header_draw(ctx, cell_layer, "Enter next activity");
  }
  else {
    menu_cell_basic_header_draw(ctx, cell_layer, "What are you doing?");
  }
}

// This is the menu item draw callback where you specify what each item should look like
static void menu_activity_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item we'll draw
  menu_cell_basic_draw(ctx, cell_layer, menu_activity_items[cell_index->row], menu_activity_subs[cell_index->row], NULL);
}

void menu_activity_selection_changed_callback(MenuLayer *menu_layer, MenuIndex new_index, MenuIndex old_index, void *data) {
  s_ticks = 0;
  menu_layer_set_selected_index(s_menu_activity_layer, new_index, MenuRowAlignCenter, true);
}

// Here we capture when a user selects a menu item
void menu_activity_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item will receive the select action
  s_ticks = 0;

  text_layer_set_text(s_status_layer, "Thank you!" );
  //send_label(menu_activity_items[cell_index->row], ACTIVITY_LABEL_COMMAND);
  strcpy(current_activity, menu_activity_items[cell_index->row]);
  must_send = true;
  window_stack_pop(true);
  window_stack_push(s_social_window, true);
}

//------------------------------------------------------------------------------------------------- Social menu
// A callback is used to specify the amount of sections of menu items
// With this, you can dynamically add and remove sections
static uint16_t menu_social_get_num_sections_callback(MenuLayer *menu_layer, void *data) {
  return 1;
}

// Each section has a number of items;  we use a callback to specify this
// You can also dynamically add and remove items using this
static uint16_t menu_social_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  return 2;
}

// A callback is used to specify the height of the section header
static int16_t menu_social_get_cell_height_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *callback_context) {
  // This is a define provided in pebble.h that you may use for the default height
  return 56;
}

// A callback is used to specify the height of the section header
static int16_t menu_social_get_header_height_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  // This is a define provided in pebble.h that you may use for the default height
  return MENU_CELL_BASIC_HEADER_HEIGHT;
}

// Here we draw what each header is
static void menu_social_draw_header_callback(GContext* ctx, const Layer *cell_layer, uint16_t section_index, void *data) {
  menu_cell_basic_header_draw(ctx, cell_layer, "Are you with someone?");
}

// This is the menu item draw callback where you specify what each item should look like
static void menu_social_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item we'll draw
  switch (cell_index->row) {
    case 0:
      menu_cell_basic_draw(ctx, cell_layer, "Alone", NULL, social_icons[cell_index->row]);
      break;
    case 1:
      menu_cell_basic_draw(ctx, cell_layer, "With others", NULL, social_icons[cell_index->row]);
      break;
  }
}

void menu_social_selection_changed_callback(MenuLayer *menu_layer, MenuIndex new_index, MenuIndex old_index, void *data) {
  s_ticks = 0;
}

// Here we capture when a user selects a menu item
void menu_social_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item will receive the select action
  s_ticks = 0;

  text_layer_set_text(s_status_layer, "Thank you!" );

  switch (cell_index->row) {
    case 0:
      send_activity(current_activity, "ALONE");
      break;
    case 1:
      send_activity(current_activity, "WITH_OTHERS");
      break;
  }
  window_stack_pop(true);
}

//------------------------------------------------------------------------------------------------- Mood menu
// A callback is used to specify the amount of sections of menu items
// With this, you can dynamically add and remove sections
static uint16_t menu_mood_get_num_sections_callback(MenuLayer *menu_layer, void *data) {
  return 1;
}

// Each section has a number of items;  we use a callback to specify this
// You can also dynamically add and remove items using this
static uint16_t menu_mood_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  return MAX_MENU_ITEMS_MOOD;
}

// A callback is used to specify the height of the section header
static int16_t menu_mood_get_cell_height_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *callback_context) {
  // This is a define provided in pebble.h that you may use for the default height
  return 45;
}

// A callback is used to specify the height of the section header
static int16_t menu_mood_get_header_height_callback(MenuLayer *menu_layer, uint16_t section_index, void *data) {
  // This is a define provided in pebble.h that you may use for the default height
  return MENU_CELL_BASIC_HEADER_HEIGHT;
}

// Here we draw what each header is
static void menu_mood_draw_header_callback(GContext* ctx, const Layer *cell_layer, uint16_t section_index, void *data) {
  menu_cell_basic_header_draw(ctx, cell_layer, "How do you feel?");
}

void menu_mood_selection_changed_callback(MenuLayer *menu_layer, MenuIndex new_index, MenuIndex old_index, void *data) {
  s_ticks = 0;
}

// This is the menu item draw callback where you specify what each item should look like
static void menu_mood_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item we'll draw
  menu_cell_basic_draw(ctx, cell_layer, menu_mood_items[cell_index->row], NULL, mood_icons[cell_index->row]);
}

// Here we capture when a user selects a menu item
void menu_mood_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data) {
  // Use the row to specify which item will receive the select action
  s_ticks = 0;

  text_layer_set_text(s_status_layer, "Thank you!" );
  send_mood(menu_mood_items[cell_index->row]);
  window_stack_pop(true);
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
  dict_write_int8(p_iter, COMMAND_KEY, current_command);
  dict_write_data(p_iter, TIMESTAMP_KEY, (uint8_t*)&timestamp, sizeof(timestamp));
  app_message_outbox_send();
}

void send_activity(char* label, char* social) {
  must_send = false;
  current_command = ACTIVITY_LABEL_COMMAND;
  app_message_outbox_begin(&p_iter);
  dict_write_int8(p_iter, COMMAND_KEY, current_command);
  dict_write_cstring(p_iter, LABEL_KEY, label);
  dict_write_cstring(p_iter, SOCIAL_KEY, social);
  app_message_outbox_send();
  strcpy(current_activity, "NA");
}

void send_mood(char* label) {
  current_command = MOOD_LABEL_COMMAND;
  app_message_outbox_begin(&p_iter);
  dict_write_int8(p_iter, COMMAND_KEY, current_command);
  dict_write_cstring(p_iter, LABEL_KEY, label);
  app_message_outbox_send();
}

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message received!");
  // Get the first pair
  Tuple *t = dict_read_first(iterator);
  int command;

  s_ticks = 0;

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
          case ACTIVITY_LABEL_COMMAND:
            vibes_enqueue_custom_pattern(pat);
            text_layer_set_text(s_status_layer, "What are you doing?");
            if (window_stack_get_top_window() == s_mood_window) {
              window_stack_pop(true);
            }
            if (window_stack_get_top_window() != s_activity_window) {
              window_stack_push(s_activity_window, true);
            }
            break;
          case MOOD_LABEL_COMMAND:
            vibes_enqueue_custom_pattern(pat);
            text_layer_set_text(s_status_layer, "How do you feel?" );
            if (window_stack_get_top_window() == s_activity_window) {
              window_stack_pop(true);
            }
            if (window_stack_get_top_window() != s_mood_window) {
              window_stack_push(s_mood_window, true);
            }
            break;
        }
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
    case ACTIVITY_LABEL_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Label command sent failed");
      break;
  }
  text_layer_set_text(s_status_layer, "Comm. failed!");
  error_flag = true;
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
    case ACTIVITY_LABEL_COMMAND:
      APP_LOG(APP_LOG_LEVEL_INFO, "Label command sent");
      break;
  }
}

//------------------------------------------------------------------------------------------------- Tick handler for stopping automatically
static void tick_handler(struct tm *tick_timer, TimeUnits units_changed) {
  // Update value
  s_ticks++;

  if (s_ticks > MAX_TIME_WITHOUT_USER) {
    if (must_send) {
      send_activity(current_activity, "NA");
    }
    else {
      window_stack_pop_all(true);
      s_ticks = 0;
    }
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
    uint8_t reason = launch_reason();
    if ((reason == APP_LAUNCH_USER)  || (reason == APP_LAUNCH_QUICK_LAUNCH) ) {
      text_layer_set_text(s_status_layer, "Enter next activity");
    }
    else {
      text_layer_set_text(s_status_layer, "What are you doing?");
    }
  }
  text_layer_set_text_alignment(s_status_layer, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_status_layer));

  // Create MenuLayer
  s_menu_layer = menu_layer_create(GRect(0, window_bounds.size.h/8, window_bounds.size.w, 3*window_bounds.size.h/4));
  menu_layer_set_callbacks(s_menu_layer, NULL, (MenuLayerCallbacks){
    .get_num_sections = menu_get_num_sections_callback,
    .get_num_rows = menu_get_num_rows_callback,
    .get_cell_height = menu_get_cell_height_callback,
    .draw_row = menu_draw_row_callback,
    .selection_changed = menu_selection_changed_callback,
    .select_click = menu_select_callback
  });
  menu_layer_set_click_config_onto_window(s_menu_layer, window);
  layer_add_child(window_layer, menu_layer_get_layer(s_menu_layer));
}

static void main_window_unload(Window *window) {
  // Destroy the menu layer
  menu_layer_destroy(s_menu_layer);
  
  // Destroy TextLayers
  text_layer_destroy(s_title_layer);
  text_layer_destroy(s_progress_layer);
  text_layer_destroy(s_status_layer);
}

static void activity_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);

  // Create MenuLayer
  s_menu_activity_layer = menu_layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h));
  menu_layer_set_callbacks(s_menu_activity_layer, NULL, (MenuLayerCallbacks){
    .get_num_sections = menu_activity_get_num_sections_callback,
    .get_num_rows = menu_activity_get_num_rows_callback,
    .get_cell_height = menu_activity_get_cell_height_callback,
    .get_header_height = menu_activity_get_header_height_callback,
    .draw_header = menu_activity_draw_header_callback,
    .draw_row = menu_activity_draw_row_callback,
    .selection_changed = menu_activity_selection_changed_callback,
    .select_click = menu_activity_select_callback
  });
  menu_layer_set_click_config_onto_window(s_menu_activity_layer, window);
  layer_add_child(window_layer, menu_layer_get_layer(s_menu_activity_layer));
}

static void activity_window_unload(Window *window) {
  menu_layer_destroy(s_menu_activity_layer);
}

static void social_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);
  
  social_icons[0] = gbitmap_create_with_resource(RESOURCE_ID_ALONE);
  social_icons[1] = gbitmap_create_with_resource(RESOURCE_ID_WITHOTHERS);

  // Create MenuLayer
  s_menu_social_layer = menu_layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h));
  menu_layer_set_callbacks(s_menu_social_layer, NULL, (MenuLayerCallbacks){
    .get_num_sections = menu_social_get_num_sections_callback,
    .get_num_rows = menu_social_get_num_rows_callback,
    .get_cell_height = menu_social_get_cell_height_callback,
    .get_header_height = menu_social_get_header_height_callback,
    .draw_header = menu_social_draw_header_callback,
    .draw_row = menu_social_draw_row_callback,
    .selection_changed = menu_social_selection_changed_callback,
    .select_click = menu_social_select_callback
  });
  menu_layer_set_click_config_onto_window(s_menu_social_layer, window);
  layer_add_child(window_layer, menu_layer_get_layer(s_menu_social_layer));
}

static void social_window_unload(Window *window) {
  gbitmap_destroy(mood_icons[0]);
  gbitmap_destroy(mood_icons[1]);

  menu_layer_destroy(s_menu_social_layer);
}

static void mood_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);

  mood_icons[0] = gbitmap_create_with_resource(RESOURCE_ID_BORED);
  mood_icons[1] = gbitmap_create_with_resource(RESOURCE_ID_EXCITED);
  mood_icons[2] = gbitmap_create_with_resource(RESOURCE_ID_HAPPY);
  mood_icons[3] = gbitmap_create_with_resource(RESOURCE_ID_RELAXED);
  mood_icons[4] = gbitmap_create_with_resource(RESOURCE_ID_STRESSED);
  mood_icons[5] = gbitmap_create_with_resource(RESOURCE_ID_TENSE);
  mood_icons[6] = gbitmap_create_with_resource(RESOURCE_ID_TIRED);
  mood_icons[7] = gbitmap_create_with_resource(RESOURCE_ID_UPSET);

  // Create MenuLayer
  s_menu_mood_layer = menu_layer_create(GRect(0, 0, window_bounds.size.w, window_bounds.size.h));
  menu_layer_set_callbacks(s_menu_mood_layer, NULL, (MenuLayerCallbacks){
    .get_num_sections = menu_mood_get_num_sections_callback,
    .get_num_rows = menu_mood_get_num_rows_callback,
    .get_cell_height = menu_mood_get_cell_height_callback,
    .get_header_height = menu_mood_get_header_height_callback,
    .draw_header = menu_mood_draw_header_callback,
    .draw_row = menu_mood_draw_row_callback,
    .selection_changed = menu_mood_selection_changed_callback,
    .select_click = menu_mood_select_callback
  });
  menu_layer_set_click_config_onto_window(s_menu_mood_layer, window);
  layer_add_child(window_layer, menu_layer_get_layer(s_menu_mood_layer));
}

static void mood_window_unload(Window *window) {
  uint8_t i;

  for (i = 0; i < MAX_MENU_ITEMS_MOOD; i++) {
    gbitmap_destroy(mood_icons[i]);
  }

  menu_layer_destroy(s_menu_mood_layer);
}

static void init() {
  strcpy(current_activity, "NA");

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
  
  // Create activity labels window
  s_activity_window = window_create();
  window_set_window_handlers(s_activity_window, (WindowHandlers) {
    .load = activity_window_load,
    .unload = activity_window_unload
  });

  // Create social labels window
  s_social_window = window_create();
  window_set_window_handlers(s_social_window, (WindowHandlers) {
    .load = social_window_load,
    .unload = social_window_unload
  });

  // Create mood labels window
  s_mood_window = window_create();
  window_set_window_handlers(s_mood_window, (WindowHandlers) {
    .load = mood_window_load,
    .unload = mood_window_unload
  });

  // Subscribe to Worker messages
  app_worker_message_subscribe(worker_message_handler);

  // Timer for auto-stop
  tick_timer_service_subscribe(SECOND_UNIT, tick_handler);
}

static void deinit() {
  APP_LOG(APP_LOG_LEVEL_INFO, "Quitting application!");
  tick_timer_service_unsubscribe();
  // Deregister callbacks
  app_message_deregister_callbacks();
  // No more worker updates
  app_worker_message_unsubscribe();
  // Destroy main Window
  window_destroy(s_main_window);
  // Destroy activity Window
  window_destroy(s_activity_window);
  // Destroy social Window
  window_destroy(s_social_window);
  // Destroy mood Window
  window_destroy(s_mood_window);

  persist_write_bool(FROM_WATCH_APP_KEY, false);

}

int main(void) {
  init();
  app_event_loop();
  deinit();
}

