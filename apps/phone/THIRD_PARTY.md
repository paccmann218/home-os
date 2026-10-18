# Third-party assets in the HomeOS Bridge app

## Fonts

Both are bundled as variable fonts in `app/src/main/res/font/`. Variable fonts are used so one file
covers every weight the UI needs, and so Inter's optical-size axis can differ between the small
labels and the large readouts.

| File | Family | Licence | Source |
|---|---|---|---|
| `fraunces_variable.ttf` | Fraunces (axes `opsz` 9–144, `wght` 100–900, `SOFT`, `WONK`) | SIL Open Font License 1.1 | [google/fonts `ofl/fraunces`](https://github.com/google/fonts/tree/main/ofl/fraunces) |
| `inter_variable.ttf` | Inter v4 (axes `opsz` 14–32, `wght` 100–900) | SIL Open Font License 1.1 | [google/fonts `ofl/inter`](https://github.com/google/fonts/tree/main/ofl/inter) |
| `jetbrains_mono_variable.ttf` | JetBrains Mono (axis `wght` 100–800) | SIL Open Font License 1.1 | [google/fonts `ofl/jetbrainsmono`](https://github.com/google/fonts/tree/main/ofl/jetbrainsmono) |

The OFL permits bundling and redistribution in an application. None of the fonts is modified or
renamed, so no Reserved Font Name restriction applies.

**How the three are used.** Fraunces is the display voice: the wordmark, section headings, block
titles, avatar monograms and the large readouts. Its optical-size axis is the reason it was chosen
over a single-master display serif — the same family is set at 46sp and at 17sp and stays optically
correct either way. Inter carries body text, labels, captions and **all inline numbers**, because
Fraunces has no tabular-figure feature and a live value set in it would shift width as it ticks.
JetBrains Mono appears only in dev mode and on graph identifiers.

**Where else the fonts appear.** The notification layouts in `app/src/main/res/layout/` set
`android:fontFamily` to the same two bundled files, so a HomeOS notification is typeset like the
app: Inter for the collapsed glance, Fraunces for the expanded card. RemoteViews are inflated with
this package's resources, so no font is exported to the system.

## The app mark

`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` and
`ic_notification.xml` are **original work for this project** — hand-authored vector paths, not
derived from any third-party icon set, and not generated from a template. The mark is one node with
three joined to it: a household whose bodies are separate nodes and whose memory is a single graph.
The three satellites take the three semantic hues from `theme/Color.kt`, so the icon and the
interface speak the same vocabulary.

## Icons

`app/src/main/java/com/homeos/bridge/ui/icons/HomeOsIcons.kt` holds SVG path data for 40 icons taken
from **Material Symbols Rounded**, weight 400, which Google publishes under the
**Apache License 2.0** ([google/material-design-icons](https://github.com/google/material-design-icons)).

The path strings are baked into source and turned into `ImageVector`s by Compose's own `PathParser`.
This is deliberate: it keeps Google's icon geometry without shipping a ~4 MB icon font or pulling in
`material-icons-extended`, which is large and discouraged for production. Material Symbols authors
on a 960×960 grid with the origin at the bottom-left (`viewBox="0 -960 960 960"`), so each path is
drawn inside a group translated down by 960.

No emoji are used anywhere in this app.
