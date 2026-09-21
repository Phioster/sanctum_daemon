# The public site

[sanctumd.dpdns.org](https://sanctumd.dpdns.org) — served by GitHub Pages straight out of
this folder. Three files and some images; no framework, no build step, no fonts or scripts
from anywhere else, so the page loads what it shows and nothing more.

```text
index.html          structure and every word on the page
assets/style.css    the app's terminal palette as tokens
assets/app.js       the moving parts (see below)
assets/shots/       screenshots, WebP at 560 px (2.6 MB of PNG -> ~200 KB)
assets/logos/       the eight service logos, 96 px
CNAME               the custom domain, read by Pages
```

## What is real and what is decoration

Everything live on the page comes from the **public GitHub releases API** at load time:
version, release date, APK size and the download count per file. If that request fails —
offline, or over the unauthenticated rate limit — the buttons fall back to the releases
page and the numbers show `—`, so the page stays usable.

Everything else is decoration with nothing behind it: the matrix rain, the gate building
itself, the typed `whoami`, the scanlines, the `[ok]` badges and the clock. The clock is
the visitor's own, not a server's.

No analytics, no cookies, no third-party requests.

## The `[fx]` button

Bottom right, cycling `dim → max → off` and remembered in `localStorage`. `dim` is the
default. `prefers-reduced-motion` starts it at `off` and keeps the rain off regardless.

## The header

The gate is the app icon (`docs/icon.png`) run through Glyphsmith's glyph pipeline: the
`ascii-typewriter` character set and the `box` edge set, with the edge angle bucketed the
way `EdgeGlyphs.glyphFor` does it. Its diagonals (`U+2571`, `U+2572`) are box-drawing, not
ASCII — which is why `DejaVu Sans Mono` leads the font stack, as it covers that block
fully.

## Deploying

`.github/workflows/pages.yml` publishes this folder on every push to `master` that touches
`site/`. Nothing else in the repo triggers it.

DNS lives at DigitalPlat and points the apex at GitHub Pages:

```text
A     @   185.199.108.153
A     @   185.199.109.153
A     @   185.199.110.153
A     @   185.199.111.153
```

## Editing the FAQ

The FAQ is written directly into `index.html`. It is the published copy — there is no
Markdown source that generates it.
