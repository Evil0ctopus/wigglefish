# Wigglefish Phased Feature Roadmap

## Vision

Wigglefish is a dark, tactical wireless observation app with a multi-page interface. It combines passive scanning, forensic intelligence, defensive tool controls, and a new Airspace Monitor for observing and tracking airborne objects in the local area.

The app should stay modular and readable instead of forcing every feature onto one screen.

---

## Core App Structure

### Page 1: Dashboard
- live scan status
- sensor summary
- active mode
- quick actions
- last event feed

### Page 2: Scanner
- Wi‑Fi/BLE scan feed
- signal meter and channel info
- radar HUD
- nearby device list
- current observations

### Page 3: Intel
- vendor lookup
- risk and security scoring
- analytics summary
- session data
- suspicious network identification

### Page 4: Airspace Monitor
- passive observation of nearby airborne objects
- lock and track one object at a time
- time-based movement tracking
- confidence labels and event logs
- approach / recede / hover / circle detection

### Page 5: Tools
- beacon generation
- deauth generation
- BLE spam tools
- probe flood / defensive utilities
- explicit armed state management

### Page 6: Settings
- USB device configuration
- sensor toggles
- permissions
- logging and export
- theme and app branding

---

## Airspace Monitor Scope

The Airspace Monitor is a passive observation window designed for identifying and tracking objects in the local airspace without active interference.

### Primary goals
- detect a nearby airborne object
- lock onto one target
- observe it over time
- classify it by likely type
- note whether it is approaching, receding, holding, or circling
- record a clean event timeline for later review

### Labels
- likely drone
- likely aircraft
- likely balloon / lantern
- bird or flock
- unknown airborne light source

### Confidence levels
- High
- Medium
- Low

### Movement states
- approaching
- receding
- holding
- circling
- repeated pass
- unknown movement

---

## Phase 1 — App Shell and Page Split

### Goal
Break the app into a clean multi-page console.

### Add
- Dashboard page
- Scanner page
- Intel page
- Tools page
- Settings page
- Airspace Monitor placeholder page

### Do not add yet
- heavy tracking logic
- detailed anomaly models
- large export systems

### Outcome
The app feels like a real command console instead of a single overloaded dashboard.

---

## Phase 2 — Brand Identity and App Icon

### Goal
Give the app a real visual identity.

### Add
- custom launcher icon using the octopus artwork
- dark neon cyberpunk palette
- consistent button styling
- splash / intro branding
- title and page header polish

### Outcome
The app reads as a branded project, not a generic tool.

---

## Phase 3 — Airspace Monitor Foundation

### Goal
Create the first working Airspace Monitor section.

### Add
- Airspace Monitor page
- object list
- detection log
- lock target action
- object category labels
- confidence values
- passive observation state

### Include fields
- timestamp
- target name or label
- category
- confidence
- direction estimate
- light signature
- RF signature
- notes

### Outcome
A user can observe and classify a nearby object in the local airspace.

---

## Phase 4 — Active Lock-and-Track Logic

### Goal
Track one object over time.

### Add
- lock target button
- target history
- repeated sightings
- route memory
- movement trend detection
- approach / recede / hover / circle status

### Track these values
- object ID
- timestamp
- heading estimate
- light pattern
- signal pattern
- distance estimate
- movement status
- speed estimate
- path trend

### Outcome
The app can answer: is it moving closer, moving away, continuing the same route, or circling?

---

## Phase 5 — Signal / Anomaly Lab

### Goal
Add the weird event detection model that fits the nighttime sighting use case.

### Add
- raw anomaly log
- suspicious light pattern tracking
- recurring pass detection
- RF burst classification
- odd behavior notes
- local event timeline

### Outcome
The app can record suspicious or unusual objects that do not fit normal device scanning.

---

## Phase 6 — Tools Cleanup and Safety Boundaries

### Goal
Make the tools page clean and explicit.

### Add
- dedicated tool tabs
- armed / not armed states
- labeled active tool modes
- safety warnings where needed
- separation between passive monitoring and active tooling

### Outcome
The app stays organized and the tool system does not pollute the main scanner workflows.

---

## Phase 7 — Logging, Export, and Polish

### Goal
Make observation and tracking useful over time.

### Add
- CSV / JSON export
- saved sessions
- event timeline export
- note history
- retention controls
- clean dark-mode polish

### Outcome
A user can document and review nighttime sightings or recurring aerial patterns.

---

## Current Implementation Status

This section records what is present in the Android workspace today; roadmap goals above are not claims that every item is implemented.

### Complete or usable
- Separate Scanner, Airspace, Tools, Intel, and Export pages are present.
- The Android UI now uses an obsidian, teal, amethyst, and copper visual system with an adaptive octopus launcher icon and matching in-app emblem.
- The pet dock now uses four Copilot Image Creator pose frames of the blue-ringed octopus. Fixed-position crossfades use different frame order/speed for sleepy, happy, sassy, excited, and alert moods; poke/alert particles and ring-frame effects remain separate overlays.
- The companion's species styling is based on the supplied octopus emblem and blue-ringed octopus references: indigo mantle, warm copper accents, scattered blue rings, and brighter warning pulses during alert moods.
- The old procedural pet drawing has been removed. Motion is generated-image keyframing, not a skeletal 3D rig; independently generated poses can still vary in details.
- A local ComfyUI/RTX 4060 workflow is installed for additional image concepts and experiments. Copilot-generated pose frames are packaged under `android/app/src/main/res/drawable-nodpi/`.
- Airspace supports a manually created visual track and timestamped sightings with category, confidence, direction, movement observation, light pattern, notes, and observer GPS when available.
- Airspace RF lock records timestamped Wi-Fi/BLE RSSI samples, keeps a bounded recent window, reports relative signal-strength change, and can export the local event log.
- Visual and RF track records are stored locally and the active track selection is restored after app restart.
- Existing wireless tool controls remain in place; this work did not remove tools or send device commands.

### Deliberate limitations and remaining work
- RSSI trend is not distance, bearing, speed, aircraft identification, or proof that a signal source is airborne. The app labels only relative RF strength.
- Visual movement and category values are user-entered observations; the app does not yet analyze camera or microphone input, estimate altitude/speed, or automatically detect circling.
- The launcher icon and header now use a clean no-text octopus image generated with Copilot Image Creator from the supplied blue-ring/circuit direction; the animation remains independently rendered in the pet view.
- Blue-ringed octopus art references: [Australian Institute of Marine Science species notes](https://www.aims.gov.au/docs/projectnet/blue-ringed-octopus.html) and [Wikimedia Commons reference photographs](https://commons.wikimedia.org/wiki/Category:Hapalochlaena).
- The existing navigation does not yet have separate Dashboard or Settings tabs. The current header provides shared status and theme access.
- Retention controls, automated anomaly classification, camera/acoustic correlation, and physical USB/radio-device runtime testing remain future work. The Android emulator has been used to preview the UI.

---

## Implementation Sequence

The recommended order is:

1. Phase 1 — app shell and multi-page navigation
2. Phase 2 — branding and app icon
3. Phase 3 — Airspace Monitor foundation
4. Phase 4 — lock and track logic
5. Phase 5 — anomaly lab and signal correlation
6. Phase 6 — tools cleanup
7. Phase 7 — logs, export, and final polish

This order keeps the app stable while adding the more advanced features one layer at a time.

---

## Repo Strategy

### Keep the local full-feature branch as the source of truth
The current local workspace already contains the richer feature set and tool layer.

### Split the public GitHub repo by feature phase
- phase 1: app shell and navigation
- phase 2: branding and icon
- phase 3: Airspace Monitor
- phase 4: tracking logic
- phase 5: anomaly lab

This keeps GitHub clean while preserving the full local build roadmap.

---

## Recommended Naming for the New Section

- Airspace Monitor
- Airspace Watch
- Local Airspace
- Signal Lab

Best choice: Airspace Monitor

---

## Final Recommendation

Build this as one app with multiple pages, and treat the weird nighttime sighting scenario as a dedicated passive monitoring mode inside Wigglefish rather than a separate app.

This keeps the project unified while allowing the app to grow into a practical field-monitoring console.
