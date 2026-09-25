# ESP Web Tools flash assets

This directory holds the browser-flasher manifest template for Site Keeper
(`https://evil0ctopus.github.io/` Flash section) using
[ESP Web Tools](https://esphome.github.io/esp-web-tools/).

## Target hardware

| Item | Value |
|------|--------|
| Chip | ESP32-C5 (`chipFamily`: `ESP32-C5`) |
| Flash size | 4 MB (`CONFIG_ESPTOOLPY_FLASHSIZE_4MB`) |
| Partition table | `../partitions.csv` (factory app @ `0x10000`, 2 MB) |
| Console baud | 115200 |
| Scope | Authorized / passive Wi-Fi + BLE advertisement metadata only |

## Flash offsets (ESP-IDF / ESP32-C5)

| Binary | Offset | Notes |
|--------|--------|--------|
| `bootloader.bin` | `0x2000` (8192) | ESP32-C5 ROM loads 2nd-stage bootloader here (not `0x0`) |
| `partition-table.bin` | `0x8000` (32768) | Standard ESP-IDF partition table offset |
| `wigglefish_firmware.bin` | `0x10000` (65536) | Factory app from `partitions.csv` |

Optional merged image (preferred by ESP Web Tools for ESP-IDF v4+ so flash mode/size/freq are baked in):

```text
merged-firmware.bin @ offset 0
```

Build the merged image after a successful `idf.py build` with:

```bash
esptool --chip esp32c5 merge_bin \
  -o build/merged-firmware.bin \
  --flash_mode dio \
  --flash_freq 80m \
  --flash_size 4MB \
  0x2000 build/bootloader/bootloader.bin \
  0x8000 build/partition_table/partition-table.bin \
  0x10000 build/wigglefish_firmware.bin
```

(Adjust `--flash_freq` to match what `idf.py build` printed / `sdkconfig`.)

## Manifest usage

1. Publish a GitHub Release (for example `firmware-v0.1.0`) with the three
   binaries (and optionally `merged-firmware.bin`).
2. Copy `manifest.template.json` → a live `manifest.json`.
3. Replace `VERSION_PLACEHOLDER` with the release tag.
4. Replace `ASSET_BASE_URL` with the release asset base, for example:

   `https://github.com/Evil0ctopus/wigglefish/releases/download/firmware-v0.1.0`

5. If using the merged image only, set a single part:

```json
"parts": [ { "path": "ASSET_BASE_URL/merged-firmware.bin", "offset": 0 } ]
```

6. Host `manifest.json` where Site Keeper can load it (GitHub Pages or
   release-adjacent). Ensure CORS allows the site origin if the manifest /
   bins are on another host (`Access-Control-Allow-Origin`).

## Site Keeper button snippet

```html
<script
  type="module"
  src="https://unpkg.com/esp-web-tools@10/dist/web/install-button.js?module"
></script>

<esp-web-install-button manifest="/path/to/manifest.json">
  <button slot="activate">Flash Wigglefish firmware</button>
</esp-web-install-button>
```

Requires HTTPS and a browser with Web Serial (Chrome / Edge / Firefox desktop).

## Local build (Josh)

```bash
# ESP-IDF 5.5.5 (or later with ESP32-C5 support), then:
cd firmware
idf.py set-target esp32c5
idf.py build
# Flash over USB-UART (CH343 etc.):
idf.py -p /dev/ttyUSB0 flash monitor
```

## CI

Workflow definition lives at `firmware/web-flash/firmware-release.yml`
(kept outside `.github/workflows/` until a token with the `workflow`
scope can install it).

To enable GitHub Actions builds:

```bash
mkdir -p .github/workflows
cp firmware/web-flash/firmware-release.yml .github/workflows/firmware-release.yml
git add .github/workflows/firmware-release.yml
git commit -m "Enable ESP32-C5 firmware release workflow"
git push
```

Then push a tag `firmware-v*` (or run the workflow via workflow_dispatch)
to build and attach release assets.
