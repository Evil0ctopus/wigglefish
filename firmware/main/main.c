#include <stdio.h>
#include <string.h>

#include "driver/gpio.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_gap.h"
#include "host/ble_hs.h"
#include "nvs_flash.h"

static const char *TAG = "wigglefish";
static wifi_ap_record_t scan_records[64];

// The Wireless Tag board routes its onboard status LED to GPIO6.
#define STATUS_LED_GPIO GPIO_NUM_6
#define STATUS_LED_ACTIVE_HIGH 1

static void status_led_set(bool on)
{
    gpio_set_level(STATUS_LED_GPIO, STATUS_LED_ACTIVE_HIGH ? on : !on);
}

static void status_led_pulse(uint8_t count)
{
    for (uint8_t index = 0; index < count; index++) {
        status_led_set(true);
        vTaskDelay(pdMS_TO_TICKS(120));
        status_led_set(false);
        vTaskDelay(pdMS_TO_TICKS(120));
    }
}

static void initialize_status_led(void)
{
    gpio_config_t config = {
        .pin_bit_mask = 1ULL << STATUS_LED_GPIO,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    ESP_ERROR_CHECK(gpio_config(&config));
    status_led_set(true);
}

static void print_json_string(const char *value)
{
    putchar('"');
    for (const unsigned char *cursor = (const unsigned char *)value; *cursor != '\0'; cursor++) {
        if (*cursor == '"' || *cursor == '\\') {
            putchar('\\');
        }
        if (*cursor >= 0x20) {
            putchar(*cursor);
        }
    }
    putchar('"');
}

static const char *auth_mode_name(wifi_auth_mode_t mode)
{
    switch (mode) {
        case WIFI_AUTH_OPEN:
            return "OPEN";
        case WIFI_AUTH_WEP:
            return "WEP";
        case WIFI_AUTH_WPA_PSK:
            return "WPA-PSK";
        case WIFI_AUTH_WPA2_PSK:
            return "WPA2-PSK";
        case WIFI_AUTH_WPA_WPA2_PSK:
            return "WPA/WPA2-PSK";
        case WIFI_AUTH_WPA2_ENTERPRISE:
            return "WPA2-EAP";
        case WIFI_AUTH_WPA3_PSK:
            return "WPA3-PSK";
        case WIFI_AUTH_WPA2_WPA3_PSK:
            return "WPA2/WPA3-PSK";
        default:
            return "UNKNOWN";
    }
}

static void print_ble_name(const uint8_t *data, uint8_t length)
{
    putchar('"');
    for (uint8_t index = 0; index < length; index++) {
        unsigned char value = data[index];
        if (value == '"' || value == '\\') {
            putchar('\\');
        }
        if (value >= 0x20) {
            putchar(value);
        }
    }
    putchar('"');
}

static void emit_ble_record_data(const uint8_t *address, int8_t rssi, const uint8_t *payload, uint8_t payload_length)
{
    printf("{\"type\":\"bluetooth\",\"address\":\"%02X:%02X:%02X:%02X:%02X:%02X\",\"mac\":\"%02X:%02X:%02X:%02X:%02X:%02X\",\"rssi\":%d",
            address[0], address[1], address[2], address[3], address[4], address[5],
            address[0], address[1], address[2], address[3], address[4], address[5], rssi);

    bool has_name = false;
    for (uint8_t offset = 0; offset < payload_length;) {
        uint8_t field_length = payload[offset];
        if (field_length == 0 || offset + field_length + 1 > payload_length) {
            break;
        }
        uint8_t field_type = payload[offset + 1];
        const uint8_t *field_data = &payload[offset + 2];
        uint8_t data_length = field_length - 1;
        if ((field_type == 0x08 || field_type == 0x09) && data_length > 0 && !has_name) {
            printf(",\"name\":");
            print_ble_name(field_data, data_length);
            has_name = true;
        } else if (field_type == 0xFF && data_length > 0) {
            printf(",\"manufacturer_data\":[");
            for (uint8_t index = 0; index < data_length; index++) {
                if (index > 0) {
                    putchar(',');
                }
                printf("%u", field_data[index]);
            }
            putchar(']');
        }
        offset += field_length + 1;
    }
    puts("}");
}

static int ble_gap_event_handler(struct ble_gap_event *event, void *argument)
{
    (void)argument;
    if (event->type == BLE_GAP_EVENT_DISC) {
        emit_ble_record_data(
            event->disc.addr.val,
            event->disc.rssi,
            event->disc.data,
            event->disc.length_data
        );
    }
    return 0;
}

static void start_ble_scan(void)
{
    struct ble_gap_disc_params scan_params = {
        .itvl = 500,
        .window = 250,
        .filter_policy = 0,
        .limited = 0,
        .passive = 1,
        .filter_duplicates = 1,
    };
    uint8_t own_address_type;
    int result = ble_hs_id_infer_auto(0, &own_address_type);
    if (result == 0) {
        result = ble_gap_disc(own_address_type, BLE_HS_FOREVER, &scan_params, ble_gap_event_handler, NULL);
    }
    if (result != 0) {
        ESP_LOGE(TAG, "BLE scan start failed: %d", result);
    }
}

static void ble_on_sync(void)
{
    start_ble_scan();
}

static void ble_host_task(void *argument)
{
    (void)argument;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

static void initialize_ble(void)
{
    ESP_ERROR_CHECK(nimble_port_init());
    ble_hs_cfg.sync_cb = ble_on_sync;
    nimble_port_freertos_init(ble_host_task);
}

static void scan_wifi(void)
{
    puts("{\"event\":\"scan_start\"}");
    fflush(stdout);
    wifi_scan_config_t scan_config = {
        .ssid = NULL,
        .bssid = NULL,
        .channel = 0,
        .show_hidden = true,
        .scan_type = WIFI_SCAN_TYPE_PASSIVE,
        .scan_time.passive = 0,
    };

    esp_err_t result = esp_wifi_scan_start(&scan_config, true);
    if (result != ESP_OK) {
        status_led_pulse(5);
        printf("{\"event\":\"error\",\"code\":%d}\n", result);
        return;
    }

    uint16_t count = 0;
    esp_wifi_scan_get_ap_num(&count);
    uint16_t record_count = count < 64 ? count : 64;
    esp_wifi_scan_get_ap_records(&record_count, scan_records);
    status_led_set(false);
    printf("{\"event\":\"scan_count\",\"count\":%u}\n", record_count);
    for (uint16_t index = 0; index < record_count; index++) {
        wifi_ap_record_t *record = &scan_records[index];
        printf("{\"type\":\"wifi\",\"ssid\":");
        print_json_string((const char *)record->ssid);
        printf(",\"bssid\":\"%02X:%02X:%02X:%02X:%02X:%02X\",\"channel\":%u,\"rssi\":%d,\"security\":\"%s\",\"encryption\":\"%s\"}\n",
               record->bssid[0], record->bssid[1], record->bssid[2],
               record->bssid[3], record->bssid[4], record->bssid[5],
               record->primary,
               record->rssi,
               auth_mode_name(record->authmode),
               auth_mode_name(record->authmode));
    }
    puts("{\"event\":\"scan_end\"}");
}

static void initialize_wifi(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t wifi_config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&wifi_config));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());
}

void app_main(void)
{
    initialize_status_led();
    esp_err_t nvs_result = nvs_flash_init();
    if (nvs_result == ESP_ERR_NVS_NO_FREE_PAGES || nvs_result == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        nvs_result = nvs_flash_init();
    }
    ESP_ERROR_CHECK(nvs_result);
    initialize_wifi();
    initialize_ble();
    ESP_LOGI(TAG, "ready; waiting for JSON commands at 115200 baud");
    status_led_set(false);
    status_led_pulse(3);
    puts("{\"event\":\"ready\",\"mode\":\"auto_scan\",\"interval_seconds\":10}");
    fflush(stdout);
    while (true) {
        scan_wifi();
        fflush(stdout);
        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}
