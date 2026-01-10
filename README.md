# Telegram-бот для администрирования WireGuard

Этот проект предоставляет Telegram-бота для администрирования WireGuard через локальные скрипты.

## Требования

- Java 17+
- Утилиты WireGuard (`wg`, `wg-quick`)
- Токен Telegram-бота

## Конфигурация (переменные окружения)

| Переменная | Описание |
| --- | --- |
| `BOT_TOKEN` | Токен Telegram-бота. |
| `ADMIN_IDS` | Список Telegram ID администраторов через запятую (только эти пользователи могут выполнять команды). |
| `WG_INTERFACE` | Имя интерфейса WireGuard (по умолчанию: `wg0`). |
| `WG_SCRIPTS_DIR` | Путь к директории со скриптами (по умолчанию: `scripts`). |
| `WG_CLIENT_DIR` | Где хранить клиентские конфиги (по умолчанию: `/etc/wireguard/clients`). |
| `WG_ENDPOINT` | Публичный endpoint сервера (обязателен для `/add`). |
| `WG_SERVER_PUBLIC_KEY` | Публичный ключ сервера (обязателен для `/add`). |
| `WG_CLIENT_ADDRESS` | Адрес клиента (обязателен для `/add`). |
| `WG_PEER_ALLOWED_IPS` | Allowed IPs для peer (обязателен для `/add`). |
| `WG_PEER_BASE` | Базовая подсеть для автогенерации `WG_PEER_ALLOWED_IPS` (например, `10.7.0.0/24`). |
| `WG_CLIENT_ALLOWED_IPS` | Allowed IPs в клиентском конфиге (по умолчанию: `0.0.0.0/0, ::/0`). |
| `WG_CLIENT_DNS` | DNS-серверы для клиентского конфига (опционально). |
| `WG_CONF` | Путь к конфигу сервера (по умолчанию: `/etc/wireguard/<WG_INTERFACE>.conf`). |
| `WG_CONFIG` | Путь к YAML-файлу конфигурации (по умолчанию: `config.yaml` или `config.yml` в корне проекта). |

## Команды

- `/status` → запускает `scripts/wg-status.sh`
- `/list` → запускает `scripts/wg-list.sh`
- `/add <name>` → запускает `scripts/wg-add.sh <name>`
- `/revoke <name>` → запускает `scripts/wg-revoke.sh <name>`
- `/config <name>` → отправляет сгенерированный клиентский конфиг администратору

## Скрипты

Скрипты используют `WG_INTERFACE` и `WG_CLIENT_DIR` из окружения. Убедитесь, что скрипты исполняемые и у пользователя бота есть права на их запуск.
Если директория `WG_SCRIPTS_DIR` не существует, бот извлечет скрипты из ресурсов JAR во временную директорию и будет использовать их. Если скрипты есть, но без права исполнения, бот попытается выставить `chmod +x` при первом запуске.

### Заметки по безопасности

- Клиентские конфиги сохраняются в `WG_CLIENT_DIR` с правами `0700` на директорию и `0600` на файлы.
- Бот отправляет `/config` только пользователям из белого списка администраторов.
- Не запускайте бота с лишними привилегиями; при необходимости настройте `sudoers` для запуска скриптов.

## Локальный запуск

Можно хранить настройки в `config.yaml` (или `config.yml`) в корне проекта. Переменные окружения имеют приоритет над YAML.

Пример `config.yaml`:

```yaml
bot_token: "your_token"
admin_ids:
  - 123456789
  - 987654321
wg_interface: "wg0"
wg_scripts_dir: "scripts"
wg_client_dir: "/etc/wireguard/clients"
wg_endpoint: "vpn.example.com:51820"
wg_server_public_key: "<server_public_key>"
wg_client_address: "10.0.0.2/32"
wg_peer_allowed_ips: "10.0.0.2/32"
wg_peer_base: "10.0.0.0/24"
wg_client_allowed_ips: "0.0.0.0/0, ::/0"
wg_client_dns: "1.1.1.1,8.8.8.8"
```

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

## Сборка и запуск на сервере

Соберите fat jar и запустите его так:

```bash
./gradlew fatJar
java -jar build/libs/wireguard-telegram-admin-1.0.0-all.jar
```

При необходимости можно указать путь к конфигу:

```bash
WG_CONFIG=/etc/wg-bot/config.yaml java -jar build/libs/wireguard-telegram-admin-1.0.0-all.jar
```

## Запуск в IntelliJ IDEA

1. Откройте проект через **File → Open** и выберите `build.gradle`.
2. В настройках Gradle выберите встроенный Gradle из IDEA или установленный в системе.
3. Создайте конфигурацию **Application** с main-классом `com.example.wg.Main` и задайте переменные окружения из раздела выше.
