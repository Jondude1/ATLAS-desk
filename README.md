# ATLAS Desk

A glanceable "desk face" for Jon's Tab S7+ (and the Pixel's browser):
one screen that answers *How's the print? How's the house? What did the
shop do? What's next?* Phase 1 is strictly **read-only** — no controls,
no switches, no sends.

Live at **http://100.68.172.113:8098** (Pi, tailnet/LAN only — the Pi is
behind home NAT, nothing is exposed to the open internet).

## The four cards

- **Printer** — Kobra S1 job state, job name, progress, time remaining,
  nozzle/bed temps from Home Assistant's Nino (`s1_`) entities, plus the
  HA Slicer AI queue depth counted from `/share/ha-slicer/jobs/*/job.json`.
- **House** — thermostat current/target temperature and mode (Wyze via
  HA), Jon/Jenna presence, and a motion summary line from `*_motion`
  binary sensors ("All quiet" when nothing is moving).
- **Shop** — PrintablePastimesCo numbers from `shop_snapshot.json`
  (visits, orders, revenue, active listings, ads spend), timestamped.
- **Next Up** — next events from `calendar_snapshot.json` (the shared
  Google Calendar; HA exposes no calendar entities for it, so the deck
  reads a snapshot — see Refreshing the snapshots).

If a source fails, its card keeps the last good values and shows
**"Stale since HH:MM"** — never a fabricated zero.

## Architecture

- `addon/atlas_desk/` — a Home Assistant local add-on ("ATLAS Desk",
  slug `local_atlas_desk`) running **deckd**, a Python-stdlib-only HTTP
  service (no dependencies). It polls `GET /api/states` every 20 s and
  serves `/api/deck`, `/healthz`, and the static PWA on port 8098.
- Auth to HA goes through the Supervisor proxy (`http://supervisor/core`)
  with the Supervisor-injected token — the same pattern as HA Slicer AI's
  printer link. The add-on declares `homeassistant_api: true`. As a
  fallback, deckd can use Jon's long-lived token from
  `/share/atlas-desk/ha_token.txt` on the Pi. **Tokens live only on the
  Pi, server-side; they are never in this repo and never sent to the
  browser.** deckd's only outbound call is that one read-only GET, and
  the base URL is auto-probed at startup from a candidate list.
- The PWA (`addon/atlas_desk/frontend/`) is a dark, big-type 2×2 grid
  (single column on phones), auto-refreshes every 20 s, requests a wake
  lock, tap-header for fullscreen, and dims at night (22:00–07:00 ET).
  Manifest name/short_name: **ATLAS Desk**.

## Refreshing the snapshots

Snapshots live on the Pi in `/share/atlas-desk/` and are staged locally
in `snapshots/` (gitignored — they contain personal data).

- Calendar: `python3 tools/write_calendar_snapshot.py` pulls the next
  3 days from the Google Calendar connector and writes
  `snapshots/calendar_snapshot.json` (next 5 upcoming events, ended
  events dropped).
- Shop: `python3 tools/write_shop_snapshot.py --input <brief numbers
  JSON>` stamps and writes `snapshots/shop_snapshot.json` from the
  numbers the etsy-morning-brief reads off the Shop Manager dashboard.
  (Seeded 2026-11-07 from that morning's brief: 69 visits/7d, 211
  listings, $17.55 ads/30d.)
- Push both to the Pi: `sh tools/push_snapshots.sh` (SSH/SFTP via
  `~/workspace/ha-slicer-ai/deploy/pi.py`). If a `ha_token.txt` is staged
  in `snapshots/`, it is transported too; its contents are never printed
  and it is gitignored.

## Deploy / update

```sh
python3 ~/workspace/ha-slicer-ai/deploy/pi.py put addon/atlas_desk /local_apps/atlas_desk
# on the Pi (via pi.py exec):
ha store reload && ha addons reload
ha addons install local_atlas_desk   # first time
# for later changes: bump `version` in config.yaml first — the
# Supervisor only treats a higher version as an update — then:
ha addons update local_atlas_desk
ha addons logs local_atlas_desk
```

## Verification (2026-11-07, live)

- deckd on the Pi polls HA via supervisor/core; `/healthz` reports
  `ha_ok: true`.
- Printer card matched a simultaneous direct HA read exactly:
  state `finished`, job "Robot Phone Stand 2_plate_1(1)", 100%,
  nozzle 75.2 °C, bed 71.6 °C, slicer queue 0 waiting / 0 running.
- House card matched: 67 °F current / 67 °F target, heat + idle,
  Jon home, Jenna home.
- Shop card shows the seeded brief snapshot with its timestamp;
  Next Up shows "Sunday School and Church", Sun Nov 8, 9:00 AM.
- Stale path tested locally: unreachable HA → cards report stale with
  null values, no fabricated numbers.
- Still Jon's to eyeball: the Deck on the actual Tab S7+ (kiosk
  fullscreen + night dim are implemented but not yet seen on-device).

## Repo discipline

This repo is public. `.gitignore` excludes token/secret/config files and
snapshot JSONs; every push is content-scanned for credential-shaped
strings before it leaves the machine. The HA token must never be
committed — same rule as the ClearDash keystore.
