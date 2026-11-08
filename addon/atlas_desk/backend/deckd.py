#!/usr/bin/env python3
"""ATLAS Desk backend (deckd).

Read-only by design: the ONLY outbound request this service ever makes is
GET {ha_url}/api/states against Home Assistant, plus reading snapshot
JSON files from /share/atlas-desk. It never calls an HA service and never
writes to HA. The HA long-lived token is read from a file on the Pi
(/share/atlas-desk/ha_token.txt) or the add-on options; it is never
logged and never sent to the browser.

Stale behaviour: if an HA poll fails, the last good card data is served
with stale=true and stale_since=<last successful poll>. Values are never
fabricated — an entity that is missing/unavailable yields null.
"""
import glob
import json
import os
import threading
import time
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VERSION = "0.1.0"
PORT = int(os.environ.get("DECK_PORT", "8098"))
FRONTEND_DIR = os.environ.get("DECK_FRONTEND", "/app/frontend")
SHARE_DIR = os.environ.get("DECK_SHARE_DIR", "/share/atlas-desk")
SLICER_JOBS_DIR = os.environ.get("DECK_SLICER_JOBS", "/share/ha-slicer/jobs")
OPTIONS_FILE = os.environ.get("DECK_OPTIONS_FILE", "/data/options.json")
POLL_SECONDS = int(os.environ.get("DECK_POLL_SECONDS", "20"))

BAD_STATES = ("unavailable", "unknown", "", None)


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def log(msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


def load_options():
    try:
        with open(OPTIONS_FILE) as f:
            return json.load(f)
    except Exception:
        return {}


OPTIONS = load_options()
HA_URL = (os.environ.get("DECK_HA_URL") or OPTIONS.get("ha_url")
          or "http://homeassistant:8123").rstrip("/")


def resolve_token():
    env = os.environ.get("HA_TOKEN", "").strip()
    if env:
        return env
    try:
        with open(os.path.join(SHARE_DIR, "ha_token.txt")) as f:
            tok = f.read().strip()
            if tok:
                return tok
    except Exception:
        pass
    return (OPTIONS.get("ha_token") or "").strip()


def to_float(v):
    if v in BAD_STATES:
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def to_int(v):
    f = to_float(v)
    return int(f) if f is not None else None


class DeckState:
    """Holds the latest derived printer/house cards + HA health."""

    def __init__(self):
        self.lock = threading.Lock()
        self.printer = None
        self.house = None
        self.ha_ok = False
        self.ha_last_ok = None

    # ---------- HA polling ----------
    def poll_once(self, token):
        req = urllib.request.Request(
            HA_URL + "/api/states",
            headers={"Authorization": "Bearer " + token},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=8) as resp:
            states_list = json.loads(resp.read().decode("utf-8"))
        states = {s["entity_id"]: s for s in states_list}
        printer = self._printer_card(states)
        house = self._house_card(states)
        with self.lock:
            self.printer = printer
            self.house = house
            self.ha_ok = True
            self.ha_last_ok = now_iso()

    def mark_failed(self):
        with self.lock:
            self.ha_ok = False

    # ---------- card derivation ----------
    @staticmethod
    def _state(states, entity_id):
        s = states.get(entity_id)
        if not s:
            return None
        v = s.get("state")
        return None if v in BAD_STATES else v

    @staticmethod
    def _attrs(states, entity_id):
        s = states.get(entity_id)
        return (s or {}).get("attributes") or {}

    def _queue_counts(self):
        queued = 0
        active = 0
        found = False
        for path in glob.glob(os.path.join(SLICER_JOBS_DIR, "*", "job.json")):
            try:
                with open(path) as f:
                    state = json.load(f).get("state")
            except Exception:
                continue
            found = True
            if state == "queued":
                queued += 1
            elif state in ("leased", "running"):
                active += 1
        if not found:
            return None, None
        return queued, active

    def _printer_card(self, states):
        job_state = self._state(states, "sensor.s1_job_state")
        queued, active = self._queue_counts()
        return {
            "source_ok": True,
            "stale": False,
            "stale_since": None,
            "available": job_state is not None,
            "state": job_state,
            "job_name": self._state(states, "sensor.s1_job_name"),
            "progress": to_float(self._state(states, "sensor.s1_job_progress")),
            "time_remaining_s": to_int(self._state(states, "sensor.s1_job_time_remaining")),
            "time_elapsed_s": to_int(self._state(states, "sensor.s1_job_time_elapsed")),
            "eta": self._state(states, "sensor.s1_job_eta"),
            "nozzle_c": to_float(self._state(states, "sensor.s1_nozzle_temperature")),
            "bed_c": to_float(self._state(states, "sensor.s1_hotbed_temperature")),
            "queued": queued,
            "active_jobs": active,
        }

    def _house_card(self, states):
        thermo_attrs = self._attrs(states, "climate.thermostat")
        motion = []
        for entity_id, s in states.items():
            if (entity_id.startswith("binary_sensor.")
                    and entity_id.endswith("_motion")
                    and s.get("state") == "on"):
                name = entity_id[len("binary_sensor."):-len("_motion")]
                motion.append(name.replace("_", " ").title())
        motion.sort()
        if motion:
            summary = "Motion: " + ", ".join(motion)
        else:
            summary = "All quiet — no motion anywhere"
        return {
            "source_ok": True,
            "stale": False,
            "stale_since": None,
            "temp_f": to_float(thermo_attrs.get("current_temperature")),
            "target_f": to_float(thermo_attrs.get("temperature")),
            "mode": self._state(states, "climate.thermostat"),
            "hvac_action": thermo_attrs.get("hvac_action"),
            "jon": self._state(states, "person.jon"),
            "jenna": self._state(states, "person.jenna"),
            "motion": motion,
            "summary": summary,
        }

    # ---------- snapshots ----------
    @staticmethod
    def _read_snapshot(filename):
        try:
            with open(os.path.join(SHARE_DIR, filename)) as f:
                return json.load(f)
        except Exception:
            return None

    # ---------- payload ----------
    def payload(self):
        with self.lock:
            printer = dict(self.printer) if self.printer else None
            house = dict(self.house) if self.house else None
            ha_ok = self.ha_ok
            ha_last_ok = self.ha_last_ok
        for card in (printer, house):
            if card is None:
                continue
            if not ha_ok:
                card["source_ok"] = False
                card["stale"] = True
                card["stale_since"] = ha_last_ok
        if printer is None:
            printer = {"source_ok": False, "stale": True,
                       "stale_since": None, "available": False,
                       "state": None, "job_name": None, "progress": None,
                       "time_remaining_s": None, "time_elapsed_s": None,
                       "eta": None, "nozzle_c": None, "bed_c": None,
                       "queued": None, "active_jobs": None}
        if house is None:
            house = {"source_ok": False, "stale": True,
                     "stale_since": None, "temp_f": None, "target_f": None,
                     "mode": None, "hvac_action": None, "jon": None,
                     "jenna": None, "motion": [], "summary": "No house data yet"}
        shop_snap = self._read_snapshot("shop_snapshot.json")
        if shop_snap:
            shop = dict(shop_snap)
            shop["status"] = "ok"
            shop["snapshot_at"] = shop_snap.get("generated_at")
        else:
            shop = {"status": "no_snapshot", "snapshot_at": None}
        cal_snap = self._read_snapshot("calendar_snapshot.json")
        if cal_snap:
            calendar = {"status": "ok",
                        "snapshot_at": cal_snap.get("generated_at"),
                        "events": cal_snap.get("events", [])}
        else:
            calendar = {"status": "no_snapshot", "snapshot_at": None, "events": []}
        return {
            "generated_at": now_iso(),
            "printer": printer,
            "house": house,
            "shop": shop,
            "calendar": calendar,
        }

    def health(self):
        with self.lock:
            ha_ok, ha_last_ok = self.ha_ok, self.ha_last_ok
        return {
            "ok": True,
            "version": VERSION,
            "ha_ok": ha_ok,
            "ha_last_ok": ha_last_ok,
            "shop_snapshot": os.path.exists(os.path.join(SHARE_DIR, "shop_snapshot.json")),
            "calendar_snapshot": os.path.exists(os.path.join(SHARE_DIR, "calendar_snapshot.json")),
        }


STATE = DeckState()

CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".webmanifest": "application/manifest+json",
    ".svg": "image/svg+xml",
    ".json": "application/json",
    ".png": "image/png",
    ".ico": "image/x-icon",
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _send(self, code, body, content_type):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(data)

    def _send_json(self, code, obj):
        self._send(code, json.dumps(obj), "application/json")

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/healthz":
            return self._send_json(200, STATE.health())
        if path == "/api/deck":
            return self._send_json(200, STATE.payload())
        if path.startswith("/api/"):
            return self._send_json(404, {"error": "not found"})
        if path == "/":
            path = "/index.html"
        rel = path.lstrip("/")
        full = os.path.normpath(os.path.join(FRONTEND_DIR, rel))
        if not full.startswith(os.path.normpath(FRONTEND_DIR)) or not os.path.isfile(full):
            return self._send_json(404, {"error": "not found"})
        ext = os.path.splitext(full)[1].lower()
        with open(full, "rb") as f:
            data = f.read()
        self._send(200, data, CONTENT_TYPES.get(ext, "application/octet-stream"))


def poll_loop():
    token = resolve_token()
    if not token:
        log("WARNING: no HA token found (env, token file, or options) — HA source disabled")
        return
    log(f"polling HA at {HA_URL} every {POLL_SECONDS}s (read-only GET /api/states)")
    while True:
        try:
            STATE.poll_once(token)
        except Exception as e:
            STATE.mark_failed()
            log(f"HA poll failed ({type(e).__name__}); serving last good data as stale")
        time.sleep(POLL_SECONDS)


def main():
    threading.Thread(target=poll_loop, daemon=True).start()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    log(f"ATLAS Desk deckd v{VERSION} listening on 0.0.0.0:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
