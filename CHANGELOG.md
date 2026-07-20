# Changelog

All notable changes to **Sanctumd**, grouped by milestone. Newest first.
Versioning is `major.minor.patch`; the app is in daily use and heading to 1.0.

## Unreleased — toward 1.0
- **Security & correctness review** — no critical findings. Applied hardening:
  atomic service-status updates; PBKDF2 raised to 210k iterations with a
  **versioned** portable-export format (older backups still import); the
  config store now surfaces a recovery banner for unreadable/corrupt data
  instead of a silently-empty list; per-card dashboard cache eviction.
- Repo prepared for release: accurate README, hardened `.gitignore`
  (release-keystore & secret patterns), this changelog, cleaned git history,
  `SECURITY.md` + issue templates.
- Next: release signing, then publishing as **1.0**.

## Notifications & live push (v0.47 – v0.83)
- **Live push** via a direct **ntfy** topic subscription — a special-use
  foreground service streams messages into local notifications instantly,
  reusing the topic your services already webhook to. Per-server connection
  merging, offline catch-up, and a masked topic in the notification title.
- **Polling fallback** worker (WorkManager) for new media / imports / requests /
  health, with per-item baselining so there is no backlog spam.
- Card data cached per-tab so switching tabs no longer refetches; pull-to-refresh
  is the refresh trigger.

## Customizable dashboard & widgets (v0.36 – v0.71)
- **Widget home** — editable category tabs + a full-page Services drawer, with a
  broad set of per-service cards (queues, missing, coming-soon, recently-added,
  poster rows, statistics, watch leaderboard).
- **Per-card styling** — title, entry count, accent colour, header icon, poster
  size, fan-art Ken-Burns backgrounds, and flat / solid / glass themes; plus
  Section and Quick-Button cards and a Universal Calendar.
- **Home-screen widgets** (Glance) — shortcuts, status, quick actions, an
  upcoming-calendar agenda, and 1×1 stat tiles.
- Global search across all services; dashboard auto-refresh & reorder;
  finger-following swipe navigation.

## Security, portability & onboarding (v0.55 – v0.75)
- Service credentials **AES-256-GCM encrypted** (Android Keystore); secrets
  excluded from cloud backup.
- **Biometric app-lock** (device-credential fallback).
- **Config export / import** — password-protected (PBKDF2 → AES-GCM) portable file.
- First-run onboarding flow; optional 18+ content filter; open-in-app buttons.

## Service integrations (v0.18 – v0.52)
- **Jellyfin admin** — dashboard, scheduled tasks, activity log, restart, user
  management with per-library access, media browsing with cast, Now Playing
  controls, plugins, libraries, server logs.
- **Radarr / Sonarr / Lidarr** — full list tabs, add, interactive search &
  release picker, grab, manual import, per-episode monitoring, System & health.
- **Seerr** (Jellyseerr) — requests, issues, discover, watchlist, per-season requests,
  media detail, users & stats.
- **Prowlarr** — indexers, search, history, tasks, send-to-arr.
- **NZBGet** — queue, history, pause/resume, edit, add-URL.

## Foundation (v0.1 – v0.17)
- Project scaffold, CI (GitHub Actions debug APK), service config model with
  Cloudflare Access custom headers, matrix-terminal theme, first integrations.
