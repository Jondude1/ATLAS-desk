# ATLAS Desk — Android app (Phase 2)

Native Android client for the Phase 1 backend (`deckd` on the Pi).
One APK for the Galaxy Tab S7+ (desk, landscape) and the Pixel 9 Pro XL
(portrait). Read-only by construction: the app contains no Home
Assistant token and only ever sends `GET http://<pi>:8098/api/deck`.

- Package: `com.jondude1.atlasdesk`
- Endpoints: tailnet `100.68.172.113:8098` first, home LAN
  `192.168.4.23:8098` as fallback (both verified answering).
- Data: polls every ~30s, pull-to-refresh, honest stale display
  (last values + "stale since", never fake zeros).

## Build & release

GitHub Actions (`.github/workflows/android.yml`) is the real build:

- Push to `main` → `assembleDebug` smoke build.
- Tag `v*` → signed `assembleRelease`; the APK is attached to a
  GitHub Release. Prerelease tags (with a `-`, e.g. `v0.2.0-alpha.1`)
  are marked prerelease.

Signing: the keystore lives only in the `KEYSTORE_BASE64` GitHub
secret (plus Jon's custody backup in `~/workspace/your_files/
atlas-desk-signing/`); CI restores it per build and passes passwords
via `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` secrets.
**Never commit a keystore** — the repo `.gitignore` blocks
`android/keystore/` and `*.jks`.

Local smoke build (SDK 34 machine): `cd android && ./gradlew
:app:assembleDebug` with `ANDROID_HOME` set — CI compiles against
SDK 35; a local compile is a syntax/smoke check only.

## Status

First pass: scaffold, data layer, four cards, desk-mode basics
(fullscreen, keep-on-while-charging, night dim 22:00–07:00 ET).
Theme: Direction A "Soft Midnight" (Catppuccin Mocha), picked
2026-11-08 — flat mantle cards at 22dp on crust, one pastel accent
per card, pill chips, monospace hero values on the Printer and
Shop cards (the Direction C borrow). The in-app self-update
checker (spec item 4) is a later pass.
