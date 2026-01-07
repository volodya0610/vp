# WireGuard Admin Telegram Bot

This project provides a Telegram admin bot for managing WireGuard peers via local scripts.

## Requirements

- Java 17+
- WireGuard tools (`wg`, `wg-quick`)
- A Telegram bot token

## Configuration (env vars)

| Variable | Description |
| --- | --- |
| `BOT_TOKEN` | Telegram bot token. |
| `ADMIN_IDS` | Comma-separated list of admin Telegram IDs (only these users can run commands). |
| `WG_INTERFACE` | WireGuard interface name (default: `wg0`). |
| `WG_SCRIPTS_DIR` | Path to scripts directory (default: `scripts`). |
| `WG_CLIENT_DIR` | Where client configs are stored (default: `/etc/wireguard/clients`). |
| `WG_ENDPOINT` | Public endpoint for the server (required for `/add`). |
| `WG_SERVER_PUBLIC_KEY` | Server public key (required for `/add`). |
| `WG_CLIENT_ADDRESS` | Client address (required for `/add`). |
| `WG_PEER_ALLOWED_IPS` | Allowed IPs for the peer (required for `/add`). |
| `WG_CLIENT_DNS` | Optional DNS servers for client config. |
| `WG_CONF` | Override server config path (default: `/etc/wireguard/<WG_INTERFACE>.conf`). |

## Commands

- `/status` → runs `scripts/wg-status.sh`
- `/list` → runs `scripts/wg-list.sh`
- `/add <name>` → runs `scripts/wg-add.sh <name>`
- `/revoke <name>` → runs `scripts/wg-revoke.sh <name>`
- `/config <name>` → sends the generated client config to the admin

## Scripts

The scripts use `WG_INTERFACE` and `WG_CLIENT_DIR` from the environment. Ensure the scripts are executable and that the bot user has permission to run them.

### Security notes

- Client configs are stored in `WG_CLIENT_DIR` with `0700` on the directory and `0600` on files.
- The bot only serves `/config` to whitelisted admin IDs.
- Avoid running the bot with unnecessary privileges; use sudoers rules to grant script access if needed.

## Running locally

```bash
export BOT_TOKEN="your_token"
export ADMIN_IDS="123456789,987654321"
export WG_INTERFACE="wg0"
export WG_ENDPOINT="vpn.example.com:51820"
export WG_SERVER_PUBLIC_KEY="<server_public_key>"
export WG_CLIENT_ADDRESS="10.0.0.2/32"
export WG_PEER_ALLOWED_IPS="10.0.0.2/32"

./gradlew run
```
