#!/bin/sh
# Push ATLAS Desk snapshots (and the HA token file, if staged) to the Pi.
# Uses the ha-slicer-ai deploy helper (SSH via tailnet proxy + core_ssh).
# The token file's contents are never printed.
set -e
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DECK=$(dirname "$HERE")
PI="$HOME/workspace/ha-slicer-ai/deploy/pi.py"
python3 "$PI" exec "mkdir -p /share/atlas-desk"
python3 "$PI" put "$DECK/snapshots" /share/atlas-desk
echo "snapshots pushed to Pi:/share/atlas-desk"
