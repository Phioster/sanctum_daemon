# Announcements

Three lengths of the same message. Links assume the release is tagged `v3.0.0`.

- Repo: https://github.com/Phioster/sanctum_daemon
- Release: https://github.com/Phioster/sanctum_daemon/releases/latest
- Site: https://sanctumd.dpdns.org

---

## Short announcement (forums, Lemmy, Discord, Matrix)

**Sanctumd 3.0: Jellyfin and the \*arr stack in one Android app**

I've been building an Android app for my own homelab and it reached 3.0, so it's time to
show it around. Sanctumd talks to Jellyfin, Radarr, Sonarr, Lidarr, Prowlarr, Seerr,
NZBGet and ntfy directly through their APIs. There's no backend and no account; your keys
stay on the phone, encrypted.

It's less a dashboard than a remote for the whole stack. You can change Jellyfin's
transcoding settings, rename files in Sonarr, approve a Seerr request, see why an NZBGet
download failed and get the \*arr webhooks as push notifications through ntfy.

There's a demo mode, so you can try it without pointing it at anything: install, tap
"try the demo".

Limits up front: Android 8+ on 64-bit ARM, sideloaded APK (no Play Store), tested against
my own setup only, and a lot of it was written with an AI assistant (reviewed, 500+ unit
tests, used daily). GPL-3.0, free.

Repo and APK: https://github.com/Phioster/sanctum_daemon

---

## Social post (Mastodon / Bluesky; 290 characters, under Bluesky's 300)

> Sanctumd 3.0: one Android app for Jellyfin, Radarr, Sonarr, Lidarr, Prowlarr, Seerr,
> NZBGet and ntfy. Talks to each API directly, no backend, no account. Demo mode to try it
> without a server. Free, GPL-3.0.
> https://github.com/Phioster/sanctum_daemon
>
> #selfhosted #homelab #jellyfin #android

(Attach `docs/promo/v3.0/graphics/social-preview.png` or the demo clip.)

---

## Long version

The GitHub release text is `release-notes-v3.0.0.md` in this folder.
