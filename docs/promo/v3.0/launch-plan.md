# Launch plan

Small project, one maintainer: the plan has to fit evenings. Ordered by impact over effort.

## Phase 0: before the tag (about one week)

| Impact | Effort | Task |
|---|---|---|
| high | low | Open the PR from this branch to `master`, let CI run, merge |
| high | medium | Release candidate: run `release.yml` by hand on `master`, install the APK **over v2.1.6** on the phone; check that services, dashboard, themes and the ntfy subscription survived |
| high | medium | Same RC on the TV box: sign-in, play a film, switch audio/subtitle track |
| high | low | Empty the `dev` pre-release (or delete it) so Releases shows only real versions |
| medium | low | Repo settings: description and website from `positioning.md` (the description still says "dashboard"; the website field is empty), add topics `lidarr`, `prowlarr`, `ntfy`, `selfhosted` |
| medium | low | Set the social preview image (Settings → General → Social preview) to `graphics/social-preview.png` |
| medium | low | Copy `release-notes-v3.0.0.md` to `.github/release-notes/v3.0.0.md` |
| medium | low | Decide on the new screenshots (see `../../screenshots/v3.0/README.md`) and swap the README/site gallery |
| low | low | Pin an issue "3.0 feedback: tell me your setup" (template below) |

## Phase 1: release day

1. Bump to 3.0.0 (versionName, versionCode 305), changelog heading, merge.
2. Tag `v3.0.0`, let `release.yml` build and publish; check both APKs download and install.
3. Check the site (download cards read `releases/latest`, so they update themselves).
4. Post to **r/selfhosted** (text in `community.md`), early in the US morning or European
   afternoon on a weekday. Stay two hours for comments.
5. Social post (`announcements.md`) with the social preview image or the demo clip.

## Phase 2: the two weeks after

| Day | Task |
|---|---|
| +1 | Answer every comment and issue; fix what is quick, tag 3.0.1 if something blocks installing or upgrading |
| +2 | r/jellyfin post |
| +3 | ntfy docs PR, awesome-jellyfin PR |
| +4 | Lemmy (selfhosted, jellyfin) |
| +7 | Look at the numbers (below); write down what people asked for most |
| +8 to +10 | r/androidapps, only if the first posts went well |
| +14 | Short follow-up release (3.0.x or 3.1) with the most-asked fix, and a one-paragraph "thanks, here is what changed" comment in the original threads where that is welcome |

## Feedback and interest, without telemetry

The app stays without analytics. Everything below is already public or visible to you as
the repository owner.

- **Downloads per release:** `https://api.github.com/repos/Phioster/sanctum_daemon/releases`
  lists `download_count` per APK. The site's download cards already show it.
- **Traffic:** GitHub → Insights → Traffic: views, unique visitors, clones and referring
  sites for the last 14 days. Note the numbers on day 0, +3, +7 and +14; GitHub only keeps 14 days.
- **Stars and forks** on the same days.
- **Issues** labelled by setup (one label per service) show which integrations get used.
- **The pinned feedback issue**, template:

  > **3.0 feedback**
  > Which services do you run with it (and versions, if you know them)?
  > What did you try first? What did you miss?
  > Anything that broke? (A separate issue is better for bugs, with Settings → About's
  > version line.)

- **Post comments**: copy recurring requests into one issue each, so the conversation does
  not get lost on Reddit.

A plain table in a gist or in this folder (`metrics.md`, date / downloads / stars / visitors /
top referrer) is enough. Two weeks of it tells you which community is worth the next post.
