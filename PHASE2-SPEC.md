# ATLAS Desk — Phase 2 Spec: the native app

_Written 2026-11-08 by Helios, at Jon's direction ("Spec Phase 2: the native ATLAS Desk app"). Status: awaiting Jon's approve/kill before any build._
_Phase 1 (live) is the backend + web face: deckd on the HA Pi serving http://100.68.172.113:8098. Phase 2 makes ATLAS Desk what Jon pictured from the start: a real app, in his GitHub repo, built by GitHub._

## What Phase 2 is

A native Android app named **ATLAS Desk**, living in the existing repo
**Jondude1/ATLAS-desk**, built and released by GitHub Actions exactly like
ClearDash. One APK, two homes:

- **Galaxy Tab S7+** — the desk. Landscape, fullscreen kiosk, always-on while plugged in.
- **Pixel 9 Pro XL** — the pocket version. Portrait, single column.

The app is a client of the Phase 1 backend. deckd keeps doing what it
already does (polls HA every 20s, holds the token server-side, serves
`/api/deck`). **The app contains no secrets and no HA write path — it is
read-only by construction, same as Phase 1.**

## Stack (all house-proven from ClearDash)

- Kotlin + Jetpack Compose, single module. Minimal dependencies.
- Gradle wrapper pinned; GitHub Actions is the real build (local SDK-34
  compile is a smoke check only — the sandbox lesson from ClearDash).
- Signing: a NEW keystore for ATLAS Desk, generated once, stored only in
  GitHub Secrets (KEYSTORE_BASE64 / passwords / alias) + Jon's backup
  copy. Never committed — the ClearDash keystore incident is the standing
  lesson, and this repo is public.
- Package: `com.jondude1.atlasdesk` (proposal; builder confirms against
  Jon's other apps' conventions).

## Design research — before any UI code (Jon's direction, 2026-11-08)

Jon: "do some research on what the most aesthetic and coolest UI and GUI
is on Pinterest and Reddit." So Phase 2 starts with a design phase, not
code:

1. Research Pinterest and Reddit (r/homeassistant dashboards, homelab /
   ambient display / tablet kiosk communities) for the most aesthetic
   smart-home desk-display UIs people actually run and praise.
2. Distill into **2–3 named design directions** with a small moodboard
   each (palette, typography, card treatment, dark-mode character) —
   grounded in real examples found, with links.
3. **Jon picks the direction** (his approve/kill gate) before UI code is
   written. The picked direction becomes the app's design language for
   both Tab and Pixel layouts.

Known access caveat: Pinterest has blocked this VM's network before
(Oct 2026); if it does again, research proceeds on Reddit + web sources
and Jon can be handed specific Pinterest boards to eyeball on his phone.

## The app

1. **Four cards, native** — Printer, House, Shop, Next Up, fed by
   deckd's `/api/deck`. Same information as the web face, rendered
   natively: big glanceable type on the Tab, single stack on the Pixel.
   Poll deckd every ~30s + pull-to-refresh.
2. **Desk mode (Tab)** — immersive fullscreen, keep-screen-on while
   charging, night dim 22:00–07:00 ET (mirrors the web face), brighten
   on touch.
3. **Honest connectivity** — primary endpoint is the Pi's tailnet
   address (100.68.172.113:8098); fallback to the Pi's LAN address when
   on home Wi-Fi (verify the LAN address answers :8098 during build).
   If neither answers: cards show last values with a visible
   "stale since HH:MM" — never blanks, never fake zeros. A small
   connection dot shows live/stale at a glance.
4. **Self-update** — the ClearDash pattern: the app checks the repo's
   latest GitHub Release and offers the new APK in-app. No Play Store,
   no account, Jon owns distribution end to end.
5. **Identity** — app name "ATLAS Desk" everywhere; icon derived from
   the Phase 1 icon motif.

## Backend chores folded into Phase 2

Phase 1 left two snapshots seeded but not self-refreshing; the app makes
them prominent, so Phase 2 finishes them:

- **Shop snapshot** refresh wired to the morning-brief pipeline
  (writer already exists: `tools/write_shop_snapshot.py`).
- **Calendar snapshot** on a light recurring refresh
  (`tools/write_calendar_snapshot.py`; HA exposes zero calendar
  entities — verified in Phase 1 — so the snapshot route stands).

## Build & release flow

- Push to `main` → the (separately approved) build-check runs.
- Tag `v*` → Android workflow builds a signed release APK and attaches
  it to a GitHub Release. Jon installs from the Release page (or the
  in-app updater after first install).
- Build-time check: pushing workflow files via the API needs the
  token's `workflow` scope — if the push is rejected, Jon adds the one
  workflow file via the GitHub web UI once; everything after that is
  normal commits.
- Note: Claude Code's CLI login is currently expired (found Nov 7).
  Until Jon re-logs it in, builds run builder-direct with Helios
  verifying — the route that shipped Phase 1.

## Explicitly out of Phase 2

- Voice / hold-to-talk (Phase 3 candidate).
- Push notifications — the ntfy-vs-HA-app decision is still open and
  still Jon's; not smuggled in here.
- Any control of anything. Read-only stands.
- Laptop/Claude Code build babysitting, game-night mode (later phases).
- Home-screen widgets (possible 2.1, not in this spec).

## Done when (Jon's own check)

- A `v0.2.0` tag produces a green Actions run and a release APK in
  Jondude1/ATLAS-desk.
- Jon installs it on the Tab from the Release page; the four cards show
  the same live numbers as the web face at the same moment.
- Kill the Tab's Wi-Fi: stale banners appear, numbers stay, nothing zeroes.
- Same APK on the Pixel shows the portrait layout and live data on the tailnet.
- Repo tree contains no keystore, no token, no snapshot JSONs (scanned before release).

## Cost/effort shape

No hardware, no subscriptions, no Play developer account. Build draws on
the shared Claude Pro allowance across a few sessions, announced before
each. Independence rule: pass — repo, keystore backup, and releases are
Jon's; the app talks only to his Pi.

## Design decision (2026-11-08, after the research pass)

Jon delegated the pick; Helios chose **Direction A "Soft Midnight"**
(Catppuccin Mocha) from
`~/workspace/research_notes/atlas-desk-ui-design-research-20261108-1329/report.md`,
with one borrow from Direction C: the Printer and Shop hero values
render in a large tabular monospace. Applied in the first pass:
crust `#11111B` background, mantle `#181825` flat cards at 22dp
radius, one fixed pastel accent per card (Printer mauve, House
teal, Shop green, Next Up blue), Mushroom-style icon-in-tinted-
circle + hero + one secondary line, status as rounded pills. Red
is reserved for real alerts only.
