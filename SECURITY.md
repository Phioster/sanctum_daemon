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

Sanctumd has **no backend and no account**. It talks directly to the services
*you* configure. Relevant areas include:

- **Credential storage.** The services blob is AES-256-GCM encrypted with a
  key held in the Android Keystore; the notification-settings blob (ntfy token)
  the same way. Both are excluded from cloud backup and device transfer.
- **Portable export.** Password-based AES-256-GCM (PBKDF2-HMAC-SHA256), so the
  file can be decrypted only with the password, on any device.
- **App lock.** The optional biometric lock gates the *screen*, not the data.
  The Keystore key is not bound to user authentication, because live push,
  home-screen widgets and downloads have to decrypt while the phone is locked
  in your pocket, that is the whole point of them. So the lock keeps someone
  holding your unlocked phone out of the UI; it is not a defence against an
  attacker who already runs code as the app's user. With the lock on, the task
  switcher no longer keeps a thumbnail of the last screen either (Android 13 and
  up); screenshots stay available on purpose.
- **Media session.** The background music session accepts any controller, as
  every media app does: the lock screen, Bluetooth headsets and car head units
  all reach it through the platform session. What that exposes is the title and
  artist of what is playing. It used to expose more: the artwork URL carried
  the Jellyfin token, and *that* is the part that was fixed: the credential now
  travels as a request header and is not in the metadata at all. A callback
  admitting only this package shipped in 1.59.0 and was withdrawn in 2.0: media3
  gives the platform session a fixed sentinel package name, so the callback shut
  out the lock screen along with everyone else.
- **Network.** Every request goes straight to your configured service over the
  URL and headers you set (Cloudflare Access supported). Cleartext HTTP is
  permitted because self-hosted LAN services commonly use it.

Out of scope: the security of the third-party services you connect to
(Jellyfin, the \*arr apps, ntfy, etc.) and of the network between the app and
those services.

## Supported versions

Sanctumd ships as a rolling latest build; fixes land in the newest version.
Please reproduce on the latest release before reporting.
