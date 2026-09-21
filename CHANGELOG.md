# Changelog

All notable changes to **Sanctumd**, grouped by milestone. Newest first.
Versioning is `major.minor.patch`; the app is in daily use, now at 2.1.

## Onboarding unblocked, and a dialog that stopped waiting (v2.1.3)
- **The first screen after installing was half-hidden.** With three-button navigation the
  `next` button of the intro sat under the system bar: label cut off, a tap in the middle
  went to Android. Gesture navigation hid the problem. A new automated walk through the
  intro found it on its first run. Two more screens had the same gap in a milder form.
- **"System & health" stopped waiting for the slowest answer.** It asked for version,
  health and disk usage and waited for all three. Disk usage is not broken, it is slow:
  on a server that is not a tidy Docker host it walks every mount point, which can pass a
  minute, and the shared 20 s read timeout cut it off every time. Version and health now
  appear at once and the disks fill in when they arrive, so the numbers show up at all.
- **The same dialog now shows the About block:** .NET, database and version, migration
  level, mode, uptime, data and startup directory. All of it was already in the answer;
  only the version was being read out of it.
- **Picture-in-picture engages by itself** from Android 12 when you leave during
  playback, and shrinks out of the video instead of the whole black screen. Android 9 and
  10 now reach into the camera cutout too; they had been handed a value they do not know.
- **Small print.** Five icons that carried the only information in their place said
  nothing to a screen reader. Fourteen German strings were still in the television
  client. Six Lint errors are gone, including a `StateFlow.value` read inside composition.
  The Lint report is no longer discarded after every build, and the launch smoke test
  runs when the app changes, not only when the test does.

---

## The player taken apart, and the TV client on a real television (v2.1.2)
- **The television client ran on a television.** 2.1.1 shipped it untested on real
  hardware. It has now been driven with a remote on an Android TV box: the D-pad moves
  focus and playback works. That last part is the point, because libmpv and its codecs
  are the whole reason the TV flavour exists, and no emulator here could answer whether
  it loads on a 32-bit stick.
- **Settings subpages open at the top.** Opening "about" showed the page already scrolled
  down, with the name and version above the fold. The list and an opened subpage shared
  one scroll position; they keep separate ones now.
- **Arrows and dots come from the theme.** Expand arrows, the calendar's month arrows,
  the bullets in the dashboard and the shortcuts, the folder marker in the \*arr browser,
  the player's seek indicator and the selection dots in the television menu were plain
  characters inside labels. They are icons now. The D-pad help stays as characters on
  purpose: it describes keys on a remote.
- **The player screen taken apart.** Its five overlays moved into their own file. The
  `PlayerScreen` composable went from 689 lines to 565 and the television player from 414
  to 397. Those count the function; the file itself only went from 1099 to 1063, since the
  code moved next door.
- **Small print.** The \*arr import dialog says "unmatched" instead of a bare dash. The
  last German strings in the television client were translated. The launch smoke test runs
  again after installing from a path that stopped existing when the flavours arrived.

---

## A new signing key, and four screens taken apart (v2.1.1)
- **Signed with a new key.** The password to the old one was only in the build secrets,
  where nobody can read it back. If those were ever lost, the app could never be updated
  again and everyone would have had to uninstall it. So the key was replaced now, while
  the release had no downloads yet and it costs almost nothing. The cost is one
  reinstall: this build cannot update over 2.1.0, Android refuses that. Export your
  configuration first (settings, backup / data), install, then import it back.
- **Four screens taken apart.** Seerr went from 810 lines to 378, the \*arr screen from
  668 to 370, Jellyfin from 585 to 433, Prowlarr from 474 to 280. Dialogs now hold and
  load their own state instead of being fed by the screen behind them. Nothing about
  this is visible; it is the kind of work that makes the next change possible.
- **Characters stopped standing in for icons.** A tick, a star and a heart were sitting
  in labels where the font decides how they look. They come from the theme now. Three
  German strings in the television client were translated as well.

## A second app for the television (v2.1)
- **Films play again.** 2.0 handed libmpv all its HTTP headers as one string, and the option
  that takes them splits on commas. A Jellyfin `Authorization` header is made of commas, so
  the server received a torn-up request and answered 400. Each header now goes in on its own.
  Nothing plays on 2.0; anyone on it needs this build.
- **An Android TV client**, built from the same source as a second APK. It is a Jellyfin
  streaming client and nothing else: the dashboard, the service list, the widgets and the
  background services are *removed* in the TV manifest rather than merely hidden, so the
  build has one launchable screen and no way to reach the rest.
- **Signing in from the sofa.** Servers are found by broadcast on the local network;
  Quick Connect shows a code on the television that you approve on your phone, because
  typing a password with a remote control is miserable. Username and password still work
  where Quick Connect is switched off.
- **https is tried before http** when you type a bare address, and the sign-in screen says
  so when the address it settled on is unencrypted. The password and the Quick Connect
  secret travel over that address moments later, and nobody inspects a URL bar on a TV.
- **libmpv does the decoding**, which is the whole point on a cheap stick: the codec gaps
  that make other clients stutter stop mattering. The panel's refresh rate is matched
  before playback begins, never during it. Switching mid-stream tears the picture.

## Small print (v2.0.1)
- **The task switcher no longer shows what the lock hides.** With the app lock on, the
  screen was covered on open, but Android had already taken its thumbnail for the
  recents list, and that one anybody could see. On Android 13 and up the thumbnail is
  suppressed while the lock is on. Screenshots keep working; only the preview goes.

## Everything the audit found, and a number to point at (v2.0)
- **A tagged release at last.** Until now the only way to get Sanctumd was the
  rolling dev build, whatever master happened to be that hour. 2.0 is the first
  version with a tag behind it and a signed APK built by the release workflow.
- **A full security audit of the repository**, and every real finding fixed.
  That is why the number jumps rather than creeps.
- **Credentials stopped leaking into URLs.** The Jellyfin token travelled in the
  query string of every music stream and artwork request, where server logs and
  proxy logs keep it; it now rides in a header on the track. Redirects can no
  longer switch from https to plain http with the credentials still attached.
- **The media session answers only to this app.** Any installed app could
  connect to the player's MediaSession and read what it published.
- **Download filenames are built, not borrowed.** The server's `container`
  string went into a path unchecked. A `../` in it wrote outside the download
  directory. Names are assembled from sanitised characters now, and the finished
  path is verified against its directory before a byte is written.
- **TLS verification is never off.** With the bundled CA file missing, mpv fell
  back to `tls-verify=no` for every stream, silently. It verifies always, and
  adds the bundle only when it is really there.
- **The lock no longer flashes the dashboard** before it covers it: an unset
  preference read as "off" for one frame.
- **Smaller edges closed.** Widget configuration screens check the widget is
  theirs before writing to it, notification ids are allocated here instead of
  taken from whatever the ntfy server sends, only http and https open in the
  browser, and destructive-action prompts name the host rather than a whole URL
  with its query string.
- **A build without the release key says so in its version name**, so it cannot
  be mistaken for one.
- **The stats screen speaks English again.** Its section headings, playback tiles
  and the trends hint had drifted into German. The app is English-only for now, so
  they are back in line, as are the last few German code comments. The language
  names in the player stay as they are: a track labelled "Deutsch" is found by
  matching that word, and a language is written in its own language.
- **Music plays again.** Tapping a track did nothing at all: the detail sheet closed
  itself and *then* launched the fetch, but `rememberCoroutineScope()` dies with its
  composable: the coroutine was cancelled before it ever reached the network. Fetching
  a track and handing it to the player now runs in the view model's scope, which
  outlives the screen, as playback that continues in the background should.
- **And it says so when it cannot.** Three layers of the music path each swallowed
  their own error, so a failure produced no message, no log line and no clue. The
  reason the bug above hid as long as it did. All three speak now, on screen and in
  the log.
- **The lock screen gets its controls back.** The audit's session callback admitted
  only this package, but media3 routes the lock screen, Bluetooth and car head units
  through the platform session under a fixed sentinel package name, so the callback
  shut them all out. It is withdrawn: the credential it was guarding left the metadata
  in the same round, which was always the real protection.
- **No cover means no cover URL.** An album without artwork still handed every one of
  its tracks the album's image address, so each one pointed at a 404 and the session's
  bitmap loader ran into it once per track.
- **The Jellyfin tile counts music too.** It showed films, series and what is playing,
  and passed over the song count the server had been sending all along.
- **An album is an album now.** Music used to open the same expanding folder rows a
  series does: a tap drilled one level deeper and a track needed the detail sheet before
  it played. A Jellyfin album opens its own screen instead: the cover, the artist and
  year, the running time, and a numbered track list where a tap plays and a long press
  still opens the details. Play and shuffle sit on the album itself, and Now Playing has
  the shuffle and repeat controls a player is expected to have (all, one, off).
- **Looking a title up before adding it.** Radarr, Sonarr and Lidarr answered a search
  with a list of titles and years, and the first tap went straight to the add dialog,
  even though the plot, the poster, the runtime and the rating were in the answer and thrown away.
  A search now has its own screen with posters, and a hit opens an info screen: cover,
  rating, runtime, genres, plot, and whether the service already holds it. Adding is one
  button there rather than the only thing the list can do. Seerr's search does the same,
  through the detail sheet it already had. Both are reached from a menu entry that now
  says **Search** instead of "Add new" / "New request". It was findable only if you
  already knew it was there.
- **One place for the app's symbols.** The same action wore two different icons two
  screens apart: some buttons drew a vector icon, others carried the character in their
  label (`"⬇  download"`), and the mono font has no glyph for several of those, so
  Android substituted a different font per character. Every action icon now comes from
  one registry, named for what it means rather than what it looks like, the way colours
  come from the theme. The mini player, the download tiles, the card editor, the Live TV
  rows and the session controls draw icons now instead of characters; what stays text is
  what was always text: a tick in a table, a chevron, the star on a pinned service. The
  home screen widgets keep their character: a widget renders through RemoteViews, where
  an icon has to be a drawable rather than the vector the app uses.
- **And the warning colours come from the theme too.** Amber marked pending downloads,
  blocked imports and "tap again to confirm" in 32 places, each one writing the colour
  out by hand: two slightly different ambers, as it turned out, plus a third copy of
  the error red. They are three named tokens now. Like the error red they deliberately
  do not follow the palette: a warning tinted to match the accent stops reading as one.
- **The app lock promises only what it keeps.** The switch read "Require
  fingerprint/face or device PIN on open", which sounds like the data is sealed
  behind it. It is not, on purpose: the Keystore key is not bound to user
  authentication, because live push, widgets and downloads have to decrypt while
  the phone is in your pocket, that is the whole reason they exist. The lock
  gates the screen, and the switch, the hint beneath it and SECURITY.md now all
  say so.

## The about screen answers for itself (v1.58)
- **What the app is**, on the page people open first: the services it speaks to, and
  tappable links to the source, this changelog, the issue tracker and the licence. The
  repository address used to be text you could not even tap.
- **A diagnostics block built for a bug report.** Version, Android, device and the kinds
  of services configured, with one tap to copy it. Kinds and a count only: no labels, no
  addresses, no keys, so it is safe to paste into a public issue.
- **The bundled work of others**, with their licences.

## Where a title streams, and Jellyfin 12 (v1.55 – v1.56)
- **Streaming availability** in the Seerr, Radarr and Sonarr detail screens:
  the services a film or series runs on, as a row of provider logos. The data
  comes from TMDB through the Seerr you already configured; the app carries no
  TMDB key of its own. Availability is always per country, so the region is
  shown and selectable, and a region TMDB knows nothing about drops the row
  entirely rather than showing an empty one. Cast and availability now arrive in
  a single Seerr call instead of two.
- **Media segments.** Intro and outro markers are queried the way Jellyfin 12
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
  is the whole blur. There is no blur pass to pay for on old hardware.

## Playback statistics (v1.50 – v1.52)
- **A statistics screen for playback.** What was watched, by whom, and what it
  cost the server, in the house style, with readable labels and bar fills that
  are actually visible.
- Sections and chart titles were given **distinct levels** so a page reads as a
  hierarchy instead of a list of equals.
- **Transcoding is counted honestly**: live TV no longer counts as avoidable
  transcoding, and a remux is counted apart from a real re-encode, because they cost
  the server entirely different things.

## Deeper service coverage (v1.44 – v1.49)
- **Series and seasons get pages of their own**, and the Jellyfin screen was
  split into one file per tab behind them.
- **Seerr requests reach Radarr and Sonarr.** A request made here lands in the
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
- **The services list became arrangeable.** Groups, view modes, pinning with a
  star, long-press actions, and a mixed view that keeps pinned services as cards
  while the rest become tiles with their stat numbers in them.
- **Choose which screen the app opens on.**

## Watching, not just browsing (v1.25 – v1.33)
- **Watched state you can see and change.** A check badge on finished items,
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
- **Resume was broken and is fixed.** Mpv was never given a start position, so
  every resumed item wiped its own progress.

## Prowlarr, Seerr and the Jellyfin media tab (v1.11 – v1.19)
- **Prowlarr indexers can be added and edited in the app.** A dynamic field
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
- **libmpv as the video engine.** It plays everything, including PGS and ASS
  subtitles, and verifies TLS against a bundled CA bundle rather than trusting
  everything.
- **Offline downloads**: a download manager, Wi-Fi-only, whole seasons at once,
  per-episode progress in the notification, and downloads shown in the Jellyfin
  media tab while offline.
- **A background music player** (media3 MediaSession) with a now-playing queue
  and music downloads.
- **Statistics across services.** A cross-service overview with bar charts,
  per-service metrics and time-series trends, plus a **player info panel** with
  media details and live playback metrics.
- Individual Jellyfin libraries can be hidden or shown.

## 1.1.0 (2026-07-21)
A structural round: no new features, but the codebase is now one you can change
safely.

- **Renamed to Sanctumd throughout.** Code, package and theme moved to
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
- **Safe mode.** Every call that changes something on a server (deletes, grabs,
  imports, request decisions, restarts, shortcuts) runs through one guard. Turn
  safe mode on (settings → security) and those calls are refused before they
  reach the network, which makes testing against a live homelab a mechanism
  rather than a matter of care.
- **One service registry.** Logos and quick actions are described once per
  service instead of in five places, with a test that fails when a new service
  type is left undescribed. See `docs/adding-a-service.md`.

## 1.0.0 (2026-07-20)
First release. Sanctumd was in daily use throughout the 0.x line; 1.0 marks the
reviewed, release-signed milestone. There is no `v1.0.0` tag: the git history was
cleaned at this point (below), and the tag did not survive it. `backup-pre-scrub`
still sits on the commit before.

- **Security & correctness review.** No critical findings. Applied hardening:
  atomic service-status updates; PBKDF2 raised to 210k iterations with a
  **versioned** portable-export format (older backups still import); the
  config store now surfaces a recovery banner for unreadable/corrupt data
  instead of a silently-empty list; per-card dashboard cache eviction.
- **Release signing.** Reproducible signed APKs built in CI (keystore from
  encrypted secrets, never in the repo).
- Repo prepared for release: accurate README, hardened `.gitignore`
  (release-keystore & secret patterns), this changelog, cleaned git history,
  `SECURITY.md` + issue templates.

## Notifications & live push (v0.47 – v0.83)
- **Live push** via a direct **ntfy** topic subscription, a special-use
  foreground service streams messages into local notifications instantly,
  reusing the topic your services already webhook to. Per-server connection
  merging, offline catch-up, and a masked topic in the notification title.
- **Polling fallback** worker (WorkManager) for new media / imports / requests /
  health, with per-item baselining so there is no backlog spam.
- Card data cached per-tab so switching tabs no longer refetches; pull-to-refresh
  is the refresh trigger.

## Customizable dashboard & widgets (v0.36 – v0.71)
- **Widget home.** Editable category tabs + a full-page Services drawer, with a
  broad set of per-service cards (queues, missing, coming-soon, recently-added,
  poster rows, statistics, watch leaderboard).
- **Per-card styling.** Title, entry count, accent colour, header icon, poster
  size, fan-art Ken-Burns backgrounds, and flat / solid / glass themes; plus
  Section and Quick-Button cards and a Universal Calendar.
- **Home-screen widgets** (Glance): shortcuts, status, quick actions, an
  upcoming-calendar agenda, and 1×1 stat tiles.
- Global search across all services; dashboard auto-refresh & reorder;
  finger-following swipe navigation.

## Security, portability & onboarding (v0.55 – v0.75)
- Service credentials **AES-256-GCM encrypted** (Android Keystore); secrets
  excluded from cloud backup.
- **Biometric app-lock** (device-credential fallback).
- **Config export / import.** Password-protected (PBKDF2 → AES-GCM) portable file.
- First-run onboarding flow; optional 18+ content filter; open-in-app buttons.

## Service integrations (v0.18 – v0.52)
- **Jellyfin admin.** Dashboard, scheduled tasks, activity log, restart, user
  management with per-library access, media browsing with cast, Now Playing
  controls, plugins, libraries, server logs.
- **Radarr / Sonarr / Lidarr.** Full list tabs, add, interactive search &
  release picker, grab, manual import, per-episode monitoring, System & health.
- **Seerr** (Jellyseerr): requests, issues, discover, watchlist, per-season requests,
  media detail, users & stats.
- **Prowlarr.** Indexers, search, history, tasks, send-to-arr.
- **NZBGet.** Queue, history, pause/resume, edit, add-URL.

## Foundation (v0.1 – v0.17)
- Project scaffold, CI (GitHub Actions debug APK), service config model with
  Cloudflare Access custom headers, matrix-terminal theme, first integrations.
