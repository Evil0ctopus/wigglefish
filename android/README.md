# Wigglefish Android controller

This native Android app connects to the ESP32-C5 through USB-C OTG and the CH343 USB-serial bridge.

## Behavior

- Prefers the CH343 by VID/PID `1A86:55D3`, recognizes common CYD CH340 `1A86:7523`, CP210x, FTDI, and native ESP USB IDs, then accepts any USB-serial device supported by usb-serial-for-android.
- Discovers USB serial devices without opening them on launch. The user taps **Connect** to request Android USB permission, open the serial link at 115200 baud, and send the status command.
- Registers for the CH343 USB attach event so connecting the board can launch the controller; the attach event does not open the serial link or send data.
- Reads the firmware's newline-delimited passive-survey JSON stream.
- Displays Wi-Fi and BLE observations with separate live counters.
- Finds supported Triones, Lotus Lantern, and ELK-BLEDOM BLE lights from advertisements. The user selects and confirms one light before direct GATT connection and RGB/power control; light commands are never broadcast to nearby BLE devices or the local network. Other vendors and Wi-Fi-only lights are not yet supported.
- Accepts the firmware's `type: "wifi"` and Wardrive Go-compatible `type: "bluetooth"` records.
- Normalizes common legacy text scan lines containing SSID/BSSID/MAC/RSSI fields from other passive firmware families.
- Parses WiGLE-style CSV rows used by HaleHound-style ESP32 CYD builds when those rows are streamed or imported.
- Keeps a separate USB-device JSONL audit log for connected-board events and passive board observations.

Passive survey is the default. Optional active lab tools can transmit wireless frames or capture management/authentication traffic and may disrupt nearby equipment; use them only in an isolated environment you own or are authorized to test. The captive portal demo is transparent and never requests or stores credentials.

The compatibility parsers normalize supported survey metadata formats. Legacy portal credential events are discarded without saving their contents. HaleHound deployments that only save CSV to a microSD card still need the CSV file transferred to the phone; the app cannot read another board's SD card through a serial connection automatically.

## Event UI

The controller uses five fixed bottom destinations: **Discover**, **Radar**,
**Tools**, **Insights**, and **Save**. Signal results are recycled native list
rows with readable names, signal strength, and text risk labels. Tap a row for
the existing detail and target actions; the filters apply to the same survey
records as before.
Short screens use a compact comic masthead and tighter card spacing so artwork
does not crowd out the signal list. Navigation labels size to fit without clipping.

Tool cards are collapsed by default. Open a card to see its controls, or expand
**Show session activity** for detailed traffic and event logs. Arming and stop
controls stay visible above the cards. Expanding a card does not run a tool.
**Lumi** opens the companion separately instead of occupying the survey screen.
**Paint** opens the custom paint shop with four saved comic palettes: Turbo Pop,
Candy Drift, Reef Racer, and Sunset Stripe. The original artwork uses cel-shaded
planes, thick ink outlines, offset shadows, halftone dots, and painted highlights,
inspired by comic-style custom car paint rather than copied character art.
Navigation and filter selection survive activity recreation.
Radar target selection is available at the top of the page. Insights show the
summary first, with the complete survey report available on demand.

### Comic motion

Page changes have short panel entrances and a one-shot Lumi celebration.
Navigation, filters, and disclosure controls have springy tap feedback. Radar
has an inked sweep; the separate Lumi pit stop has swimming, poke confetti,
and evolution accessories. Animations never initiate device commands.

Use **Paint > Motion** to switch between animated and reduced-motion modes.
Android's disabled animator setting is also respected. Radar/companion loops
run at approximately 30 fps only while visible and stop offscreen or in the
background. Hero celebrations end after 700 ms; there is no animated wallpaper.
Signal rows remain recycled, and repeated unchanged button updates reuse their
paintwork. The radar is a stylized **signal mix, not a location map**.

### Emulator verification (no phone required)

The workspace task **Android: verify event UI** builds the debug app,
instrumentation APK, and Android lint report. The debug application uses the
separate package `com.wigglefish.android.lumitest`.

Run `EventUiTest` on an Android emulator using Android Studio's instrumented
test runner. Tests cover all five destinations, activity recreation, tool
disclosures, filtering synthetic observations, and recycling a 1,000-row list.
They also check hidden/background animation suspension, all four palettes,
and opening/closing the separately hosted Lumi companion.
Comic regression tests also verify paint/keyline pixels, text contrast of at
least 4.5:1 across all palettes, reduced-motion persistence, finite hero
celebrations, and reuse of unchanged button drawables.
Layout assertions require unclipped navigation labels and room for a complete
two-line signal card, including at 360dp width with 130% text.
Grant location and Bluetooth permissions to the emulator app before running
the tests. No test opens a USB port, arms a test, or sends device commands.

## Build

Open this `android` directory in Android Studio, allow Gradle to sync, then run the `app` configuration on the Galaxy S26 Ultra. Connect the ESP32-C5 through a USB-C OTG data adapter and accept the USB permission prompt.

CYD boards can connect through a USB-C OTG adapter when the board exposes its USB-UART bridge. The board still needs compatible passive scanner firmware, such as HaleHound CSV output or firmware that emits Wigglefish JSON. A bare ESP32, factory firmware, or unrelated firmware may connect as serial but will not produce Wi-Fi/BLE observations.
