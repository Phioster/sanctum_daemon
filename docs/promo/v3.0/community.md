# Community outreach

Where to post, what each place cares about, and a post written for it. Nothing here has been
posted.

## Before posting anywhere

- **Read the rules on the day you post.** Reddit was not reachable from where this was
  written, so the subreddit rules below come from secondary sources and are marked as such.
  Sidebars and pinned megathreads change; they win over this file.
- **One place a day, not five at once.** Cross-posting the same text the same hour reads as
  spam and gets caught by Reddit's filters.
- **Stay for the comments** for the first two hours. Answering questions is most of the
  value of a post.
- **Say it is your project and that AI helped write it**, in the post, not when someone asks.
- **Lead with the demo mode**: it is the answer to "why would I install an APK from a stranger".

## Media

The demo video (`sanctumd-3.0-demo.mp4`, 49 s, silent, captioned), seven short clips (`clips/`)
and current screenshots (`docs/screenshots/`) are ready; `announcements.md` says which fits where.

## Priority

| # | Where | Why | Effort | Notes |
|---|---|---|---|---|
| 1 | r/selfhosted | the exact audience | low | rules: check the self-promotion rule and any "new project" megathread; the repo is older than three months (created 2026-07-14), which is the age some summaries quote |
| 2 | r/jellyfin | Jellyfin admin + TV client | low | keep the \*arr part short; see below |
| 3 | ntfy docs, "Projects + scripts" | permanent listing, people search there | low | PR to `binwiederhier/ntfy`, file `docs/integrations.md` |
| 4 | awesome-jellyfin | permanent listing | low | PR, edit `assets/clients/clients.yaml` (CLIENTS.md is generated); Conventional Commit title; "4 weeks old, maintained" criterion is met |
| 5 | Lemmy: selfhosted@lemmy.world, jellyfin@lemmy.ml | smaller, friendlier to FOSS | low | same text as Reddit, adapted |
| 6 | r/androidapps | general Android users | low | only after 1–2 have produced feedback; check its self-promotion format |
| 7 | r/homelab | broad, hardware-focused | low value | skip unless someone asks there |
| — | r/sonarr, r/radarr, r/usenet | | | skip: support-focused subs, and a post centred on downloading invites the wrong discussion |

## r/selfhosted

**Title:** Sanctumd: an Android app that manages Jellyfin and the \*arr stack directly (no backend, demo mode built in)

**Body:**

I run Jellyfin with Radarr, Sonarr, Lidarr, Prowlarr, Seerr, NZBGet and ntfy, and wanted
one app on the phone that can actually *do* things across all of them, not only show
status. That became Sanctumd, now at 3.0.

What it does that I could not get elsewhere in one place:

- Jellyfin admin: users and library access, plugins, tasks, backups, API keys, and the
  server/transcoding/library settings (it sends back only what you changed)
- The \*arr side beyond the queue: manual import, rename files, tags, quality profiles,
  download clients, import lists, logs, backups
- Seerr requests (approve, edit before approving, retry a failed one)
- NZBGet: why a download failed, volume per news server, upload an .nzb from the share menu
- ntfy subscription, so the \*arr webhooks arrive as Android notifications with posters

How it works: every request goes from the phone straight to the service's own API, with
the URL and headers you give it (Cloudflare Access supported). No backend, no account, no
analytics, no crash reporting. Keys are stored AES-256-GCM encrypted, key in the Android
Keystore.

**To try it without connecting anything:** install the APK and tap "try the demo". It runs
a made-up homelab inside the app.

Limits: Android 8+ on 64-bit ARM, APK from GitHub releases (no Play Store), only tested
against my own setup (versions in the README). Disclosure: a large part of the code was
written with an AI assistant; every change is reviewed and goes through 500+ unit tests,
lint and an emulator launch test, and I use it daily. GPL-3.0.

GitHub: https://github.com/Phioster/sanctum_daemon

Version numbers of your setup in a bug report are gold; that is how the "tested" list grows.

## r/jellyfin

The Jellyfin community is wary of download-tool talk (the official forum discourages \*arr
discussion that isn't about a Jellyfin problem). Lead with Jellyfin.

**Title:** I made an Android app for administering Jellyfin from the phone (plus a separate libmpv TV client)

**Body:**

Sanctumd started as a way to manage my Jellyfin server without opening the web UI on a
phone. At 3.0 it covers:

- Dashboard: active sessions with remote control, scheduled tasks (run/cancel), activity,
  devices, storage, backups, API keys, plugins with updates and repositories
- Users and per-library access, Quick Connect approval
- Server, transcoding and per-library settings, including hardware acceleration, tone
  mapping, trickplay and metadata/image providers. Only changed fields are sent back.
- Metadata and artwork editing, collections, playlists, merging and splitting versions
- Music with timed lyrics, Instant Mix and "more like this"

There is also a separate Android TV APK that is only a Jellyfin player, using libmpv for the
codecs cheap TV sticks lack. It finds the server by broadcast and signs in with Quick Connect.

It can also talk to Seerr and the \*arr apps if you run them, but none of that is needed.
A demo mode lets you look around without a server.

Free, GPL-3.0, no account, no telemetry. Android 8+ (arm64). AI-assisted, reviewed and
tested; details in the README. Tested on my own server only, so reports with version
numbers are welcome.

https://github.com/Phioster/sanctum_daemon

## ntfy docs PR

File: `docs/integrations.md`, section **Projects + scripts**, alphabetical position.

```markdown
- [Sanctumd](https://github.com/Phioster/sanctum_daemon) - Android app for Jellyfin and the *arr stack; subscribes to ntfy topics for live pushes with images, priorities and action buttons (Kotlin)
```

PR title: `Add Sanctumd to projects`

## awesome-jellyfin PR

Edit `assets/clients/clients.yaml` (look at an existing Android entry for the exact keys;
the file defines them). Description, short and non-promotional as their guide asks:

> Android app to administer Jellyfin (dashboard, users, settings, metadata) alongside the \*arr apps; separate libmpv-based Android TV client.

PR title: `feat(clients): add Sanctumd`

## Lemmy

Use the r/selfhosted text, trimmed to the first two paragraphs, the "try it" line and the
limits paragraph. Lemmy readers care about the GPL and the absence of telemetry; keep both.

## r/androidapps

**Title:** [Dev] Sanctumd: manage a self-hosted Jellyfin + Radarr/Sonarr server from your phone (free, open source)

Short body: what it is in two sentences, the demo-mode line, the limits line, the link. The
audience there mostly doesn't run a server, so the demo mode is the whole pitch.

## Answers to the questions that will come

- **"How is this different from nzb360 / LunaSea?"** Same space. Sanctumd adds Jellyfin
  administration, Seerr, ntfy pushes and a TV player, is GPL-3.0 and free, and speaks only
  NZBGet as a download client.
- **"Why not the Play Store?"** One person, no time for store review cycles yet; every APK
  is built from the tagged source by GitHub Actions.
- **"AI-written, why should I trust it with my API keys?"** The storage code is small and
  named in the README (`Crypto.kt`, `ServiceStore.kt`, `Http.kt`); read it, or try the demo
  first. Security reports go through GitHub's private advisories.
- **"iOS?"** No plans.
- **"Plex/Emby?"** No.
