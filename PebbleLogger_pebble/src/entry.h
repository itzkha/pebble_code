typedef void (*EntryCallback)(uint8_t activity_index, uint8_t callingMarker);

void entry_init(void);

void entry_deinit(void);

void entry_get_name(uint8_t markerType, EntryCallback callback);
