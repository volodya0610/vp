#!/usr/bin/env bash
set -euo pipefail

NAME="${1:-}"
if [[ -z "$NAME" ]]; then
  echo "Usage: wg-add.sh <name>" >&2
  exit 1
fi

if [[ ! "$NAME" =~ ^[a-zA-Z0-9_-]{1,64}$ ]]; then
  echo "Invalid name. Use letters, numbers, dashes, and underscores." >&2
  exit 1
fi

WG_INTERFACE="${WG_INTERFACE:-wg0}"
WG_CLIENT_DIR="${WG_CLIENT_DIR:-/etc/wireguard/clients}"
WG_CONF="${WG_CONF:-/etc/wireguard/${WG_INTERFACE}.conf}"
WG_ENDPOINT="${WG_ENDPOINT:-}"
WG_SERVER_PUBLIC_KEY="${WG_SERVER_PUBLIC_KEY:-}"
WG_CLIENT_ADDRESS="${WG_CLIENT_ADDRESS:-}"
WG_PEER_ALLOWED_IPS="${WG_PEER_ALLOWED_IPS:-}"
WG_CLIENT_ALLOWED_IPS="${WG_CLIENT_ALLOWED_IPS:-0.0.0.0/0, ::/0}"
WG_CLIENT_DNS="${WG_CLIENT_DNS:-}"

if [[ -z "$WG_ENDPOINT" || -z "$WG_SERVER_PUBLIC_KEY" || -z "$WG_CLIENT_ADDRESS" || -z "$WG_PEER_ALLOWED_IPS" ]]; then
  echo "Missing required env: WG_ENDPOINT, WG_SERVER_PUBLIC_KEY, WG_CLIENT_ADDRESS, WG_PEER_ALLOWED_IPS" >&2
  exit 1
fi

umask 077
mkdir -p "$WG_CLIENT_DIR"
chmod 700 "$WG_CLIENT_DIR"

if [[ -f "$WG_CLIENT_DIR/${NAME}.conf" ]]; then
  echo "Client config already exists for $NAME" >&2
  exit 1
fi

CLIENT_PRIV_KEY=$(wg genkey)
CLIENT_PUB_KEY=$(printf '%s' "$CLIENT_PRIV_KEY" | wg pubkey)
CLIENT_PRESHARED_KEY=$(wg genpsk)

cat <<CONFIG > "$WG_CLIENT_DIR/${NAME}.conf"
[Interface]
PrivateKey = ${CLIENT_PRIV_KEY}
Address = ${WG_CLIENT_ADDRESS}
${WG_CLIENT_DNS:+DNS = ${WG_CLIENT_DNS}}

[Peer]
PublicKey = ${WG_SERVER_PUBLIC_KEY}
PresharedKey = ${CLIENT_PRESHARED_KEY}
AllowedIPs = ${WG_CLIENT_ALLOWED_IPS}
Endpoint = ${WG_ENDPOINT}
PersistentKeepalive = 25
CONFIG

chmod 600 "$WG_CLIENT_DIR/${NAME}.conf"

cat <<PEER >> "$WG_CONF"

# peer: ${NAME}
[Peer]
PublicKey = ${CLIENT_PUB_KEY}
PresharedKey = ${CLIENT_PRESHARED_KEY}
AllowedIPs = ${WG_PEER_ALLOWED_IPS}
PEER

wg syncconf "$WG_INTERFACE" <(wg-quick strip "$WG_INTERFACE")

echo "Added peer ${NAME}. Config saved to ${WG_CLIENT_DIR}/${NAME}.conf"
