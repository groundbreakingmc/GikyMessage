# Transforms

[← Documentation](README.md)

`transform:` changes the text of a [segment](segments.md) right before it's shown — mostly useful
for values that come from placeholders, which you can't edit yourself:

```text
&7Balance: &e[{balance}](transform:compact) &7coins
```

![Balance: 1.2M coins](images/en/tr-intro.svg)

## The steps

| Step | Does | Example |
|---|---|---|
| `compact` | Shortens numbers of 1000 and more | `1234567` → `1.2M` |
| `upper` | UPPER CASE | `Steve` → `STEVE` |
| `lower` | lower case | `Steve` → `steve` |
| `smallcaps` | Small capitals | `Steve` → `ꜱᴛᴇᴠᴇ` |
| `max:N` | Keeps at most `N` characters | `max:6`: `xXDragonSlayerXx` → `xXDrag` |

To use several steps, put them in quotes, separated by spaces. They run from left to right, and
`max` always runs last:

```text
[{player}](transform:"smallcaps max:10")
```

## Real-life examples

```text
[&6&l{server}]  &7Online: &f[{online}](transform:compact)
&7Top: &f[{player}](transform:max:8)&7 — &e[{kills}](transform:compact) kills
[&b{rank}](transform:smallcaps) &f{player}
```

![A scoreboard-like title with a compact online count, a top line with a shortened name, and a rank
in small capitals](images/en/tr-examples.svg)

## How `compact` counts

| Value | Shown |
|---|---|
| `999` | `999` (below 1000 nothing changes) |
| `1000` | `1K` |
| `1050` | `1.1K` |
| `1234567` | `1.2M` |
| `999999` | `1M` |
| `-1500` | `-1.5K` |
| `1234.56` | `1.2K` |
| `3500000000` | `3.5B` |

It uses `K`, `M`, `B` and `T`, keeps one decimal and rounds half up. Numbers glued to letters stay
as they are, so a name like `Steve1234` isn't touched, while every separate number in the text is
shortened: `1234567 coins and 2000 gems` → `1.2M coins and 2K gems`.

## Good to know

- `smallcaps` changes only the Latin letters `a`–`z`; Cyrillic and other letters, digits and symbols
  stay as they are. The letter `x` has no small capital and stays `x`.
- `max` counts characters in the whole segment, colors aside, and doesn't add `…`.
- Unknown step names are ignored, as are `max:0` and `max:` with something other than a number.
- Transforms run before a [gradient](gradients.md) and a [translation](translations.md), so
  `[{name}](transform:upper, gradient:#ff5555-#5555ff)` colors the upper-case text.

---

[← Translations](translations.md) · [Documentation](README.md) · [Escaping →](escapes.md)
