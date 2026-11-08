#!/usr/bin/env python3
"""Write calendar_snapshot.json for ATLAS Desk from the Google Calendar
connector (`hatch_gws_cli calendar +agenda`). Read-only.

Output: {"generated_at": iso, "events": [{summary, start, end, location, all_day}]}
Events that already ended are dropped; the next 5 upcoming are kept.
"""
import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

GWS = os.environ.get("GWS_CLI", "/opt/hatch/bin/hatch_gws_cli")
ET = ZoneInfo("America/New_York")


def parse_dt(value, all_day):
    """Always returns an aware datetime (or None) so sorting never mixes
    naive and aware values. All-day dates anchor to midnight ET."""
    if value is None:
        return None
    try:
        if all_day:
            return datetime.fromisoformat(value).replace(
                hour=0, minute=0, second=0, tzinfo=ET)
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return dt if dt.tzinfo else dt.replace(tzinfo=ET)
    except ValueError:
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "snapshots", "calendar_snapshot.json"))
    ap.add_argument("--days", type=int, default=3)
    args = ap.parse_args()

    out = subprocess.run(
        [GWS, "calendar", "+agenda", "--days", str(args.days), "--format", "json"],
        capture_output=True, text=True, timeout=120)
    if out.returncode != 0:
        print("agenda failed:", out.stderr[:300], file=sys.stderr)
        return 1
    raw = json.loads(out.stdout)
    events_in = raw if isinstance(raw, list) else raw.get("events", [])

    now = datetime.now(timezone.utc)
    kept = []
    for ev in events_in:
        all_day = bool(ev.get("all_day"))
        start = ev.get("start")
        end = ev.get("end") or ev.get("end_exclusive")
        end_dt = parse_dt(end, all_day)
        if end_dt is not None:
            if end_dt.tzinfo is None:
                end_dt = end_dt.replace(tzinfo=timezone.utc)
            if end_dt < now:
                continue
        kept.append({
            "summary": ev.get("summary", "(untitled)"),
            "start": start,
            "end": end,
            "location": ev.get("location", ""),
            "all_day": all_day,
            "_sort": parse_dt(start, all_day) or now,
        })
    kept.sort(key=lambda e: e["_sort"])
    events = [{k: v for k, v in e.items() if k != "_sort"} for e in kept[:5]]

    snap = {"generated_at": now.isoformat(), "events": events}
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as f:
        json.dump(snap, f, indent=1)
    print(f"wrote {args.out}: {len(events)} upcoming events")
    return 0


if __name__ == "__main__":
    sys.exit(main())
