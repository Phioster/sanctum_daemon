# v3.0 pre-release audit

State on 2026-10-17, branch `ccr-ba9f9ed2-5lsqw7`, app at **2.1.39** (versionCode 304).
Everything marked *verified* was checked in this session; *not verified* means it could not
be, and says why.

## Where things stand

| | |
|---|---|
| Last public release | **v2.1.6**, 2026-09-28 (phone + TV APK) |
| Code since then | 71 commits, 123 files, ~11 200 lines in `app/src/main`, versions 2.1.7 to 2.1.39 |
| Open pull requests | none. The work since `master` (#158) sits on this branch, unmerged |
| Pre-release `dev` | public, holds ~50 APKs named after feature branches |
| Tests | 543 `@Test` cases in `app/src/test` (README and site still say 339) |
| CI on this branch | Build APK (unit tests, detekt, lint, both flavours) and UI smoke **green** on every push of this session |

So "v3.0" is, for anyone outside, the jump from 2.1.6 to everything since. That is the
release's strength (a lot to show) and its main risk (none of it has been through a tag yet).

## Findings, by weight

### Must decide or fix before tagging

1. **Thirty-three versions have never been released.** 2.1.7 to 2.1.39 only exist as
   feature-branch builds. The daily use on one phone covers the phone flavour; the TV
   flavour changed too (subtitle/audio tracks on the TV, 2.1.26) and was last run on the
   box before that. *Recommendation:* tag a release candidate from the merged branch,
   install it over v2.1.6 on the phone and the TV box, then tag v3.0.0 from the same commit.
2. **Upgrade path not exercised.** Nothing changed the application id
   (`org.phioster.nexarr`), the DataStore names (`nexarr_services`, kept on purpose) or the
   signing setup, and versionCode only grew, so an in-place update from 2.1.6 *should* keep
   every service. *Not verified:* this environment has no device and no Android SDK. Users
   still on **2.1.0** must reinstall, because the signing key changed in 2.1.1; the release
   notes say so.
3. **The public `dev` pre-release.** Anyone who opens Releases sees ~50 APKs with names like
   `sanctumd-arr-paket-h.apk`. They are signed with the real key (built by `release.yml` with
   the secrets), so not a security hole, but they are unreviewed builds a stranger can
   install, and the page looks unmaintained next to a launch post. `release.yml` names each
   asset after the branch (`GITHUB_REF_NAME`), so they pile up instead of being replaced.
   *Recommendation:* empty the `dev` release before launch, and stage the asset under a fixed
   name (`sanctumd-dev.apk`) so it stays one file. Left to you: it deletes published files.

### Fixed in this session

4. **README out of date.** Version badge said 2.1.3, install commands used
   `sanctumd-v2.1.3.apk`, `./status` said "now at 2.1.3", the test count said 339, and the
   feature lists stopped around 2.1.6 (no demo mode, theme editor, Jellyfin server and
   transcoding settings, *arr tools, NZBGet volumes, lyrics). Rewritten from the changelog,
   claims only for what is in the code.
5. **Website the same.** `site/index.html`: test count, feature blocks, a "try the demo"
   pointer. The download cards already read `releases/latest`, so they follow the next tag
   by themselves.
6. **Delete without a question.** "Delete" in every service screen removed the service at
   once, one slip below "Edit". It now asks first (v2.1.37).

### Worth knowing, no action forced

7. **Share-menu reach.** To accept `.nzb` files from apps that don't know the type, the app
   registers for `application/octet-stream` shares (v2.1.38). Sanctumd therefore shows up when
   sharing any unknown file type; non-NZBs are refused with a message. If that annoys in
   daily use, drop the octet-stream line from the `SEND` filter; "open with" and the in-app
   picker keep working.
   **Done (after v3.0.1):** the octet-stream line is gone from the `SEND` filter.
8. **Posters in public material.** Demo screenshots show real film and series artwork
   (TMDb) and real actors' photos. That is how every media app presents itself and the
   README already says where it comes from, but for paid ads or a store listing it would
   need a second look. For README, site and community posts it is normal practice.
9. **"Tested against" is one homelab.** README and site say so plainly; keep it that way in
   every post. Server versions listed there are as recorded by you and were not re-checked.
10. **Unsigned fallback is marked.** Building `assembleRelease` without the secrets signs
    with the committed debug key and appends `-UNSIGNED-debugkey` to the version. Good; no
    change needed. Signing keys and secrets were not touched.

## What was verified, and how

| Check | Result | How |
|---|---|---|
| Unit tests, detekt, Android Lint, phone + TV compile | green on every push today, last on `fd7520a` | GitHub Actions *Build APK* |
| Launch, onboarding, home edit mode on an emulator | green | GitHub Actions *UI smoke (maestro)* |
| New code paths (service menu, NZBGet volumes/upload, lyrics/mix/similar) | covered by `NzbgetServersTest`, `JellyfinMusicMoreTest`, run through the demo backend | unit tests in CI |
| Demo mode end to end on a device | screenshots and video from the real debug APK | GitHub Actions *Screenshots*, emulator (Pixel 5 profile, API 33) |
| Upgrade 2.1.6 → current keeping services | **not verified** | needs a device |
| TV flavour on hardware | **not verified** since 2.1.26 | needs the TV box |
| Real servers for the new features | **not verified** here; field and route names were taken from the NZBGet and Jellyfin sources | needs your homelab |
| Release signing | not run (needs the secrets); configuration read, unchanged | `app/build.gradle.kts`, `release.yml` |

## Compatibility claims, checked against the source

- *Android 8.0+*: `minSdk = 26`. Correct.
- *Phone APK arm64-v8a only, TV APK arm64 + armeabi-v7a*: `abiFilters` in `app/build.gradle.kts`. Correct.
- *No analytics, no crash reporting*: no SDK of that kind in the dependencies. Hard-coded
  hosts in the code are `image.tmdb.org` (Seerr images, as the FAQ says) and the GitHub repo
  link in About (opened in the browser, not fetched). Correct as stated. One addition the
  FAQ does not mention: **demo mode** loads its posters from TMDb and its album covers from
  Apple's CDN (`mzstatic.com`); no user data goes with those requests.
- *Credentials AES-256-GCM in the Android Keystore*: `security/Crypto.kt`, `data/ServiceStore.kt`. Correct.
