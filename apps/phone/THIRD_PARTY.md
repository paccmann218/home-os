# Third-party assets in the HomeOS Bridge app

## Fonts

Both are bundled as variable fonts in `app/src/main/res/font/`. Variable fonts are used so one file
covers every weight the UI needs, and so Inter's optical-size axis can differ between the small
labels and the large readouts.

| File | Family | Licence | Source |
|---|---|---|---|
| `inter_variable.ttf` | Inter v4 (axes `opsz` 14–32, `wght` 100–900) | SIL Open Font License 1.1 | [google/fonts `ofl/inter`](https://github.com/google/fonts/tree/main/ofl/inter) |
| `jetbrains_mono_variable.ttf` | JetBrains Mono (axis `wght` 100–800) | SIL Open Font License 1.1 | [google/fonts `ofl/jetbrainsmono`](https://github.com/google/fonts/tree/main/ofl/jetbrainsmono) |

The OFL permits bundling and redistribution in an application. Neither font is modified, and
neither is renamed, so no Reserved Font Name restriction applies.

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
