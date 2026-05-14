# GikyMessage

A **compile-once, render-many** text formatting library for [Adventure](https://github.com/KyoriPowered/adventure)-based Minecraft servers.

> **Why GikyMessage?**
> This library was built for a single, specific purpose: **reducing memory pressure and allocation overhead** under high-concurrency, high-frequency text rendering.
> It is **not** a replacement for MiniMessage, the legacy formatting API, or any other general-purpose format.
> If you need placeholder replacement called thousands of times per second on the same template string, this is for you.

---

## How it works

```java
// Compile once — the format string is parsed into a token tree
Text greeting = Text.of("&aHello, {player}! Your score: &e{score}");

// Render many times — no re-parsing, minimal allocation
Component result = greeting.render("player", playerName, "score", scoreComponent);
```

`Text.of(...)` builds an immutable token tree from the raw format string.
Every subsequent `render(...)` call walks that tree and substitutes placeholders without touching the original string.

For hot paths where the same placeholder value repeats frequently, use `Text.cacheableOf(...)` to reuse resolved components:

```java
Text msg = Text.cacheableOf("&7Prefix &r{player}&7: {message}");
```

---

## Format reference

### Color codes — `&`

| Code | Result |
|------|--------|
| `&0`–`&9`, `&a`–`&f` | Minecraft named colors (black → white) |
| `&#RRGGBB` | 24-bit hex color |
| `&#RGB` | Short hex color (expanded: `R` → `RR`, etc.) |
| `&r` | Reset all color and decorations |

**Examples:**
```
&cRed text
&#ff5500Orange text
&#f80Short orange
&r&aGreen after reset
```

**Code confirmation:** `Compiler.java` handles `&` followed by `#` via `parseHexPacked()` for hex and `ColorUtils.fromLegacyCode()` for named colors. `&r` explicitly zeroes `curColor`, `curDeco`, and `curShadow`.

---

### Decoration codes — `&`

| Code | Decoration |
|------|-----------|
| `&l` | **Bold** |
| `&o` | *Italic* |
| `&n` | Underlined |
| `&m` | ~~Strikethrough~~ |
| `&k` | Obfuscated (random chars) |

Decorations stack — `&l&o` gives bold italic. `&r` clears everything.

---

### Shadow color — `$`

Applies a drop shadow color (added in Minecraft 1.21.4 / Adventure 4.18+).

```
$#RRGGBB   — full hex shadow color
$#RGB      — short hex shadow color
```

**Example:**
```
$#000000&#ffffff White text with black shadow
```

**Code confirmation:** `Compiler.java` handles `$` + `#` via `parseHexPacked()`, storing the result as `ShadowColor` in `curShadow`. `ColorUtils.shadowColorOf()` provides a cached `ShadowColor` instance.

---

### Placeholders — `{key}`

```
{player}
{score}
{prefix}
```

Placeholders are resolved at render time via a `Resolver`. The placeholder's color and decorations are inherited by any static text that immediately follows it.

**Example:**
```
&7Welcome, {player}&7!
```
Here `&7!` is rendered as a child of the `{player}` component, inheriting its color.

**Code confirmation:** `Token.PlainDyn` implements the *placeholder-as-parent* model described in its Javadoc. `TextImpl.fillSlot()` handles null-resolution fallback.

---

### Bracketed segments — `[text](action:value)`

Applies click events, hover text, gradients, or object components to a span of text.

```
[Click me](action:value)
[Text](action1:value1, action2:value2)
[Text](action:"value with spaces", action2:value2)
```

Values containing spaces must be quoted with `"` or `'`. Placeholders (`{key}`) are supported inside values.

#### Available actions

| Action | Adventure mapping | Description |
|--------|------------------|-------------|
| `run` | `ClickEvent.runCommand(...)` | Run a command when clicked |
| `suggest` | `ClickEvent.suggestCommand(...)` | Put text into the player's chat box |
| `url` | `ClickEvent.openUrl(...)` | Open a URL in the browser |
| `copy` | `ClickEvent.copyToClipboard(...)` | Copy text to clipboard |
| `show` | `HoverEvent.showText(...)` | Show hover tooltip (supports full format syntax) |
| `insert` | `Style.insertion(...)` | Shift+click inserts text into chat |
| `page` | `ClickEvent.changePage(n)` | Turn to page `n` in a book/dialog |
| `gradient` | per-character color interpolation | Color gradient across the text |
| `head` | `ObjectContents.playerHead(...)` | Insert a player head object (1.21.5+) |
| `sprite` | `ObjectContents.sprite(Key)` | Insert a texture sprite (1.21.5+) |

**Examples:**

```
[Spawn](run:"/spawn")
[Warp](suggest:"/warp ", show:"Click to warp!")
[Docs](url:https://example.com)
[Copy IP](copy:play.example.com)
[Hover me](show:"&aGreen tooltip!")
[Shift-click](insert:"hello ")
[Turn page](page:2)
[Rainbow](gradient:#ff0000-#00ff00-#0000ff)
[{player}](head:{player})
[icon](sprite:minecraft:textures/item/diamond.png)
```

#### Gradient syntax

Colors are separated by `-`. Accepts full (`#RRGGBB`) or short (`#RGB`) hex, as well as legacy color letters (`a`, `c`, …).

```
[Gradient text](gradient:#ff0000-#ffff00-#00ff00)
[Short](gradient:#f00-#0f0)
[Legacy](gradient:c-e-a)
```

The gradient is applied per character using linear interpolation across all provided color stops.

**Code confirmation:** `Compiler.buildBracketToken()` parses all actions from the raw `char[]` without intermediate `String` allocation. `parseGradientColors()` handles the `-`-separated stop list. `Token.Gradient` and `Token.GradientDyn` implement per-character `ColorUtils.interpolate()`.

---

### Combining actions

Multiple actions can be combined on one segment:

```
[Click](run:"/spawn", show:"&aTeleport to spawn")
[Link](url:https://example.com, show:"Open in browser")
```

---

### Escape sequences

| Escape | Result |
|--------|--------|
| `\n` | Newline |
| `\[` | Literal `[` |
| `\]` | Literal `]` |
| `\{` | Literal `{` |
| `\}` | Literal `}` |
| `\\` | Literal `\` |

---

## Java API

### Compiling

```java
// Standard — no render caching
Text msg = Text.of("&aHello, {player}!");

// Cacheable — reuses last component for each placeholder when value unchanged
Text msg = Text.cacheableOf("&7{prefix} &r{player}&7: {message}");
```

### Rendering

```java
// No placeholders
Component c = msg.render();

// With a resolver
Component c = msg.render(resolver);

// Convenience overloads (1–10 key/value pairs)
Component c = msg.render("player", playerComponent);
Component c = msg.render("player", playerComponent, "score", scoreComponent);

// Map-based
Component c = msg.render(Map.of("player", playerComponent));
```

### Custom resolver

```java
Resolver r = key -> switch (key) {
    case "player" -> Component.text(player.getName()).color(NamedTextColor.GOLD);
    case "health" -> Component.text(String.valueOf(player.getHealth())).color(NamedTextColor.RED);
    default -> null;   // null = use sentinel fallback
};

Component c = msg.render(r);
```

---

## Design notes

**Token tree, not string manipulation.** The format string is parsed once into an immutable tree of `Token` objects. Rendering walks the tree — it never touches the original string again.

**Per-decoration bit packing.** `StyleImpl` stores all five `TextDecoration` states in a single `short` (2 bits each), reducing object count versus a standard `EnumMap`.

**Delta styling.** During compilation, style changes are stored as *deltas* relative to the inherited style. Unchanged properties produce no object allocation at render time.

**Color caching.** `ColorUtils.textColorOf(int)` and `ColorUtils.shadowColorOf(int)` cache instances by their integer value, so the same RGB never allocates a second `TextColor` or `ShadowColor`.

**Thread safety.** `TextImpl` uses `ThreadLocal<Component[]>` for its per-render placeholder buffer, making concurrent rendering from multiple threads safe without synchronization.

**Static caching.** Tokens that produce the same result regardless of placeholder values cache their `Component` on the first call.

---

## Limitations

- Placeholders are identified at compile time from the format string. Resolving a key that was not present in the original format string has no effect.
- Maximum placeholder count per format string: 16 (configurable in `Compiler.PH_CAP`).
- `head` with a dynamic player name requires the value to be wrapped in `{…}`: `[icon](head:{player})`.
- `sprite`, `head`, and `page` / `dialog` require a compatible Adventure + server version.
