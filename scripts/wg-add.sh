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
WG_PEER_BASE="${WG_PEER_BASE:-}"
WG_CLIENT_ALLOWED_IPS="${WG_CLIENT_ALLOWED_IPS:-0.0.0.0/0, ::/0}"
WG_CLIENT_DNS="${WG_CLIENT_DNS:-}"

if [[ -z "$WG_ENDPOINT" || -z "$WG_SERVER_PUBLIC_KEY" || -z "$WG_CLIENT_ADDRESS" ]]; then
  echo "Missing required env: WG_ENDPOINT, WG_SERVER_PUBLIC_KEY, WG_CLIENT_ADDRESS" >&2
  exit 1
fi

if [[ -z "$WG_PEER_ALLOWED_IPS" ]]; then
  if [[ -z "$WG_PEER_BASE" ]]; then
    echo "Missing required env: WG_PEER_ALLOWED_IPS or WG_PEER_BASE" >&2
    exit 1
  fi
  WG_PEER_ALLOWED_IPS=$(awk -v base="$WG_PEER_BASE" '
    function ip_to_int(ip,    a) { split(ip, a, "."); return (a[1]*16777216)+(a[2]*65536)+(a[3]*256)+a[4]; }
    function int_to_ip(n,    a,b,c,d) { a=int(n/16777216); n%=16777216; b=int(n/65536); n%=65536; c=int(n/256); d=n%256; return a"."b"."c"."d; }
    BEGIN {
      split(base, parts, "/");
      if (parts[2] != 24) { print ""; exit 0; }
      net = ip_to_int(parts[1]);
      for (i = 2; i <= 254; i++) { used[int_to_ip(net + i)] = 0; }
    }
    /AllowedIPs =/ {
      gsub(/AllowedIPs = /, "", $0);
      split($0, items, ",");
      for (i in items) {
        gsub(/^[ \t]+|[ \t]+$/, "", items[i]);
        if (items[i] ~ /^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+\\/32$/) {
          split(items[i], ipcidr, "/");
          used[ipcidr[1]] = 1;
        }
      }
    }
    END {
      for (i = 2; i <= 254; i++) {
        ip = int_to_ip(net + i);
        if (!(ip in used) || used[ip] == 0) {
          print ip "/32";
          exit 0;
        }
      }
      print "";
    }
  ' "$WG_CONF")

  if [[ -z "$WG_PEER_ALLOWED_IPS" ]]; then
    echo "Failed to allocate peer IP from WG_PEER_BASE=$WG_PEER_BASE" >&2
    exit 1
  fi
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
