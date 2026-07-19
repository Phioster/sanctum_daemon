# Sanctumd

> **Sanctumd** — a unified, native Android dashboard for a self-hosted homelab.
> Jellyfin administration **and** the *arr / download stack in one app, under one
> consistent matrix-terminal theme. *The daemon that guards your homelab.*

Think **nzb360 + Jellyfin management** rebuilt as a single Jetpack Compose app:
add your services once, and everything — status, queues, media, requests,
users, logs — shows up in one terminal-styled UI. Every action hits each
service's own API directly; there is no backend and no account.

<!--
SCREENSHOTS — placeholders. Drop censored screenshots (no tokens / server
addresses / real usernames) into docs/screenshots/ and swap the paths below.

![Dashboard](docs/screenshots/dashboard.png)
![Jellyfin admin](docs/screenshots/jellyfin.png)
![Live push](docs/screenshots/livepush.png)
-->

> 📸 *Screenshots coming soon.*

## Features

### Media & requests
- **Jellyfin admin** — server dashboard (info, scheduled tasks, activity log,
  restart), **user management** (create, edit policy & per-library access, reset
  password, delete), **media browsing** (Continue Watching, Recently Added,
  library → series → season → episode, item detail with cast), **Now Playing**
  (active sessions with pause / stop / send-message), plugins, libraries and
  server logs.
- **Radarr · Sonarr · Lidarr** — Library / Missing / Cutoff / Queue / History,
  add with quality & metadata profiles, **interactive search** (release picker
  with custom-formats, score & rejections) → grab, manual import, per-episode
  monitoring, queue status filter, delete, System & health.
- **Jellyseerr** — Requests, Issues, Discover & Watchlist; approve / decline,
  per-season TV requests, issue threads, media detail with cast, users & stats.
- **Prowlarr** — indexers, search, history, system & tasks, send-to-arr.
- **NZBGet** — queue & history, pause / resume, edit, add-URL, server details.

### The dashboard
- **Fully customizable home** — editable category tabs plus a full-page Services
  drawer. Per-service cards (queues, missing, coming-soon, recently-added,
  poster rows, statistics, watch leaderboard, …).
- **Per-card styling** — title & entry count, accent colour, header icon, poster
  size, opt-in fan-art Ken-Burns background, and flat / solid / glass card
  themes. Plus Section headers, Quick-Button cards and a **Universal Calendar**.
- **Global search** across every configured service in parallel.
- **Home-screen widgets** (Glance) — shortcuts, status, quick actions, an
  upcoming-calendar agenda, and 1×1 stat tiles.

### Notifications
- **Live push** — subscribes directly to a topic on your **ntfy** server and
  turns every message into a local notification, instantly. Reuses the webhook
  topic your services already publish to; supports a secondary backup topic.
- **Polling fallback** — an on-device worker that raises notifications for new
  media, imports, requests and health issues even when live push is offline.

### Privacy & portability
- Service credentials stored **AES-256-GCM encrypted** in the Android Keystore.
- Optional **biometric app-lock** (with device-credential fallback).
- **Config export / import** — a password-protected (PBKDF2 → AES-GCM) portable
  file for moving your whole setup to a new device.
- Secrets excluded from cloud backup; **Cloudflare Access** custom headers
  supported for tunnelled services.

## Tech

Kotlin · Jetpack Compose · Material 3 · Retrofit / OkHttp · Coil · Glance
widgets · WorkManager · androidx.biometric · AES-256-GCM (Android Keystore).

- **min SDK 26** (Android 8.0) · **target SDK 35** (Android 15)
- No local Android SDK required to contribute — builds run in CI.

## Build

Every push triggers the **Build APK** GitHub Actions workflow; download the
`sanctumd-debug-apk` artifact from the run and install it:

```sh
adb install -r sanctumd-debug.apk
```

To build locally instead:

```sh
gradle assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Status

Feature-complete for the current service set and in daily use; heading toward a
**1.0** release (release-signing, a full code-review pass, then publishing).
See [CHANGELOG.md](CHANGELOG.md) for the milestone history.

## License

[GPL-3.0](LICENSE) — Sanctumd is free software.
