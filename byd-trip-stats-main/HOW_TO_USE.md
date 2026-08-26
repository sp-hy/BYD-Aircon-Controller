# How to Use BYD Trip Stats

A plain-language guide for using the app in its current **Phase 2** form, where telemetry is read directly from the car.

---

## Before You Start

You need:

1. **A BYD vehicle with DiLink 3.0** (tested on Seal; should work on Atto 3, Dolphin, Seal U)
2. **BYD Trip Stats installed on the DiLink unit**

You do **not** need Electro or an MQTT topic for normal operation.

---

## Installation

1. Download the latest `.apk` from the [Releases page](https://github.com/angoikon/byd-trip-stats/releases)
2. On the DiLink unit, enable installation from unknown sources when prompted
3. Grant the requested permissions
4. Open **BYD Trip Stats**

---

## First Launch

On first launch, select your exact car model.

This loads the correct:
- battery capacity
- WLTP range
- reference consumption
- recommended tyre pressures
- vehicle mass and drag area (used for energy breakdown)

You can change the selected car later by tapping the car name on the dashboard.

---

## Main Dashboard

The dashboard is the live vehicle screen.

### Top Bar

- **Car name** — tap to change model
- **History icon** — opens trip and charging history
- **Settings icon** — opens settings

### Energy Flow Area

With **Dashboard icons & animations** enabled (Settings → Preferences — the default):

- **Battery icon** — live SoC, animated while charging
- **Drivetrain graphic** — tyre pressure overview
- **Tyre badges** — green = healthy, orange/red = attention needed, grey = no data yet
- **Consumption chart thumbnail** — tap to expand daily / monthly / yearly consumption

With that preference turned **off**, these elements move into the top app bar (battery + consumption icons) and a dedicated **Tyres** card on the right-side stat panel — freeing the full height for the range projection chart and disabling animations to lower CPU load on older firmware.

### Range Projection

The main chart compares:

- **Dashed line** — the car's own BMS estimate
- **Solid line** — BYD Trip Stats projection from your actual driving

Before you move off, the chart shows a **SCANNING…** placeholder. Once you start driving, the projection leaves the catalog baseline within the first few hundred metres and keeps refining as the trip continues. A small badge shows which model tier is currently driving the projection — **Live trip**, **Speed bins**, or **Baseline**.

### Right-side Stat Cards

Typical cards include:

- **Estimated SoH / Battery health**
- **Temperature** (ambient + battery)
- **HV / 12V**
- **Front / Rear Motors** (RPM + power split)
- **Odometer**
- **Total Discharge**

Notes:
- `Estimated SoH` is derived from telemetry unless a direct in-car SoH source is confirmed
- Cabin temperature may remain blank if the car does not expose a trustworthy live reading

### Bottom Metrics

- **Power** — live motor output or regen
- **Speed**
- **Battery (SoC)**
- **Range (BMS)**
- **Distance** — current engine-on segment distance; when a trip survives a brief stop the cumulative trip distance is shown alongside it

---

## Trip Recording

### Automatic recording

Recommended mode.

When **Auto** is enabled:
- gear changes to **D** or **R** → trip starts
- gear returns to **P** → trip ends

Short engine-off breaks continue the same trip rather than ending it, as long as you return within the **Engine-off trip timeout** (Settings → Preferences — default 30 minutes). The current engine-on segment distance and the cumulative trip distance are then shown side by side in the dashboard's Distance metric.

### Manual recording

If Auto is turned off, a manual record button is shown. Use this only when you intentionally want to skip automatic detection.

---

## Trip History

Open from the history icon.

You can:
- browse all trips
- sort by six criteria (date, distance, energy, duration, efficiency, cost) with ascending/descending toggle
- filter by date range, distance, energy, duration, and efficiency
- open trip details
- long-press to select multiple trips for comparison or export

### Trip Detail

Each trip detail screen includes:

**Overview** — key stats: distance, duration, energy, consumption, SoC change, max speed, max power, max regen, regeneration efficiency, cost.

**Charts** — per-trip timeseries:
- speed
- power
- SoC
- altitude
- motor RPM (front / rear / both, depending on drivetrain)
- instantaneous consumption
- battery voltage (HV bus + cell min/max band)
- tyre pressures (all four wheels)
- drive mode and regen mode timeline

**Heatmaps** — 14 correlation dimensions including power vs speed, consumption vs speed, regen vs speed, RPM vs speed, battery temp vs power, SoC vs consumption, altitude vs consumption, tyre pressure vs consumption, and more. AWD cars gain an additional front vs rear RPM torque-split heatmap.

**Route** — trip path on OpenStreetMap with energy event markers. Works fully offline once the map tiles are cached.

**Analysis** — computed insights and the **Energy Breakdown** (see below).

### Energy Breakdown

Found in the Analysis tab of each trip detail.

The breakdown splits total energy consumed into four physics-based components:

| Component | What it represents |
|---|---|
| **Rolling resistance** | Energy lost to tyre friction over the trip distance. Proportional to mass × distance. |
| **Aerodynamic drag** | Energy lost to air resistance. Proportional to CdA × speed² × distance. |
| **Net gradient** | Net energy cost of elevation changes. Climb costs energy; descent recovers some via regen. Shown as climb / descent split. |
| **Auxiliary losses** | Remainder after the three modelled forces. Covers 12 V system, HVAC, GPS noise, and model residual. |

All components are **scaled proportionally to always sum to the measured consumed energy**, so the numbers are always internally consistent.

Notes:
- Requires vehicle mass and CdA from the car catalog (all supported models have these)
- HVAC is not separately quantified — the DiLink firmware does not expose a reliable real-time HVAC power signal, so it appears inside auxiliary losses
- The percentage shown next to each component is its share of total consumed energy

### Trip Export

From a trip's detail screen, open **Export** for three formats:

- **CSV** — every recorded telemetry field per data point, for spreadsheets
- **JSON** — the same data in structured form
- **HTML viewer** — a single self-contained `.html` file with the trip data embedded inside it. Double-click it on any computer and every chart renders in the browser — no separate JSON to manage, no viewer to host, nothing to install

Each format can be saved to the car's Download folder or sent straight to your configured Telegram bot. The Download and Telegram groups in the Export dialog are collapsible — tap a section header to expand it.

### Trip Comparison

Long-press a trip → select 2–3 trips → tap the compare icon.

Opens a side-by-side view with:
- **Summary tab** — metric table with winner highlighted per row
- **Charts tab** — overlaid speed, power, consumption, SoC, and elevation normalised to 0–100% trip distance
- **Routes tab** — all routes on a shared map with per-trip visibility toggle

---

## Charging History

Charging sessions are recorded separately from trips and cover two scenarios:

- **Car ON** — real-time session with full Power / SoC / Voltage / Temperature charts
- **Car OFF** — SoC delta reconstruction on next wake-up covers overnight, timed, and remote charging when DiLink kills the app mid-charge

### Charging Detail

Four tabs:
- **Overview** — summary: kWh added, SoC start/end, peak/avg kW, duration
- **Power + SoC** — dual-axis chart; switch between Time and SoC x-axis (SoC mode is useful for DC taper analysis)
- **Voltage** — HV bus voltage over time
- **Temperature** — battery temperature rise during the session

### Charging Costs

Trip details can account for:
- **Fixed home tariff** — set once in Settings → Preferences → Electricity tariff
- **Custom DC charging cost override** — enter the real amount paid for a public charging stop on any individual trip

---

## Consumption Charts

Tap the small chart thumbnail on the dashboard to expand:

- **Daily consumption**
- **Monthly consumption**
- **Yearly consumption**

The chart shows your selected-duration average alongside your selected car's reference consumption. Outlier points outside a plausible consumption range are filtered to keep the chart readable.

---

## Analytics

### Battery Degradation

Tap the **Battery Health** stat card on the dashboard.

Shows SoH per trip over time with a least-squares trend line, a projected trajectory, and an estimated year when the pack will reach 80% — the typical EV warranty threshold.

### Seasonal Analysis

Accessible from the ☀️ icon in the Trip History toolbar.

Groups all trips by meteorological season (Spring / Summer / Autumn / Winter) and shows average consumption per season as a colour-coded bar chart. Automatically highlights a winter efficiency penalty when both winter and summer data are present.

### Trip Goals & Personal Bests

Accessible from the 🏆 icon in the Trip History toolbar.

- **Personal bests** — lowest efficiency, longest trip, longest consecutive daily driving streak
- **Goals** — set a consumption target and/or a monthly distance goal; animated progress bars update in real time

---

## Settings

Open from the gear icon. Three top-level tabs:

### Data

- Backup and restore (local filesystem or Telegram bot)
- Update tools
- Reset tools
- Vehicle snapshot / telemetry diagnostics

### Preferences

- Electricity tariff (price/kWh + currency symbol)
- Goals & Personal Bests
- Units and app behaviour preferences
- Engine-off trip timeout — how long a trip stays open across an engine-off break before it closes (default 30 minutes)
- Minimum trip distance — trips shorter than this are discarded automatically when they end (set to 0 to keep every trip)
- Dashboard icons & animations — switches between the animated icon layout and the compact top-bar layout; off also lowers CPU usage on older firmware

### Connections

Optional integrations for forwarding live telemetry to external tools. **All are disabled by default.**

| Integration | What is sent | Where it goes |
|---|---|---|
| **ABRP** | Live telemetry snapshot (SoC, speed, power, GPS) | ABRP servers via Link Generic API using your user token |
| **MQTT** | Full telemetry JSON at a configurable interval | An external broker you specify (host, port, topic, credentials) |

Both have a **Test** action and show a last-sync timestamp. Disabling either has no effect on local trip recording or the dashboard.

### About & FAQ

- App version and build info
- In-app FAQ covering common DiLink behaviour, autostart survival, and charging-session caveats
- Troubleshooting notes

---

## Backup & Restore

### Local backup

`Settings → Data → Open Backup & Restore`

Backups are stored in `Download/BydTripStats/` on the car's internal storage.

### Telegram backup

Connect a private Telegram bot for remote personal backups. This is optional.

Setup: provide your bot token and chat ID in Settings → Data. You can trigger a backup manually or set a schedule (daily / weekly / monthly).

**Note:** When enabled, your encrypted database file is sent to Telegram's servers as a file attachment to your bot. If you prefer to keep data entirely off third-party servers, use local filesystem backup instead.

---

## External Data: What Leaves the Car

By default, **nothing leaves the car**. The following are opt-in only:

- **Telegram backup** — encrypted DB file sent to your own private Telegram bot when you configure it and trigger a backup
- **MQTT** — live telemetry JSON published to a broker you specify, at the interval you set
- **ABRP** — live telemetry snapshot sent to ABRP using the token you provide
- **Update checks** — the app can check for new APK releases; this is the only network call made without explicit user setup

---

## Autostart / Survival

DiLink may kill background apps aggressively. To improve survival:

1. Open the car's **Disable Autostart** app
2. Find **BYD Trip Stats**
3. Toggle its entry **OFF** (so autostart is effectively enabled)
4. Reboot the car UI
5. Open BYD Trip Stats again

**Important:** this setting often resets after app updates. Re-check it after every install.

The app uses a foreground telemetry service, wake lock, Wi-Fi lock, boot receiver, and a watchdog worker — but survival still depends on the OEM system behaviour.

---

## What Is Direct and What Is Derived

### Read directly from the car

- SoC
- Charging power
- Speed
- Gear
- Odometer
- HV / 12V voltage
- Motor RPM (front / rear)
- Cell voltage min/max
- Tyre pressures
- Battery temperatures
- Most trip and charging telemetry

### Calculated by the app

- Estimated SoH
- Range projection
- Trip costs
- Energy breakdown (rolling, aero, gradient, auxiliary)
- Battery temperature midpoint when only cell min/max exists
- Analytics, averages, goals, seasonal summary

---

## Known Caveats

- **Estimated SoH** is not yet confirmed as a direct BMS SoH source on all firmware builds
- **Cabin temperature** may be blank — the app avoids showing HVAC setpoints as if they were real cabin air readings
- **HVAC power** is not quantified separately in the energy breakdown — DiLink does not broadcast reliable real-time compressor power
- **Overnight charging capture** depends on whether DiLink keeps the app alive during sleep

---

## Tips

- Re-check Autostart after every app update
- The range projection leaves the catalog baseline within the first few hundred metres — the model-tier badge tells you when it's running on real trip data (Speed bins / Live trip) rather than the WLTP baseline
- Use the **SoC x-axis** mode in the charging Power + SoC chart for DC taper analysis
- Use **Heatmaps** to spot correlations — e.g. speed vs consumption to find your car's efficiency sweet spot
- Use the **Seasonal Analysis** view after your first winter to see the real cold-weather efficiency penalty
- Use **Trip Comparison** on your regular commute routes to track consistency over time
