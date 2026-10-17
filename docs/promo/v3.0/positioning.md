# Sanctumd v3.0: positioning and messaging

One source for every sentence that describes the app. Posts, the release page, the README
and the site take their wording from here, so they never disagree.

## Headline and tagline

**Headline:** Your whole media stack, in one Android app.

**Tagline:** Jellyfin, the \*arr apps, Seerr, NZBGet and ntfy. No backend, no account.

**Sign-off (already the app's own line):** the daemon that guards your homelab.

## The ten-second version

> Sanctumd is a free, open-source Android app for people who run Jellyfin and the \*arr
> stack. It talks to each service's own API, so from one app you can see the download queue,
> fix a stuck import, approve a request, change Jellyfin's transcoding settings and then
> watch the film. There is a demo mode, so you can try all of it without a server.

## Who it is for

Someone with a self-hosted media server (Jellyfin plus some of Radarr, Sonarr, Lidarr,
Prowlarr, Seerr, NZBGet, ntfy) who manages it from their phone and is tired of switching
between a browser tab per service and a separate app per job.

Not for: Plex or Emby users, iPhone users, anyone who wants a hosted service.

## What makes it worth trying

Each point is a feature in the code today, phrased so it can be checked.

1. **It manages, not just monitors.** Most phone dashboards show status. Sanctumd changes
   things: Jellyfin's server, transcoding and per-library settings; the \*arr apps' quality
   profiles, tags, import lists, download clients, file renaming and manual import; Seerr's
   requests and settings; NZBGet's queue, speed limit and post-processing.
2. **Jellyfin administration and the download side in the same app.** Users and library
   access, plugins and repositories, scheduled tasks, backups, API keys, who is watching
   what (with remote control), next to the queue that feeds it.
3. **Try it in thirty seconds.** "try the demo" opens a busy made-up homelab inside the
   app. No server, no sign-up, and nothing of a real setup is touched.
4. **Nothing in between.** No backend, no account, no analytics, no crash reporting.
   Credentials stay on the phone, AES-256-GCM encrypted with a key in the Android Keystore.
5. **Changes you can undo, mostly by not making them.** Jellyfin's settings pages send back only the
   fields you touched and show how many; destructive actions ask first; a Safe Mode switch
   blocks every write while you look around.
6. **Pushes that arrive.** Subscribe to your ntfy topic and webhook messages from the \*arr
   apps arrive as Android notifications, with posters; tapping one opens the title it is
   about.
7. **A television client from the same source.** A separate, small APK that is only a
   Jellyfin player, built on libmpv for the codecs cheap TV sticks lack.
8. **Free software.** GPL-3.0, no paid tier, nothing held back.

## Honest limits (say them before someone else does)

- Android only, 8.0 or newer, 64-bit ARM for the phone app. Sideloaded APK, no Play Store.
- Built and used against one homelab by one person. The README lists the exact server
  versions it runs against; others may or may not work.
- A large part of the code was written with an AI assistant, reviewed, tested (500+ unit
  tests, lint, an emulator launch test) and used daily. The README says so; posts should too.
- NZBGet is the only download client it speaks to directly (SABnzbd, qBittorrent and others
  show up through the \*arr apps' queues, not as their own screens).

## Words to use, words to avoid

| Use | Avoid |
|---|---|
| "manages", "changes", "from one app" | "ultimate", "all-in-one solution", "game-changer" |
| "talks to each service's own API" | "seamless integration" |
| "tested against one homelab" | "works with every setup" |
| "free, GPL-3.0" | "100% free forever!!" |
| "demo mode" | "no setup required" (setup is needed for a real server) |

## Wording by channel

**GitHub repository description (350 characters max):**
Jellyfin admin and the *arr stack (Radarr, Sonarr, Lidarr, Prowlarr, Seerr, NZBGet, ntfy)
in one native Android app. Talks to each service directly: no backend, no account, no
telemetry. Demo mode built in. GPL-3.0.

**GitHub topics:** `jellyfin`, `radarr`, `sonarr`, `lidarr`, `prowlarr`, `jellyseerr`,
`nzbget`, `ntfy`, `homelab`, `selfhosted`, `android`, `jetpack-compose`, `kotlin`

**Website hero (already the site's `whoami`, keep):** one Compose app for your whole
self-hosted stack.

**Social bio line:** Sanctumd: your Jellyfin and \*arr stack in one Android app. Free, open
source, no backend.
