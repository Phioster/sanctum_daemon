# Handoff: v3.0 launch prep, to continue locally (Termux / ADB)

Written at the end of a cloud session that had no device access. Everything below is on
branch **`ccr-ba9f9ed2-5lsqw7`** (not merged, no PR yet). Start with `git fetch && git checkout ccr-ba9f9ed2-5lsqw7`.

## Ground rules from the maintainer (still apply)

- Do not set the version to 3.0, do not touch signing keys or secrets, do not merge PRs,
  do not publish a release or any post. Those decisions stay with the maintainer.
- Never fabricate screenshots or claim a check passed that was not run.
- Keep app changes separate from promo assets.

## Done

| What | Where | Verified |
|---|---|---|
| Tidier service menu (bottom sheet), remove asks first | v2.1.37, `ui/common/ServiceMenu.kt` | CI green (tests, detekt, lint, both flavours, UI smoke) |
| Paket N: NZBGet volume per news server, .nzb upload/share | v2.1.38 | CI green, `NzbgetServersTest` |
| Paket O: Jellyfin lyrics, Instant Mix, more like this | v2.1.39 | CI green, `JellyfinMusicMoreTest` |
| Audit | `audit.md` | read it first |
| Positioning, release notes draft, announcements, community posts, launch plan | this folder | text only, nothing posted |
| README + site brought up to 2.1.39 (badge, features, test count, demo pointer, install) | `README.md`, `site/index.html` | site rendered at 390 px, no overflow, no JS errors |
| Graphics: social preview, launch banner, feature showcase, video end card | `graphics/` + HTML sources in `graphics/src/` | maintainer approved banner; cursor in social preview moved behind "SANCTUMD" as asked |
| Video tooling: storyboard, captions, assembly script | `video.md`, `captions.ass`, `make-video.sh` | script syntax-checked; not yet run on real footage |
| Emulator capture workflow | `.github/workflows/screenshots.yml`, `.maestro/screenshots/` | two runs failed and were fixed (ABI → API 30; regex `?` in "try the demo"); the third run was still going at handoff, see below |

## Open, in priority order

### 1. Screenshots from the real phone (the maestro flow or by hand)

Check first whether the emulator run committed `docs/screenshots/v3.0/*.png` (commit
"Screenshots aus dem Demo-Modus (Emulator, …)" on the branch). Those are real captures of
the debug build, but from an emulator; the maintainer wants phone captures.

**With maestro** (a computer with the phone on adb): `gradle assemblePhoneDebug` then
`bash .maestro/screenshots/capture.sh`. It **wipes Sanctumd's data** on the device (the flow
starts from a clean install), so on a real phone it refuses unless run with `CONFIRM_WIPE=1`.
Export the config first (Settings → backup / data). Also: the debug build has the same
application id as the release but another signing key, so `adb install` fails over an
installed release; the release would have to be uninstalled first. For the phone that is in
daily use, the by-hand route below is the safer one.

**By hand with adb** (works from Termux with `pkg install android-tools`, wireless debugging
paired to the same phone):

```sh
# in the app: Settings → demo mode, then long-press the amber DEMO chip to hide it
adb exec-out screencap -p > 01_home.png     # repeat per screen below
```

Screens to capture (names match the flow and `make-video.sh`):

| File | How to get there |
|---|---|
| `01_home` | home tab with the universal calendar |
| `02_services` | ☰ Services drawer |
| `03_radarr_library` | Radarr |
| `04_radarr_service_menu` | Radarr → ⋮ (the new sheet) |
| `05_radarr_quality_profiles` | ⋮ → Quality profiles |
| `06_radarr_detail` | tap a film |
| `07_jellyfin_dashboard` | Jellyfin → Dashboard |
| `08_jellyfin_transcoding` | Dashboard → Transcoding tile |
| `09_jellyfin_now_playing` | Jellyfin → Now Playing |
| `10_nzbget_queue` | NZBGet |
| `11_nzbget_news_servers` | NZBGet → ⋮ → News servers |
| `12_seerr` | Seerr |
| `13_ntfy` | ntfy |
| `14_stats` | drawer → stats icon |
| `15_theme_presets` | drawer → ⚙ → theme |
| `16_theme_editor` | theme → new theme |
| also worth it | Now Playing music screen with **lyrics** open (play a demo album's track 1: timed lyrics) |

Then crop the status bar the way `docs/screenshots/README.md` describes (162 px on that
phone) and put the files in `docs/screenshots/v3.0/`. Check each for private data before
committing (demo mode shows none, but notifications or a status-bar icon can).

### 2. Write `docs/screenshots/v3.0/README.md`

Which of the eleven current README screenshots each new one replaces. Assessment from the
audit: the current eleven are recent (v2.1.33, demo mode, real phone) and fine; they only miss
2.1.34+ features. `themes.png` is the weakest (looks like `services.png`); replace it with the
theme editor. Add the service menu, NZBGet news servers, Jellyfin transcoding and lyrics.

### 3. Swap the new shots into the graphics

`graphics/src/feature-showcase.html` has a `SHOTS` list at the bottom; replace `themes.png`
(and maybe `media-detail.png`) with `v3.0/` captures, then re-render (command in `README.md`
here). Same idea for the README gallery and `site/assets/shots/` (WebP, 800 px wide:
`cwebp -resize 800 0 -q 80 in.png -o out.webp`).

### 4. Demo video

Either `adb shell screenrecord --time-limit 60 /sdcard/demo.mp4` while doing the storyboard
in `video.md` by hand on the phone (then `adb pull`), or let `capture.sh` record it. Save as
`docs/screenshots/v3.0/demo-raw.mp4` and run `docs/promo/v3.0/make-video.sh` (needs ffmpeg
with libass; Termux: `pkg install ffmpeg`). Without a usable recording: `make-video.sh --slides`.
Watch the result before keeping it; captions are timed in `captions.ass`.

### 5. Before tagging (maintainer decisions, see `launch-plan.md`)

PR to master, release candidate installed over v2.1.6 on phone and TV box, empty the public
`dev` pre-release, repo description/website/social preview, copy the release notes draft to
`.github/release-notes/v3.0.0.md`, then the 3.0.0 bump.

## Known loose ends

- The `dev` pre-release holds ~50 branch-named APKs; `release.yml` names them after the branch.
- The share filter for `application/octet-stream` makes Sanctumd appear for any unknown file
  shared; keep or drop (audit item 7).
- Community rules could not be read from the cloud session (Reddit blocked); check them on
  the day, `community.md` says what to look for.
