#!/bin/sh
# ATLAS Desk backend. Read-only: deckd only ever issues GETs to Home Assistant.
exec python3 /app/backend/deckd.py
