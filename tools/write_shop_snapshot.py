#!/usr/bin/env python3
"""Write shop_snapshot.json for ATLAS Desk from a morning-brief numbers file.

Usage: write_shop_snapshot.py --input brief_numbers.json [--out PATH]

The input JSON carries the fields the etsy-morning-brief reads off the
Shop Manager dashboard (visits_7d, visits_yesterday, orders_yesterday,
revenue_yesterday, orders_7d, revenue_7d, active_listings,
ads_spend_30d, ads_orders_30d, ...). This script stamps generated_at,
marks the source, warns about missing expected keys, and writes the
snapshot. It never invents numbers.
"""
import argparse
import json
import os
import sys
from datetime import datetime, timezone

EXPECTED = ["visits_7d", "visits_yesterday", "orders_yesterday",
            "orders_7d", "revenue_7d", "active_listings", "ads_spend_30d"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "snapshots", "shop_snapshot.json"))
    args = ap.parse_args()

    with open(args.input) as f:
        data = json.load(f)
    missing = [k for k in EXPECTED if k not in data]
    if missing:
        print("warning: missing expected keys: " + ", ".join(missing), file=sys.stderr)
    data["generated_at"] = datetime.now(timezone.utc).isoformat()
    data["source"] = "etsy-morning-brief"
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as f:
        json.dump(data, f, indent=1)
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
