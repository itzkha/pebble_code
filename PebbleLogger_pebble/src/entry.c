#include "pebble.h"

#include "entry.h"

#define NUM_MAIN_ACTIVITIES 13
#define NUM_MENU_SECTIONS 1

static Window *window;

static uint8_t selected_row_index; 
static uint8_t marker_type;
EntryCallback hs_callback; // function from PebbleLogger 

static uint8_t row_clicked;
// This is a simple menu layer
static SimpleMenuLayer *simple_menu_layer;

// A simple menu layer can have multiple sections
static SimpleMenuSection menu_sections[NUM_MENU_SECTIONS];

// Each section is composed of a number of menu items
static SimpleMenuItem main_activity_items[NUM_MAIN_ACTIVITIES];


const char *main_activities[] = {
  "Eat/Drink",
  "Working",
  "Education",
  "Personal Care",
  "Sports/Rec.",
  "Leisure/Relax/Social",
  "Household",
  "Consumer Purch.",
  "Travelling",
  "Receiving Serv.",
  "Religious Activities",
  "Volunteer Activities",
  "Gvt/Civic Obligations"
};

const char *example_activities[] = {
  "lunch, dinner, beer",
  "day-job,(not classes!)",
  "attending class, hw",
  "sleeping, showering",
  "skiing, weights, etc.",
  "party, watching tv, museum",
  "cooking, cleaning, laundry",
  "shopping, browsing",
  "commute (by foot, car)",
  "bank, doctor's, haircut",
  "attending church",
  "community service",
  "military service"
};


// You can capture when the user selects a menu icon with a menu item select callback
static void menu_select_callback(int index, void *ctx) {
  selected_row_index = index;
  row_clicked = 1;
  // Restore example activities for all rows first
  for (uint8_t i = 0; i < NUM_MAIN_ACTIVITIES; i++) {
    main_activity_items[i].subtitle = example_activities[i];
  }
  // Here we just change the subtitle to indicate selected
  main_activity_items[index].subtitle = "ACTIVITY SELECTED";
  // Mark the layer to be updated
  layer_mark_dirty(simple_menu_layer_get_layer(simple_menu_layer));
}

// This initializes the menu upon window load
static void window_load(Window *window) {
   
 for (uint8_t i = 0; i < NUM_MAIN_ACTIVITIES; i++) { 

  // This is an example of how you'd set a simple menu item
  main_activity_items[i] = (SimpleMenuItem){
    // You should give each menu item a title and callback
    .title = main_activities[i],
    .subtitle = example_activities[i],
    .callback = menu_select_callback,
  };
 }
  // Bind the menu items to the corresponding menu sections
  menu_sections[0] = (SimpleMenuSection){
    .num_items = NUM_MAIN_ACTIVITIES,
    .items = main_activity_items,
  };

  // Now we prepare to initialize the simple menu layer
  // We need the bounds to specify the simple menu layer's viewport size
  // In this case, it'll be the same as the window's
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_frame(window_layer);

  // Initialize the simple menu layer
  simple_menu_layer = simple_menu_layer_create(bounds, window, menu_sections, NUM_MENU_SECTIONS, NULL);

  // Add it to the window for display
  layer_add_child(window_layer, simple_menu_layer_get_layer(simple_menu_layer));
}

/* static void click_config_provider(void *context) { */


/*   window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler); */
/*   window_long_click_subscribe(BUTTON_ID_SELECT, 0, select_long_click_handler, NULL); */

/* } */

// Deinitialize resources on window unload that were initialized on window load
void window_unload(Window *window) {
  simple_menu_layer_destroy(simple_menu_layer);

  //trigger callback to send off marker to server only if a row was clicked
  if (row_clicked == 1) {
    hs_callback(selected_row_index,marker_type);
    row_clicked = 0;
  }
}


void entry_init(void) {
  window = window_create();
  /* window_set_click_config_provider(ui.window, click_config_provider); */
  window_set_window_handlers(window, (WindowHandlers) {
      .load = window_load,
	.unload = window_unload,
	}
    );
}

void entry_deinit(void) {
  window_destroy(window);
}

void entry_get_name(uint8_t markerType, EntryCallback callback) {
  hs_callback = callback;
  marker_type = markerType;
  window_stack_push(window, true);
  
}

