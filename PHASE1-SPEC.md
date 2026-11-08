# ATLAS Desk — Phase 1 Spec

_Written 2026-11-07 by Helios. Status: approved by Jon 2026-11-07 ("start phase 1 now"); build in progress._
_Name: "ATLAS Desk" — Jon's naming, 2026-11-07. (Project codename during the pitch was "Helios Deck"; the product is ATLAS Desk, the desk face of the Atlas ecosystem.)_

## What Phase 1 is

One glanceable screen — the "desk face" — that runs as a web app (PWA) on Jon's
Tab S7+ at his desk and in the Pixel's browser on the go. It answers, at a
glance and without opening five apps: **How's the print? How's the house?
What did the shop do? What's next?**

Phase 1 is deliberately **read-only**. No controls, no switches, no sends.
It proves the whole Deck idea on one screen; everything interactive is a
later phase.

## Verified ground truth (checked 2026-11-07, 19:15 ET)

- Home Assistant on the Pi (tailnet 100.68.172.113) is **up** — API probe
  returns 401 (Core up, demanding auth), per the standing probe rule.
- Athena is **down right now by Jon's own hand** (game night). This is the
  design lesson baked into this spec: **Phase 1 hosts on the Pi and must
  never depend on Athena being up.**
- Printer state already lives in HA via the Nino integration (Kobra S1,
  `s1_` entity prefix) and the HA Slicer AI add-on (v0.5.0) runs on the Pi —
  so printer data is available from the Pi alone.
- Shop numbers already get pulled daily by the `etsy-morning-brief` job —
  Phase 1 reuses that pipeline instead of building a new Etsy path.

## Architecture

- **Host:** the HA Pi (always on, already runs add-ons). A small backend
  service ("deckd") + a static PWA front end, served on the tailnet only.
  No public exposure, no cloud dependency.
- **Access:** tailnet-only. Tab and Pixel reach it over Tailscale; nothing
  is reachable from the open internet.
- **Secrets:** the HA long-lived token lives server-side in deckd's config
  on the Pi. It never ships to the browser.
- **Failure behaviour:** if a data source is unreachable, its card shows
  "stale since HH:MM" with the last known values — never a blank screen,
  never a fake zero. (This week proved why: Athena goes down on purpose.)

## The screen — four cards

1. **Printer** — Kobra S1 state (idle/printing/paused), current job name,
   progress %, time remaining, from HA `s1_` entities + slicer queue count
   from the HA Slicer AI add-on. If the printer is off/unavailable, the card
   says so plainly.
2. **House** — inside temperature (Wyze thermostat via HA), who's home
   (presence state), and a one-line "all quiet" summary of door/camera
   state. Status only — no camera feeds in Phase 1, no controls ever in
   this phase.
3. **Shop** — PrintablePastimesCo snapshot: visits (7d), orders, revenue,
   active listings, ads spend (30d), from a `shop_snapshot.json` written by
   the existing morning-brief pipeline, refreshed morning + evening. Card
   shows the snapshot's timestamp so staleness is honest. Etsy numbers do
   not move minute-to-minute; a snapshot is the truthful cadence.
4. **Next up** — next 3 events from the shared Google Calendar.
   _Build-time verification step:_ confirm HA exposes calendar entities for
   the shared calendar; if not, deckd falls back to a snapshot written by
   Helios's briefing jobs. Jenna's CCA teaching events display as-is and
   are never altered — this app only ever reads.

Layout: 2×2 grid on the Tab (big type, readable across the desk, dark
theme); single column stack on the Pixel. Kiosk-friendly on the Tab:
fullscreen, no chrome, dims to a clock at night.

## Explicitly out of Phase 1

- Any device control (hard rule stands: Helios never flips switches; a
  future control phase would be Jon's separate call, designed then).
- Native Android app, hold-to-talk voice, notifications/push (Phase 2;
  push needs the ntfy-vs-HA-app decision first).
- Laptop/Claude Code build babysitting (Phase 3).
- Game-night mode (later; needs Athena by definition).
- Camera feeds, TTS, Kyle-anything. One screen, four cards.

## Build order

1. deckd skeleton on the Pi serving the PWA shell + health endpoint.
2. HA adapter (printer + house cards live).
3. Shop snapshot writer added to the morning-brief pipeline; shop card.
4. Calendar source verification, then the Next-up card.
5. Tab kiosk pass (fullscreen, night dim) + Pixel layout pass.

Build loop per house standard: Helios specs → Claude Code builds →
Helios verifies independently against live HA/shop data → Jon sees it on
the actual Tab before it's called done.

## Done when (Jon's own check)

- Tab on the desk shows the printer's real progress % matching HA.
- House card temperature matches the thermostat.
- Shop card numbers match that morning's brief, timestamp visible.
- Next-up shows the real next calendar event.
- Pull Athena's plug (or leave it off): the Deck doesn't care.
- Kill the internet uplink: everything except the shop snapshot's
  freshness still works over the tailnet.

## Open items for Jon (none block the build)

- Push notifications: deferred to Phase 2 by design — no decision needed now.
- Whether the Deck eventually earns the rotating short-term slot after the
  CX-5 buddy: Jon's call after Phase 1 is on the Tab.

## Cost/effort shape

No new hardware, no subscriptions. Build draws on the shared Claude Pro
allowance across a few sessions, announced before each, same as ClearDash.
Everything lives in Jon's workspace/GitHub — if Helios vanished tomorrow,
deckd and the PWA keep running on the Pi without me (independence rule: pass).
