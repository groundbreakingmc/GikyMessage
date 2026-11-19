# Troubleshooting

**English** · [Русский](ru/troubleshooting.md) · [← Documentation](README.md)

Find the symptom, then the cause and the fix.

- **Formatting:** [a style or shadow disappears after a
  color](#a-style-or-a-shadow-disappears-after-a-color) · [a hex color
  shows up as text](#a-hex-color-shows-up-as-text) · [a gradient doesn't
  appear](#a-gradient-doesnt-appear) · [a custom shadow isn't
  visible](#a-custom-shadow-isnt-visible)
- **Text:** [`{player}` instead of the name](#the-message-shows-player-instead-of-the-name) ·
  [square brackets disappear](#square-brackets-disappear) · [a number or a price
  disappears](#a-number-or-a-price-disappears) · [`&c` in a value isn't a
  color](#c-in-a-value-shows-up-as-text) · [an extra
  space](#theres-an-extra-space-when-a-value-is-empty) · [a number comparison never
  holds](#a-number-comparison-never-holds)
- **Actions:** [a tooltip stops in the middle](#a-tooltip-stops-in-the-middle) · [nothing happens on
  click](#nothing-happens-on-click) · [a head isn't shown](#a-player-head-isnt-shown)
- **Loading:** [YAML errors](#the-config-doesnt-load-yaml-error) ·
  [`TextFormatException`](#the-plugin-reports-textformatexception)

---

### A style or a shadow disappears after a color

**Cause:** a color code ends bold, italic, every other style and the custom shadow. In
`&l&6SkyCraft` the `&6` ends the bold before any text.

**Fix:** write the color first and the style or shadow after it: `&6&lSkyCraft`,
`&f$#ff5555SkyCraft`. [More](colors.md#turning-a-style-off).

### A hex color shows up as text

**Cause:** a hex code needs exactly six digits. Shorter ones such as `&#f80` aren't codes and stay
as text.

**Fix:** write all six digits: `&#ff8800`. [More](colors.md#any-color-hex-codes).

### A gradient doesn't appear

**Cause:** the color list isn't valid: it has fewer than two colors, a short hex code such as
`#f55`, or spaces around `-`. Or the letters have colors of their own: `color:` keeps them.

**Fix:** join full colors with `-`: `color:#ff5555-#5555ff` or `color:c-9`. To color every letter,
use `force-color:`. [More](gradients.md#choosing-colors).

### A custom shadow isn't visible

**Cause:** the player's game is older than 1.21.4, the shadow code doesn't have exactly six
digits, or a color code comes after the shadow code and ends it.

**Fix:** write six digits after `$#`, and the transparency after a colon: `$#000000:80`. Put the
shadow after the color: `&f$#000000:80Text`, not `$#000000:80&fText`.
[More](colors.md#text-shadow).

---

### The message shows `{player}` instead of the name

**Cause:** the plugin doesn't provide a placeholder with exactly this name. Names are
case-sensitive, so `{Player}` isn't `{player}`.

**Fix:** check the spelling against the plugin's list of placeholders. The literal `{player}` is
there on purpose, so that typos are easy to see.

### Square brackets disappear

**Cause:** `[` always starts a segment, so `[VIP]` becomes a segment with the text `VIP`.

**Fix:** write `\[VIP\]`. [More](escapes.md).

### A number or a price disappears

**Cause:** `#` or `$` followed by six digits (or letters from `a` to `f`) is a color or shadow code,
so `#123456` and `$100000` are read as codes.

**Fix:** put a backslash in front: `\#123456`, `\$100000`. [More](escapes.md).

### `&c` in a value shows up as text

**Cause:** values of placeholders are never read as formatting. That protects you from players who
put codes into their nicknames or messages.

**Fix:** nothing to change in the message. If a value is supposed to be colored, like a rank prefix,
the plugin has to pass it as formatted text ([for
developers](placeholders.md#formatted-values-such-as-prefixes)).

### There's an extra space when a value is empty

**Cause:** `{clan} {player}` keeps the space even when `{clan}` is empty.

**Fix:** hide the value together with its space: `[{clan} ](if:{clan}){player}`.
[More](conditions.md).

### A number comparison never holds

**Cause:** one side isn't a plain number: it's empty, or written as `1,000`, `1.2K` or `10%`.

**Fix:** make the plugin report the bare number, for example with `value.number(…)`.
[More](conditions.md#numbers----).

---

### A tooltip stops in the middle

**Cause:** a comma inside the parentheses separates actions, so `show:&7Click here, then read`
becomes the tooltip "Click here". A closing parenthesis in the text ends the whole list in the same
way.

**Fix:** put the text in quotes, `show:"&7Click here, then read"`, or write it without a comma.
[More](segments.md#tooltips-show).

### Nothing happens on click

Check these in order:

1. **One click per segment.** With two click actions only one is used, `run` before the others.
2. **Permission.** `run:` runs the command as the player, who needs permission for it.
3. **The link.** `url:` needs a full address with `https://`. A fixed link that isn't a valid
   address stops the message from loading; a link from a placeholder that isn't valid is simply left
   out.
4. **A comma.** `run:/say Hello, world` ends at the comma. Put such values in quotes:
   `run:"/say Hello, world"`.

### A player head isn't shown

**Cause:** heads need Minecraft 1.21.9 or newer on the player's side, and the name must be one a
player could have: at most 16 characters, without spaces or non-Latin letters. For other names the
segment shows its text instead.

**Fix:** check the client version and the name. [More](segments.md#player-heads-and-sprites).

---

### The config doesn't load: YAML error

**Cause:** the message is in double quotes, where YAML reads `\` as the start of [its own
escapes](https://yaml.org/spec/1.2.2/#57-escaped-characters) and doesn't accept `\[`; or an
apostrophe ends a [single-quoted](https://yaml.org/spec/1.2.2/#732-single-quoted-style) message
early.

**Fix:** use single quotes and write apostrophes twice: `'&7Don''t \[forget\]'`.
[More](escapes.md#yaml-use-single-quotes).

### The plugin reports `TextFormatException`

GikyMessage refuses to load a message that can't work, and the error says why:

| Message contains | Meaning |
|---|---|
| `Invalid click value` | A fixed link or page number isn't valid, for example `url:example com` or `page:0` |
| `Invalid head name` | A fixed player name in `head:` can't belong to a player |
| `Invalid font key`, `Invalid sprite key` | A key has upper-case letters, spaces or other characters that keys can't have |
| `… need Adventure 4.xx or newer` | The server's Adventure is too old for a feature the message uses |

Fix the part of the message it names, or update the server.
