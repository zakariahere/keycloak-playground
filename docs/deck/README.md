# Decks

Slide sources for talks generated out of this repo. **This folder is the
reference layout** — new decks follow the same shape.

```
docs/deck/
├── README.md                 you are here
├── .gitignore                png/, *.pptx, slide-base.css are all build output
└── <deck-name>/
    ├── NN-short-name.html        one slide, numbered so sort order == deck order
    └── NN-short-name.notes.txt   speaker notes for that slide (optional)
```

## What is committed, and what is not

Only the **HTML and the notes** are versioned. Everything else is a pure
function of them:

| Artefact | Versioned | Why |
| -------- | --------- | --- |
| `NN-*.html` | yes | The actual source. Edit these. |
| `NN-*.notes.txt` | yes | Speaker notes, one file per slide. |
| `png/` | no | ~2.5 MB per slide, regenerated every run. |
| `*.pptx` | no | ~23 MB, and derivable from the HTML. |
| `slide-base.css` | no | Copied in by the build. See below. |

`slide-base.css` is deliberately **not** committed. The build copies it in from
`~/.claude/skills/ppt/slide-base.css` on every run, and that file is the brand
kit (tokens lifted from `@zakaria/brand-kit` — bone paper, ink, one vermilion
signal; Fraunces / IBM Plex Mono / Hanken Grotesk). Committing a copy here would
fork the brand: decks would drift from the blog and the site. Edit the skill
copy, never a copy in this folder.

## Rebuild a deck

From the repo root:

```bash
pwsh -NoProfile -Command "& \"\$HOME/.claude/skills/ppt/make-deck.ps1\" -SlideDir \"\$PWD/docs/deck/intro-to-keycloak\" -Out \"\$PWD/docs/deck/intro-to-keycloak.pptx\" -Title 'Intro to Keycloak'"
```

Add `-PngOnly` to stop after rendering and eyeball the images first.

`$PWD` is not decoration: **`-SlideDir` must be absolute.** Pass a relative path
and headless Chrome resolves `--screenshot` against its own working directory,
writes the PNG somewhere else, and the script dies on
`Render produced no PNG for 01-title.html` — which reads like a broken slide
rather than a bad path.

## Rules that are easy to get wrong

- **Slide text is not editable in PowerPoint.** Each slide ships as a rendered
  PNG. To change a word, edit the HTML and rebuild. Say this when you hand a
  deck to someone.
- **Overflow is silent.** The canvas is a hard 1280&times;720; anything past it
  is clipped and the render still exits 0. Always render with `-PngOnly` and
  actually look at the output before building.
- **`ul.list li` AND `ol.steps li` are both `display:flex`.** Any inline
  `<code>`/`<strong>` inside a bullet becomes its own flex item with a gap, which
  shreds the line into staggered columns. Wrap the bullet content in a single
  `<span>` and add `li > span { display: block; }`. Bit us once per deck — see
  `intro-to-keycloak/11-gotchas.html` and
  `authorization-code-flow/08-callback.html`.
- **`.cols` has `flex:1`, so cards stretch and overflow silently.** If a slide has
  a `.cols` row *plus* anything below it, give the row `style="flex:0 0 auto"` and
  cut text until it fits. This is the failure the render will not tell you about.
- **Fonts load from Google Fonts over the network.** The build already passes a
  6 s virtual-time budget; shorten it and the whole deck renders in Times New Roman.
- **One idea in signal red per slide.** Two red things means neither reads as
  the point.

## Decks here

| Deck | Slides | About |
| ---- | ------ | ----- |
| [`intro-to-keycloak`](intro-to-keycloak/) | 12 | What a token is, what it costs, and where every auth failure comes from. Diagrams are inline SVG driven off the brand tokens; slide 10 is real captured output from the running lab. |
| [`authorization-code-flow`](authorization-code-flow/) | 11 | Lesson 001. The real authorization request parameter by parameter, the three bindings (state / nonce / code_challenge), and the PKCE + nonce hashes verified against the capture. |
