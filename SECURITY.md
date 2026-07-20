# Security Policy

Sanctumd stores the credentials for your self-hosted services (API keys,
Cloudflare Access tokens, Jellyfin passwords, ntfy tokens). Security reports
are taken seriously.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report privately via GitHub's **[Report a vulnerability](https://github.com/Phioster/sanctum_daemon/security/advisories/new)**
(repo → *Security* tab → *Report a vulnerability*). That keeps the details
private until a fix is available.

Please include, where possible:
- affected app version (Settings → About),
- Android version / device,
- steps to reproduce or a proof of concept,
- the impact you observed.

You can expect an initial response within a few days. Once a fix ships, credit
is happily given in the release notes unless you prefer to stay anonymous.

## Scope

Sanctumd has **no backend and no account** — it talks directly to the services
*you* configure. Relevant areas include:

- **Credential storage** — the services blob is AES-256-GCM encrypted with a
  key held in the Android Keystore; the notification-settings blob (ntfy token)
  the same way. Both are excluded from cloud backup and device transfer.
- **Portable export** — password-based AES-256-GCM (PBKDF2-HMAC-SHA256), so the
  file can be decrypted only with the password, on any device.
- **Network** — every request goes straight to your configured service over the
  URL and headers you set (Cloudflare Access supported). Cleartext HTTP is
  permitted because self-hosted LAN services commonly use it.

Out of scope: the security of the third-party services you connect to
(Jellyfin, the \*arr apps, ntfy, etc.) and of the network between the app and
those services.

## Supported versions

Sanctumd ships as a rolling latest build; fixes land in the newest version.
Please reproduce on the latest release before reporting.
