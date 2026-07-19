<div align="center">

<img src="docs/banner.svg" alt="Sanctumd" width="820">

<br>

![Android](https://img.shields.io/badge/Android-8.0+-238636?style=flat-square&labelColor=0d1117&logo=android&logoColor=3fb950)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-238636?style=flat-square&labelColor=0d1117&logo=kotlin&logoColor=3fb950)
![SDK](https://img.shields.io/badge/SDK-35-238636?style=flat-square&labelColor=0d1117)
![License](https://img.shields.io/badge/License-GPL--3.0-238636?style=flat-square&labelColor=0d1117)
![Status](https://img.shields.io/badge/status-heading_to_1.0-238636?style=flat-square&labelColor=0d1117)

**Jellyfin admin + the \*arr / download stack — one native Android app, one matrix-terminal theme.**

</div>

```console
visitor@homelab:~$ ./sanctumd --whoami

  > one Compose app for your whole
    self-hosted stack. add services
    once — status, queues, media,
    requests, users, logs — all in
    one terminal-styled UI. every
    action hits each service's own
    API directly. no backend. no
    account. the daemon that guards
    your homelab.
```

<div align="center">
  <img src="docs/screenshots/services.png"         width="49%" alt="Services overview">
  <img src="docs/screenshots/dashboard-series.png" width="49%" alt="Series dashboard">
  <br>
  <img src="docs/screenshots/home-calendar.png"  width="49%" alt="Home · calendar &amp; leaderboard">
  <img src="docs/screenshots/jellyfin-admin.png" width="49%" alt="Jellyfin admin">
  <br>
  <img src="docs/screenshots/media-detail.png" width="49%" alt="Media detail with cast">
  <img src="docs/screenshots/live-push.png"    width="49%" alt="Live push (ntfy)">
</div>

<div align="center">

**works with**

<img src="docs/logos/jellyfin.png" height="46" alt="Jellyfin" title="Jellyfin">&nbsp;&nbsp;&nbsp;
<img src="docs/logos/radarr.png"   height="46" alt="Radarr"   title="Radarr">&nbsp;&nbsp;&nbsp;
<img src="docs/logos/sonarr.png"   height="46" alt="Sonarr"   title="Sonarr">&nbsp;&nbsp;&nbsp;
<img src="docs/logos/lidarr.png"   height="46" alt="Lidarr"   title="Lidarr">&nbsp;&nbsp;&nbsp;
<img src="docs/logos/prowlarr.png" height="46" alt="Prowlarr" title="Prowlarr">&nbsp;&nbsp;&nbsp;
<img src="docs/logos/seerr.png"    height="46" alt="Seerr"    title="Seerr">&nbsp;&nbsp;&nbsp;
<img src="docs/logos/nzbget.png"   height="46" alt="NZBGet"   title="NZBGet">

<sub>Jellyfin · Radarr · Sonarr · Lidarr · Prowlarr · Seerr · NZBGet</sub>

</div>

---

## ▚▚ `./features`

<details open>
<summary><b>media &amp; requests</b></summary>

```text
[jellyfin]
  dashboard · tasks · activity
  users · per-library access
  media browse · cast · now playing
  plugins · libraries · logs

[radarr / sonarr / lidarr]
  library · missing · cutoff
  queue · history · add
  interactive search → grab
  manual import · per-ep monitoring
  system & health

[seerr]
  requests · issues · discover
  watchlist · per-season requests
  approve/decline · media detail
  users & stats

[prowlarr]
  indexers · search · history
  tasks · send-to-arr

[nzbget]
  queue · history · pause/resume
  edit · add-url · server details
```
</details>

<details>
<summary><b>the dashboard</b></summary>

```text
[home]
  editable tabs + services drawer
[cards]
  queues · missing · coming-soon
  recently-added · poster rows
  statistics · watch leaderboard
  sections · quick-buttons
[style]
  accent · header icon · poster size
  fan-art Ken-Burns background
  flat / solid / glass themes
[search]
  global, across all services
[widgets]
  Glance home-screen: shortcuts ·
  status · quick actions · agenda
```
</details>

<details>
<summary><b>notifications</b></summary>

```text
[live-push]
  direct ntfy topic subscription
  → instant local notifications
  reuses your webhook topic
  per-server merge · catch-up
  masked topic in the title
[fallback]
  on-device polling worker for
  new media / imports / requests
```
</details>

<details>
<summary><b>privacy &amp; portability</b></summary>

```text
[crypto]
  credentials AES-256-GCM encrypted
  (Android Keystore)
[lock]
  optional biometric app-lock
[export]
  password-protected portable config
  (PBKDF2 → AES-GCM)
[network]
  secrets kept out of cloud backup
  Cloudflare Access headers
```
</details>

---

## ▚▚ `./stack`

```text
Kotlin · Compose · Material 3
Retrofit / OkHttp · Coil · Glance
WorkManager · androidx.biometric
AES-256-GCM (Android Keystore)

min SDK 26 (Android 8.0)
target SDK 35 (Android 15)
```

## ▚▚ `./build`

Every push triggers the **Build APK** workflow —
grab the `sanctumd-debug-apk` artifact and install:

```console
$ adb install -r sanctumd-debug.apk
```

Or build locally:

```console
$ gradle assembleDebug
  → app/build/outputs/apk/debug/
    app-debug.apk
```

## ▚▚ `./status`

```text
feature-complete · in daily use
heading to 1.0:
  release-signing
  full code-review
  publish
```

See [CHANGELOG.md](CHANGELOG.md) for the milestone history.

---

<div align="center">

`GPL-3.0` — Sanctumd is free software. See [LICENSE](LICENSE).

</div>
