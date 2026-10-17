# Demo video

**Goal:** 45 seconds that show one evening with the stack, not a tour of every screen.
Silent with captions (most people watch muted), vertical 1080×2340 for phones and social,
ending on a title card with the link.

## How it is recorded

`.maestro/screenshots/v3_video.yaml` drives the real debug build in demo mode, and
`.maestro/screenshots/capture.sh` records the screen with `adb shell screenrecord` around it.
In CI that happens on an emulator (*Screenshots* workflow); with your phone plugged in, the
same two files record the phone instead:

```console
$ gradle assemblePhoneDebug
$ bash .maestro/screenshots/capture.sh     # needs maestro and one device on adb
```

Captions and the end card are added afterwards with ffmpeg (`make-video.sh` in this folder),
so the raw recording stays untouched and the text can be changed without re-recording.

## Storyboard

| # | Time | On screen | Caption |
|---|---|---|---|
| 1 | 0–6 s | Home: universal calendar, popular shows, watch leaderboard; a slow scroll | **Your whole media stack. One Android app.** |
| 2 | 6–10 s | Services drawer: every service with its live numbers | **Jellyfin, the \*arr apps, Seerr, NZBGet, ntfy** |
| 3 | 10–17 s | Radarr library, then the ⋮ sheet: quality profiles, tags, import lists, indexers, logs, backups | **Not just status: manage them** |
| 4 | 17–25 s | NZBGet → News servers: today, 7 days, 30 days, articles found | **What every news server delivered** |
| 5 | 25–31 s | Jellyfin → Now Playing: who watches what, with remote control | **Who is watching, and a remote for it** |
| 6 | 31–39 s | Jellyfin → Dashboard → Transcoding settings | **Jellyfin's own settings, from the phone** |
| 7 | 39–45 s | Back home | **No backend. No account. Try the demo.** |
| 8 | 45–49 s | End card: icon, "SANCTUMD 3.0", repo link, "free · GPL-3.0" | (card) |

Captions are short enough to read in two seconds, sit in the lower third above the gesture
bar, and never cover the part of the screen being shown.

## If the recording is not usable

The emulator renders with software graphics, so animations can stutter. Then record the same
flow on the phone (command above), or cut the video from the screenshots in
`docs/screenshots/v3.0/` as a slideshow: 4 s per picture, 0.4 s cross-fade, same captions.
`make-video.sh` has a `--slides` mode for that.
