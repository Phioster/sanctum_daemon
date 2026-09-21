# UI smoke tests (maestro)

[maestro](https://maestro.mobile.dev) drives the installed app like a user, taps,
scrolls, asserts what's on screen. And fails loudly on a crash or a missing element.

App package id: **`org.phioster.nexarr`** (the installed id; the code namespace is
`org.phioster.sanctumd`).

## Run locally

```bash
# one-time install
curl -Ls "https://get.maestro.mobile.dev" | bash

# with a device/emulator connected and the app installed:
maestro test .maestro/01_launch_smoke.yaml     # single flow
maestro test .maestro/                          # every flow in order
```

Screenshots land next to where you run it (or under `~/.maestro`).

## The flows

| File | Needs | What it checks |
|------|-------|----------------|
| `01_launch_smoke.yaml` | nothing (fresh or configured) | app boots, first frame renders, no launch crash |
| `02_onboarding.yaml` | nothing (clears state itself) | all five intro pages, and that "get started" opens the first-service editor |
| `03_home_edit_addcard.yaml` | past onboarding, so after `02` | Edit mode reveals the Add-card FAB; drawer opens |
| `10_radarr_custom_search.yaml` | a Radarr service with a missing movie | the new **Custom search** entry shows in the Missing menu |

`01`, `02` and `03` are the CI gate (see `.github/workflows/ui-smoke.yml`), run in that
order on a clean emulator, so none of them may depend on a configured service. `02`
clears app state first, which is also what leaves `03` past onboarding. `10` needs a
real, configured device and carries its data assumptions in its comments.

## Extending for the other changes

- **Sonarr season interactive search:** open a Sonarr series → assert a `SEASON n`
  header is visible → tap its search icon → `assertVisible: "Releases"`.
- **Manual import assign:** Radarr → `⋮` → `Manual import` → type a folder → `Scan`
  → `assertVisible: "assign"` on an unmatched row.
- **Honest Prowlarr push:** open Prowlarr → `Search` → run a query → tap a release →
  `Send to Radarr` → assert the result text is `not added` / `grabbed`, never a bare
  `sent` for an untracked movie.

Selectors are matched by visible text or accessibility description; keep the on-screen
strings stable or update the flow when they change.
