# Changelog

All notable changes to **Sanctumd**, grouped by milestone. Newest first.
Versioning is `major.minor.patch`; the app is in daily use, now at 1.57.

## Where a title streams, and Jellyfin 12 (v1.55 – v1.56)
- **Streaming availability** in the Seerr, Radarr and Sonarr detail screens —
  the services a film or series runs on, as a row of provider logos. The data
  comes from TMDB through the Seerr you already configured; the app carries no
  TMDB key of its own. Availability is always per country, so the region is
  shown and selectable, and a region TMDB knows nothing about drops the row
  entirely rather than showing an empty one. Cast and availability now arrive in
  a single Seerr call instead of two.
- **Media segments** — intro and outro markers are queried the way Jellyfin 12
  expects, with the repeated-parameter form, and the remaining Jellyfin calls
  were moved to the procedures valid from server 12 on.

## Ambient glow (v1.53 – v1.57)
- **Colour bleeds out of the picture into the letterbox bars**, YouTube-style,
  following the scene. libmpv renders into a SurfaceView, so the app never sees
  the frames: the colours come from Jellyfin's **trickplay** tiles instead, one
  small sheet covering minutes of film.
- Items without trickplay **fall back to the artwork's colours**; items with
  neither keep their black bars.
- An **ambient line in the info panel** says what the glow is currently doing,
  a **switch** in the player settings turns it off, and the seekbar was pared
  back to match.
- The wash is **averaged down to ten pixels** before it is stretched out, so it
  reads as fields of colour rather than a recognisable picture. That downscale
  is the whole blur — there is no blur pass to pay for on old hardware.

## Playback statistics (v1.50 – v1.52)
- **A statistics screen for playback** — what was watched, by whom, and what it
  cost the server, in the house style, with readable labels and bar fills that
  are actually visible.
- Sections and chart titles were given **distinct levels** so a page reads as a
  hierarchy instead of a list of equals.
- **Transcoding is counted honestly**: live TV no longer counts as avoidable
  transcoding, and a remux is counted apart from a real re-encode — they cost
  the server entirely different things.

## Deeper service coverage (v1.44 – v1.49)
- **Series and seasons get pages of their own**, and the Jellyfin screen was
  split into one file per tab behind them.
- **Seerr requests reach Radarr and Sonarr** — a request made here lands in the
  service that has to act on it.
- **Collections**, with the films still missing from them and their download
  status.
- **Lidarr can hand-import a stuck download** instead of leaving it wedged.
- **Notifications carry the time the event happened** and open the thing they
  are about; a failing watchlist now says so instead of looking empty.

## Themes and the services list (v1.34 – v1.43)
- **Theme presets with a separate background choice**, then **two-tone presets**:
  Synthwave reworked, Tron and Toxic in place of Nord and Gruvbox, Japan Neon
  flipped to pink on blue. Every two-tone theme tints its surfaces; the accent
  carries everything visible, the base colour only the surfaces underneath.
- **The services list became arrangeable** — groups, view modes, pinning with a
  star, long-press actions, and a mixed view that keeps pinned services as cards
  while the rest become tiles with their stat numbers in them.
- **Choose which screen the app opens on.**

## Watching, not just browsing (v1.25 – v1.33)
- **Watched state you can see and change** — a check badge on finished items,
  tap to toggle, and unwatched-episode counts on folders.
- **Seasons and albums expand in place** instead of drilling into a new screen.
- **The player runs a whole evening on its own**: autoplay next with a
  configurable lead time (45 s by default), intro and outro skip with a segment
  readout in the info panel, picture-in-picture, a sleep timer and a resume
  prompt.
- **Library sort, filter and search**, favourites, casting to another device,
  downloading at a smaller quality, and a **continue-watching home-screen
  widget**.

## The player becomes a player (v1.20 – v1.24)
- **Pinch to zoom, pan, double-tap to reset**, with zoom snapping to fit / 2× /
  3× / 4× and a YouTube-style zoom-to-fill for aspect ratios that do not match
  the screen.
- **Brightness and volume swipes** got sensitivity and edge-margin settings.
- **The camera cutout is respected by default**, and fit uses the full width of
  the phone; zoom fills into the cutout when you ask it to.
- **Resume was broken and is fixed** — mpv was never given a start position, so
  every resumed item wiped its own progress.

## Prowlarr, Seerr and the Jellyfin media tab (v1.11 – v1.19)
- **Prowlarr indexers can be added and edited in the app** — a dynamic field
  form built from the schema catalogue, a Test button in the add dialog, and the
  server's real validation message instead of a bare 400.
- **Seerr** gained genre and category rows in Discover, category screens with
  sorting, a full-screen media detail with a banner in place of the old dialog,
  and a watchlist that adds, removes, reflects its state and reports the actual
  error when it cannot.
- **Lidarr album monitoring**, and a whole-item monitor toggle in the detail
  screen that also fixes artist monitoring.
- **The Jellyfin media tab moved to the front** and was redesigned: a media home
  with per-row styling (accent, poster size, fan-art background, hide), a browse
  grid, full-screen detail, and shared section/hint primitives underneath it all.

## Playback, downloads and music (v1.2 – v1.10)
- **In-app Jellyfin video playback** with streaming and progress reporting,
  gradient scrims, a settings panel and swipe gestures.
- **libmpv as the video engine** — it plays everything, including PGS and ASS
  subtitles, and verifies TLS against a bundled CA bundle rather than trusting
  everything.
- **Offline downloads**: a download manager, Wi-Fi-only, whole seasons at once,
  per-episode progress in the notification, and downloads shown in the Jellyfin
  media tab while offline.
- **A background music player** (media3 MediaSession) with a now-playing queue
  and music downloads.
- **Statistics across services** — a cross-service overview with bar charts,
  per-service metrics and time-series trends — and a **player info panel** with
  media details and live playback metrics.
- Individual Jellyfin libraries can be hidden or shown.

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
