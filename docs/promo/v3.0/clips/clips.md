# Short clips (TikTok, Reels, Shorts)

Seven vertical clips, 11 to 17 seconds, one feature each, recorded on the phone (v2.1.39, demo
mode) the same way as the demo video (`../record/`). Captions are burnt in, white on a dark box,
the first one is the hook; every clip ends on 2.5 s of the end card. Nothing has been posted.

## Posting order (one a day)

| # | File | Length | On-screen captions |
|---|---|---|---|
| 1 | `sanctumd-overview.mp4` | 16 s | Stop juggling six apps · One calendar: films, shows and albums · Every service, live numbers · Jellyfin, the \*arr apps, Seerr, NZBGet, ntfy |
| 2 | `sanctumd-themes.mp4` | 17 s | Nine themes, one tap each · Or build your own · It warns you before it gets unreadable |
| 3 | `sanctumd-news-servers.mp4` | 16 s | Which Usenet provider actually delivers? · Per server: today, 7 days, 30 days · and which one is falling behind |
| 4 | `sanctumd-jellyfin-remote.mp4` | 15 s | Who is watching what on your Jellyfin? · A remote for every screen in the house · Even the audio and subtitle tracks |
| 5 | `sanctumd-push.mp4` | 16 s | Your server texts you · when a film lands, poster included · Live push via ntfy, your own server |
| 6 | `sanctumd-try-the-demo.mp4` | 15 s | No server yet? · Try the built-in demo · A busy homelab, nothing to set up |
| 7 | `sanctumd-lyrics.mp4` | 11 s | Your Jellyfin music, now with lyrics · Right in the player |

## Caption and hashtag suggestions

Three to five hashtags, broad ones for reach mixed with niche ones that find the right people.
Check the current size of each in the TikTok Creative Center (Trends → Hashtags, filter by country
and "Tech & Electronics") before posting. Links in TikTok captions are not clickable: "link in
bio", with the repo or the website in the profile.

| Clip | Caption | Hashtags |
|---|---|---|
| overview | My whole homelab in one Android app. What do you run? | `#homelab #selfhosted #jellyfin #android #techtok` |
| themes | Nine themes, or build your own. Which one is yours? | `#androidapp #customization #homelab #techtok` |
| news-servers | Finally see which Usenet provider actually delivers | `#usenet #nzbget #homelab #selfhosted` |
| jellyfin-remote | A remote for every Jellyfin screen in the house | `#jellyfin #mediaserver #homelab #selfhosted` |
| push | My server texts me when a movie lands | `#ntfy #radarr #sonarr #homelab` |
| try-the-demo | No server yet? Try it with the built-in demo | `#selfhosted #homelab #opensource #androidapp` |
| lyrics | Your own music server, now with lyrics | `#jellyfin #selfhosted #music #homelab` |

## How each was recorded

`../record/scene.py` (taps timed from the recording's start), `../record/norm.py` (crop, 30 fps,
trim), then captions and the end card. Coordinates are for the Nothing Phone 3 (1260 × 2800).

| Clip | Start screen (demo mode) | scene.py actions | Trimmed to |
|---|---|---|---|
| overview | home calendar, top | `1.8:tap:630,1143 3.8:tap:630,1257 6.4:tap:84,258` | 14 s |
| themes | Settings → theme | `1.0:tap:759,663 2.8:tap:759,1203 4.6:tap:759,798 6.4:tap:735,528 8.2:tap:666,1902 10.4:swipe:1140,2385,330,2385,2600` (the last preset tap is Matrix, the user's theme) | 15 s |
| news-servers | NZBGet queue | `1.2:tap:1176,258 2.8:tap:315,2092 6.0:swipe:630,2200,630,1500,2000 10.0:swipe:630,2200,630,1300,2200` | 14 s |
| jellyfin-remote | Jellyfin → Now Playing | `4.0:tap:151,1215 8.0:tap:440,2000 11.5:tap:938,2291` | 13 s |
| push | services drawer | `1.0:swipe:630,2200,630,900,400 2.4:tap:929,2265 6.0:swipe:630,2200,630,1100,2200 10.0:swipe:630,2200,630,1100,2200` | 14 s |
| try-the-demo | add service form, demo mode off | `2.5:tap:630,2271 8.5:tap:84,258` | 13 s |
| lyrics | now playing (a demo album track) | `1.5:tap:549,2171` | 8.4 s |

Instant Mix was left out: in demo mode it visibly changes nothing.
