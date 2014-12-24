#include <pebble_worker.h>

#define DATA_LOG_TAG_ACCELEROMETER 51
#define BUFFER_SIZE 25
#define BUFFER_SIZE_BYTES sizeof(uint64_t)+(3*BUFFER_SIZE*sizeof(int16_t))
#define WORKER_DOTS 10
#define FROM_WATCH_APP_KEY 98

static DataLoggingSessionRef s_log_ref;
static int packets_sent = 0;
static bool from_watch_app = false;

typedef struct packet {
  uint64_t timestamp;
  AccelRawData xyz[BUFFER_SIZE];
} accel_packet;                                             //if BUFFER_SIZE_BYTES is not a multiple of 8, C appends some bytes to perform memory packing (8 bytes)

static accel_packet to_send;
static const size_t accel_data_size_bytes = 3*BUFFER_SIZE*sizeof(int16_t);


static void data_handler(AccelRawData *data, uint32_t num_samples, uint64_t timestamp) {
//  uint16_t i;
  static DataLoggingResult result;
  
  to_send.timestamp = timestamp;
/*  for (i = 0; i < num_samples; i++) {
    to_send.xyz[i][0] = (int16_t)data[i].x;                 //save the measures
    to_send.xyz[i][1] = (int16_t)data[i].y;
    to_send.xyz[i][2] = (int16_t)data[i].z;
  }*/
  memcpy(to_send.xyz, data, accel_data_size_bytes);
  result = data_logging_log(s_log_ref, &to_send, 1);        //push the data
                                                            //data are sent to the phone (if available) ~every minute (I don't know how to change that)
  if (result == DATA_LOGGING_SUCCESS) {
    packets_sent++;
  }

  AppWorkerMessage msg_data;
  msg_data.data0 = packets_sent % WORKER_DOTS;
  msg_data.data1 = result;
  app_worker_send_message(WORKER_DOTS, &msg_data);          //send a message to the application
}

void delayed_logging(void *data) {
  // Start logging
  s_log_ref = data_logging_create(DATA_LOG_TAG_ACCELEROMETER, DATA_LOGGING_BYTE_ARRAY, BUFFER_SIZE_BYTES, true);
  // Subscribe to the accelerometer data service
  accel_raw_data_service_subscribe(BUFFER_SIZE, data_handler);
  // Choose update rate
  accel_service_set_sampling_rate(ACCEL_SAMPLING_25HZ);
}

static void worker_init() {
  app_timer_register(1000, delayed_logging, NULL);
}

static void worker_deinit() {
  // Finish logging session
  accel_data_service_unsubscribe();
  data_logging_finish(s_log_ref);
  
  persist_write_bool(FROM_WATCH_APP_KEY, false);
}

int main(void) {
  from_watch_app = persist_exists(FROM_WATCH_APP_KEY) ? persist_read_int(FROM_WATCH_APP_KEY) : false;
  if (from_watch_app) {                                                                            // check whether the worker was launched from the watch app
    worker_init();
    worker_event_loop();
    worker_deinit();
  }
  else {
    worker_launch_app();
  }
}

