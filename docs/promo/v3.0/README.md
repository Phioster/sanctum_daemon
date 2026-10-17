# Sanctumd v3.0: launch kit

Everything for announcing 3.0, in one place. Nothing in here has been published.

| File | What it is |
|---|---|
| [`audit.md`](audit.md) | Pre-release audit: state, risks, what was verified and what was not |
| [`positioning.md`](positioning.md) | Headline, tagline, differentiators, honest limits, wording per channel |
| [`release-notes-v3.0.0.md`](release-notes-v3.0.0.md) | GitHub release text; copy to `.github/release-notes/v3.0.0.md` before tagging |
| [`announcements.md`](announcements.md) | Short announcement for forums, a social post |
| [`community.md`](community.md) | Where to post, in which order, a post written for each place, answers to the obvious questions |
| [`launch-plan.md`](launch-plan.md) | Before, on and after release day; feedback without telemetry |
| [`video.md`](video.md) | Demo video: storyboard, how it is recorded, captions |
| `graphics/` | Rendered images (PNG), see below |
| `graphics/src/` | Their editable sources: plain HTML + CSS, using the real screenshots |

## Graphics

| File | Size | Use |
|---|---|---|
| `graphics/social-preview.png` | 1280×640 | GitHub social preview, link cards |
| `graphics/launch-banner.png` | 1500×500 | Release page, X/Mastodon/Bluesky header (keep the bottom-left free of text: the avatar sits there) |
| `graphics/feature-showcase.png` | 1920×1080 | One image with six screens and what each does, for posts and the site |
| `graphics/end-card.png` | 1080×2340 | Last frame of the demo video |

Every phone screen in them is a real screenshot of the app in demo mode (`docs/screenshots/`).

### Re-rendering after an edit

The sources are HTML because that is what the site is made of and anyone can edit it.
Render with any Chromium:

```console
$ npx playwright screenshot --viewport-size=1280,640 \
    "file://$PWD/docs/promo/v3.0/graphics/src/social-preview.html" docs/promo/v3.0/graphics/social-preview.png
```

(Same for the others with their sizes from the table.) Opening the HTML in a browser at that
window size and taking a screenshot works too.

## Video

`make-video.sh` turns the recording from the *Screenshots* workflow (or, with `--slides`,
the screenshots) into `sanctumd-3.0-demo.mp4` with captions (`captions.ass`) and the end card.
