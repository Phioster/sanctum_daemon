# sanctum_daemon

> **Sanctumd** — a unified, native Android dashboard for a self-hosted homelab:
> Jellyfin admin **and** the *arr / download stack in one app, with one
> consistent terminal theme. The daemon that guards your homelab.

Think "nzb360 + Jellyfin management", built as a single Compose app: you add
your services once, everything shows up in one terminal-styled UI, and actions
hit each service's API directly.

## Status

🚧 Early scaffold (v0.1.0). Round 1 is just the build pipeline + app shell.
Next: add-service flow + Jellyfin and Radarr integrations.

## Roadmap (MVP)

- [x] Project scaffold + CI build (debug APK artifact)
- [ ] Service config model (URL, API key, **custom headers** for Cloudflare Access)
- [ ] Jellyfin integration (sessions, library counts, scan) — via the official Kotlin SDK
- [ ] Radarr integration (status, wanted/queue, search)
- [ ] Unified dashboard with per-service accent colors
- [ ] More services: Sonarr, Prowlarr, NZBGet, Jellyseerr …

## Tech

Kotlin · Jetpack Compose · Material 3 · Jellyfin Kotlin SDK · Retrofit.
Built in CI (GitHub Actions) — no local Android SDK required to contribute.

## Build

Pushes trigger the **Build APK** workflow; grab the `nexarr-debug-apk` artifact.

## License

GPL-3.0 — see [LICENSE](LICENSE).
