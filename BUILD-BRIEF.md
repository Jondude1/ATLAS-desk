# ATLAS Desk — Phase 1 build brief for Claude Code

Implement the full Phase 1 build described here, in this directory
(~/workspace/helios-deck). The product name is **ATLAS Desk** — use it in
every user-visible string (add-on name, PWA manifest name/short_name,
page <title>, on-screen header).

## Hard rules

- The backend must be **read-only against Home Assistant**: only HTTP GET
  to the HA REST API. No POST/PUT/DELETE anywhere in the code. No service
  calls of any kind.
- Python **standard library only** for the backend (no pip installs).
- Never print or log the HA token. It is read at runtime from a file.
- Dark theme, big glanceable type. One screen, four cards.

## Layout to create

```
addon/atlas_desk/
  config.yaml
  build.yaml
  Dockerfile
  run.sh
  backend/deckd.py
  frontend/index.html
  frontend/style.css
  frontend/app.js
  frontend/manifest.webmanifest
  frontend/sw.js
  frontend/icon.svg
tools/
  write_calendar_snapshot.py
  write_shop_snapshot.py
  push_snapshots.sh
snapshots/            (snapshot JSON files land here; add .gitkeep)
README.md
.gitignore            (__pycache__, snapshots/*.local.json)
```

## Add-on packaging (mirror the HA Slicer AI local add-on pattern)

config.yaml:
- name: "ATLAS Desk", slug: atlas_desk, version: "0.1.0"
- description: "Glanceable desk face — printer, house, shop, next up. Read-only."
- arch: [aarch64, amd64], init: false, startup: application, boot: auto
- ports: 8098/tcp: 8098, ports_description for it
- map: share:ro
- options: ha_url: "http://homeassistant:8123"
- schema: ha_url: str?

build.yaml: build_from amd64+aarch64 = docker.io/library/python:3.12-slim

Dockerfile: FROM ${BUILD_FROM} (ARG BUILD_FROM=python:3.12-slim),
ENV PYTHONUNBUFFERED=1, WORKDIR /app, COPY backend /app/backend,
COPY frontend /app/frontend, COPY run.sh /run.sh, chmod +x, EXPOSE 8098,
CMD ["/run.sh"]

run.sh: `#!/bin/sh` → exec python3 /app/backend/deckd.py

## backend/deckd.py (stdlib only)

- ThreadingHTTPServer on 0.0.0.0:$DECK_PORT (default 8098).
- Config: read /data/options.json if present → ha_url (default
  "http://homeassistant:8123"). Token resolution order:
  1. env HA_TOKEN
  2. contents of /share/atlas-desk/ha_token.txt (stripped)
  3. options.json "ha_token"
  Never log it. If no token: HA source reports status "no_token".
- A background thread polls HA every 20 s:
  GET {ha_url}/api/states with header Authorization: Bearer <token>,
  timeout 8 s. Keep the parsed states dict + last-success timestamp.
  On failure keep the previous data and mark the HA source stale
  (stale_since = last success time). This is the ONLY outbound call
  deckd makes.
- Slicer queue (read on each poll from the filesystem, share is mapped):
  glob /share/ha-slicer/jobs/*/job.json, parse "state":
  queued_count = state == "queued"; active_count = state in
  ("leased", "running"). Tolerate missing dir (counts = null).
- GET /api/deck returns JSON:
  {
    "generated_at": iso8601,
    "printer": {
      "source_ok": bool, "stale": bool, "stale_since": iso|null,
      "available": bool,           // key job entities present & not unavailable
      "state": "finished"|...,     // from sensor.s1_job_state, null if unavailable
      "job_name": str|null,        // sensor.s1_job_name
      "progress": number|null,     // sensor.s1_job_progress
      "time_remaining_s": int|null,// sensor.s1_job_time_remaining
      "eta": str|null,             // sensor.s1_job_eta
      "nozzle_c": number|null,     // sensor.s1_nozzle_temperature
      "bed_c": number|null,        // sensor.s1_hotbed_temperature
      "queued": int|null, "active_jobs": int|null
    },
    "house": {
      "source_ok": bool, "stale": bool, "stale_since": iso|null,
      "temp_f": number|null,       // climate.thermostat attr current_temperature
      "target_f": number|null,     // attr temperature
      "mode": str|null,            // climate.thermostat state
      "hvac_action": str|null,
      "jon": "home"|"not_home"|null,    // person.jon
      "jenna": "home"|"not_home"|null,  // person.jenna
      "motion": [friendly names],  // binary_sensor.*_motion with state "on"
      "summary": "All quiet — no motion anywhere" | "Motion: Living Room, Family Room"
    },
    "shop": { "status": "ok"|"no_snapshot", ...fields from shop_snapshot.json verbatim, "snapshot_at": its generated_at },
    "calendar": { "status": "ok"|"no_snapshot", "snapshot_at": ..., "events": [ {summary, start, end, location, all_day} ] }
  }
  Numeric parsing: HA states are strings; convert with float()/int()
  where sensible; "unavailable"/"unknown"/missing → null.
  Motion friendly name: binary_sensor.living_room_motion → "Living Room".
- Snapshot files are read from /share/atlas-desk/shop_snapshot.json and
  /share/atlas-desk/calendar_snapshot.json on each /api/deck request.
  Missing/corrupt → status "no_snapshot", never fabricated numbers.
- GET /healthz → {"ok": true, "version": "0.1.0", "ha_ok": bool,
  "ha_last_ok": iso|null, "shop_snapshot": bool, "calendar_snapshot": bool}
- Static: serve /app/frontend at /; also serve /manifest.webmanifest,
  /sw.js, /icon.svg at root. Correct content types (.webmanifest →
  application/manifest+json, .js → text/javascript, .svg → image/svg+xml).
  404 JSON for unknown /api/* paths.
- Log concise lines to stdout with timestamps. No token in logs, ever.

## Frontend (PWA)

- index.html: <title>ATLAS Desk</title>; header with icon.svg +
  "ATLAS Desk" wordmark + a live clock (id=clock); a <main class="grid">
  with four <section class="card"> blocks: Printer, House, Shop, Next Up,
  each with a .big element, a .lines container and a .foot status line.
  Link manifest + stylesheet + app.js. Meta viewport + theme-color #0b0e13.
- app.js:
  - fetch('/api/deck') every 20 s, render all cards. On fetch error show
    "Deck unreachable" in a header status pill, keep last render.
  - Printer: big = state text (e.g. "Finished", "Printing 62%") — if
    progress is present and state is printing, show "Printing · 62%".
    Lines: job name; progress bar (div.bar > div.fill width %);
    "≈42m left" from time_remaining_s (format h/m); nozzle/bed temps;
    "Slicer queue: N waiting" when queued != null. If available=false:
    big = "Printer unavailable", lines explain plainly.
  - House: big = "68°" (temp_f). Lines: "Set 67° · Heat · Idle";
    "Jon home · Jenna away" style presence line; summary line.
  - Shop: big = "69 visits · 7 days". Lines: "Yesterday: 13 visits ·
    0 orders"; "Orders (7d): 0 · $0"; "211 active listings";
    "Ads (30d): $17.55 spent · 0 orders". Render generically from the
    snapshot fields present (visits_7d, visits_yesterday, orders_yesterday,
    revenue_yesterday, orders_7d, revenue_7d, active_listings,
    ads_spend_30d, ads_orders_30d) — skip lines whose fields are missing.
    Foot: "Snapshot <time>".
  - Calendar: lines = up to 3 events: "Sun 9:00 AM — Sunday School and
    Church" (+ location if present). All-day: "Mon — Pay Credit Card".
    Format times in America/New_York.
  - Card foot for HA-backed cards: "Updated HH:MM" or, when stale,
    amber "Stale since HH:MM". For snapshot cards: "Snapshot HH:MM" /
    "No snapshot yet".
  - Clock: every second, America/New_York time, "7:24:05 PM" + short date.
  - Night mode: if ET hour >= 22 or < 7, add class "night" to <body>.
  - Wake lock: try navigator.wakeLock.request('screen') on load and on
    visibilitychange; swallow errors.
  - Fullscreen: clicking the header toggles fullscreen on documentElement.
- style.css: background #0b0e13; cards #151a22, 1px border #232b38,
  radius 18px, padding 22px; font stack system; .big font-size
  clamp(2.4rem, 6vw, 4.2rem) weight 700; grid: 2 columns at
  min-width 700px, 1 column below; header flex; .bar track #232b38,
  .fill #4da3ff; .stale color #f5b544; body.night { filter: brightness(.5) }
  and body.night #clock { font-size: 3rem }.
- manifest.webmanifest: name "ATLAS Desk", short_name "ATLAS Desk",
  start_url "/", scope "/", display "standalone",
  background_color "#0b0e13", theme_color "#0b0e13",
  icons: [{src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any"}]
- icon.svg: 512x512, dark rounded square (#151a22, rx 96), a bold
  light "A" formed by simple polygons in #4da3ff + a small orbit dot
  circle in #f5b544. No external assets.
- sw.js: cache name "atlas-desk-v1"; on install cache the shell
  ["/", "/style.css", "/app.js", "/manifest.webmanifest", "/icon.svg"];
  fetch handler: /api/ → network only; everything else cache-first
  with network fallback.

## tools/

- write_calendar_snapshot.py (python3, stdlib): runs
  /opt/hatch/bin/hatch_gws_cli calendar +agenda --days 3 --format json
  (path overridable via env GWS_CLI). Parses the JSON list; for each event
  keep {summary, start, end (or end_exclusive), location, all_day}.
  Drop events whose end is in the past; sort by start; keep first 5.
  Writes {"generated_at": now-iso, "events": [...]} to --out
  (default snapshots/calendar_snapshot.json). Print a one-line summary.
- write_shop_snapshot.py (python3, stdlib): --input <json file> (fields
  produced by the morning brief), stamps/overrides generated_at with now,
  adds "source": "etsy-morning-brief", writes to --out
  (default snapshots/shop_snapshot.json). Warn (stderr) on missing
  expected keys but still write.
- push_snapshots.sh (sh): mkdir -p /share/atlas-desk on the Pi via
  ~/workspace/ha-slicer-ai/deploy/pi.py exec, then pi.py put the local
  snapshots/ dir to /share/atlas-desk. If a local file
  snapshots/ha_token.txt exists, put it too (it is how the token gets to
  the Pi; the script must not print its contents).

## README.md

Explain: what ATLAS Desk is, architecture (deckd add-on on the Pi,
read-only HA polling, snapshot files in /share/atlas-desk), how snapshots
get refreshed (calendar writer from the Google Calendar connector; shop
writer fed by the morning brief), deploy steps (pi.py put addon dir to
/local_apps/atlas_desk, ha apps reload/install/start), security notes
(token lives only in /share/atlas-desk/ha_token.txt on the Pi, never in
the repo or browser), and the Phase 1 verification checklist from the
spec (printer % matches HA, temp matches thermostat, shop numbers match
the brief, next-up shows the real next event).

Write clean, complete files — no TODOs or placeholders in code.
