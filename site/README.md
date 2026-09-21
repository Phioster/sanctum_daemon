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

## Where the look comes from

Not invented here. The tokens, the idioms and the effects are lifted from the homelab
dashboard's own `config/custom.css` (gethomepage), so the site and the dashboard read as
one thing:

```text
#00ff41 / #00cc33      text and muted text
#000000 on #020805     panels on background
'Courier New' first    then JetBrains Mono, then ui-monospace
> heading_             group titles as a shell prompt, blinking underscore
./name                 names as executables
# text                 descriptions as shell comments
[OK] [DOWN] [..]       status as terminal tags, never coloured dots
[ 21.09.26, 23:23 ]    the clock, with a blinking colon
-> link                links with an arrow
vignette, no scanlines scanlines were deliberately removed there on 2026-07-09
```

Two-layer phosphor glow on headings (`0 0 6px` + `0 0 20px`) and CRT bloom on panels
(outer `20px` plus `inset 22px`). Prefixes are dropped below 640 px, as they are there.

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
`ascii-typewriter` character set and the `ascii` edge set, with the edge angle bucketed the
way `EdgeGlyphs.glyphFor` does it.

It is deliberately pure ASCII. The `box` edge set looks better, but its diagonals
(`U+2571`, `U+2572`) are missing from `DroidSansMono` — the font Android maps `monospace`
to — so they would be pulled from a fallback font at a different advance width and the
whole grid would shear. Checked against the fonts on a real device, not assumed.

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
