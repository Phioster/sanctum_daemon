# Changelog

All notable changes to **Sanctumd**, grouped by milestone. Newest first.
Versioning is `major.minor.patch`; the app is in daily use, now at 1.0.

## 1.1.0 — 2026-07-21
A structural round: no new features, but the codebase is now one you can change
safely.

- **Renamed to Sanctumd throughout** — code, package and theme moved to
  `org.phioster.sanctumd`. The installed package id, the DataStore names and the
  Keystore alias deliberately keep their historic name; changing those would
  orphan existing installs' data. Placed home-screen widgets have to be added
  again once after this update.
- **Split the two files everything lived in.** `MainActivity.kt` (7030 lines) and
  the network layer (2827 lines) became 30 files along the boundaries that were
  already there: one package per screen, one file per service API. The largest
  file left is under 1000 lines; MainActivity is 350.
- **A test layer, and CI gates on it.** JVM unit tests cover the config export
  (round trip, wrong password, legacy format), the ntfy stream parser, the API
  response mapping and auth headers against recorded responses, and the dashboard
  edit rules. A red test now blocks the APK.
- **Safe mode.** Every call that changes something on a server — deletes, grabs,
  imports, request decisions, restarts, shortcuts — runs through one guard. Turn
  safe mode on (settings → security) and those calls are refused before they
  reach the network, which makes testing against a live homelab a mechanism
  rather than a matter of care.
- **One service registry.** Logos and quick actions are described once per
  service instead of in five places, with a test that fails when a new service
  type is left undescribed. See `docs/adding-a-service.md`.

## 1.0.0 — 2026-07-20
First tagged release. Sanctumd was in daily use throughout the 0.x line; 1.0
marks the reviewed, release-signed milestone.

- **Security & correctness review** — no critical findings. Applied hardening:
  atomic service-status updates; PBKDF2 raised to 210k iterations with a
  **versioned** portable-export format (older backups still import); the
  config store now surfaces a recovery banner for unreadable/corrupt data
  instead of a silently-empty list; per-card dashboard cache eviction.
- **Release signing** — reproducible signed APKs built in CI (keystore from
  encrypted secrets, never in the repo).
- Repo prepared for release: accurate README, hardened `.gitignore`
  (release-keystore & secret patterns), this changelog, cleaned git history,
  `SECURITY.md` + issue templates.

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
