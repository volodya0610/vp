#!/usr/bin/env bash
set -euo pipefail

NAME="${1:-}"
if [[ -z "$NAME" ]]; then
  echo "Usage: wg-revoke.sh <name>" >&2
  exit 1
fi

if [[ ! "$NAME" =~ ^[a-zA-Z0-9_-]{1,64}$ ]]; then
  echo "Invalid name. Use letters, numbers, dashes, and underscores." >&2
  exit 1
fi

WG_INTERFACE="${WG_INTERFACE:-wg0}"
WG_CLIENT_DIR="${WG_CLIENT_DIR:-/etc/wireguard/clients}"
WG_CONF="${WG_CONF:-/etc/wireguard/${WG_INTERFACE}.conf}"

if [[ ! -f "$WG_CONF" ]]; then
  echo "Config not found: $WG_CONF" >&2
  exit 1
fi

TMP_CONF=$(mktemp)
trap 'rm -f "$TMP_CONF"' EXIT

awk -v name="$NAME" '
  $0 ~ "^# peer: " name "$" {skip=1; next}
  skip && $0 ~ "^\\[Peer\\]$" {next}
  skip && $0 ~ "^$" {skip=0; next}
  skip {next}
  {print}
' "$WG_CONF" > "$TMP_CONF"

mv "$TMP_CONF" "$WG_CONF"

if [[ -f "$WG_CLIENT_DIR/${NAME}.conf" ]]; then
  rm -f "$WG_CLIENT_DIR/${NAME}.conf"
fi

SYNC_TMP=$(mktemp)
trap 'rm -f "$SYNC_TMP"' EXIT
wg-quick strip "$WG_INTERFACE" > "$SYNC_TMP"
wg syncconf "$WG_INTERFACE" "$SYNC_TMP"


echo "Revoked peer ${NAME}."
