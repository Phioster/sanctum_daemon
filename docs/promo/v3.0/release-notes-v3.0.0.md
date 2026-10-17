<!--
Draft of the GitHub release text for v3.0.0. To use it: copy to
.github/release-notes/v3.0.0.md before pushing the tag; release.yml prefers that file over
the changelog. Check the version in the install lines matches the tag.
-->

# Sanctumd 3.0

Sanctumd is one Android app for a self-hosted media stack: Jellyfin, Radarr, Sonarr, Lidarr,
Prowlarr, Seerr, NZBGet and ntfy, each reached through its own API. No backend, no account,
no telemetry.

3.0 collects everything since 2.1.6, thirty-three smaller versions that were only ever
built for testing. The short version: **the app went from watching your stack to running it.**

## Try it without a server

Install the APK and tap **try the demo** on the first screen (or Settings → demo mode later).
You get a busy made-up homelab: films, shows and music, six people watching, a full download
queue, requests and pushes. It runs inside the app; nothing reaches a server and nothing of
your own setup is touched. Tap the amber **DEMO** chip to leave.

## What's new since 2.1.6

### Jellyfin, administered from the phone
- **Server, transcoding and library settings.** Hardware acceleration and its codecs, tone
  mapping, encoder quality, trickplay, metadata language, real-time monitoring, metadata and
  image providers in order. Only the fields you change are sent back; the save button counts them.
- **Remote control** for sessions that accept it, and **Quick Connect** codes approved from the phone.
- **API keys, backups, storage, devices, upcoming and missing episodes** as dashboard tiles.
- **Plugin updates and repositories**, cancelling a running task.
- **Edit metadata and pick artwork**, collections, playlists (that play on), merge and
  split versions, upload subtitles.
- **Music:** albums as a running order, **timed lyrics** that follow the song, **Instant Mix**,
  and "more like this".

### The \*arr apps, beyond the queue
- **Edit a title after adding it**, see the file behind every episode, **rename files** to
  the naming scheme, **tags** with where they are used.
- **Quality profiles, indexers, download clients** (with "test all"), **import lists**,
  **import exclusions**, **blocklist**, **logs** and **backups**.
- Seasons in reading order, monitored and folded one by one; a season searched in one tap;
  history per title.
- Prowlarr: connected apps, tags, indexer proxies.

### Seerr
- Edit a request before approving, retry a failed one, fix a stuck status.
- Season details while requesting, actors' pages, upcoming, recommended and similar titles.
- Per-user requests and quotas, the blocklist, Seerr's own **settings**, jobs and log.

### NZBGet
- **Why a download failed**, in words, with "retry failed articles", "post-process again" and
  "mark bad, grab another" (tells Sonarr/Radarr to blocklist it and find a different release).
- ETA per item, pause for a while, post-processing on hold, several items at once.
- **Volume per news server**: today, 7 and 30 days, total, how many articles each one had.
- **Upload .nzb files** by sharing them to Sanctumd or with the file picker.

### Everything else
- **Pushes:** pictures, priorities, action buttons, emoji tags and Markdown from ntfy.
- **Your own themes**, with a readability check that keeps text legible in every background mode.
- **One menu per service** instead of a list taller than the screen; removing a service asks first.
- Pull to refresh everywhere, and lists that refresh themselves while they are open.
- On the TV: audio and subtitle tracks from the remote.

The full list, version by version, is in [CHANGELOG.md](https://github.com/Phioster/sanctum_daemon/blob/master/CHANGELOG.md).

## Install

| | |
|---|---|
| **Phone** | `sanctumd-v3.0.0.apk`, Android 8.0+, 64-bit ARM |
| **Android TV** | `sanctumd-tv-v3.0.0.apk`, a Jellyfin player only, installs next to the phone app |

Open the APK on the device and allow installing from your browser or file manager when
Android asks. Or from a computer: `adb install -r sanctumd-v3.0.0.apk`.

**Updating from 2.1.x** installs over the old version and keeps your services, as long as
that came from this release page. If you are still on **2.1.0 or older**, uninstall first
(the signing key changed in 2.1.1): export your setup under Settings → backup / data, then
import it after reinstalling.

## Good to know

- Built and used against one homelab. The [README](https://github.com/Phioster/sanctum_daemon#readme) (section `./tested`)
  lists the server versions it runs on every day; others may work or may not. An issue with
  your version numbers is the most useful bug report there is.
- No crash reporting, on purpose. A bug only reaches me through an
  [issue](https://github.com/Phioster/sanctum_daemon/issues).
- Security reports: privately, see [SECURITY.md](https://github.com/Phioster/sanctum_daemon/blob/master/SECURITY.md).
- A large part of the code was written with an AI assistant; see the README's `./built-with`
  for how it is reviewed and tested.
