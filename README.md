# GikyMessage

Compile a short Adventure template and bind its placeholder handlers once. Render it
with different contexts without selecting handlers by key again.

```java
record Account(String name, long balance) {}

Text<Account> message = Text.of("&a{name}: &e{balance}", key -> switch (key) {
    case "name" -> (context, value) -> value.text(context.name());
    case "balance" -> (context, value) -> value.number(context.balance());
    default -> null; // Literal {key} fallback.
});

Component alice = message.render(new Account("Alice", 10));
Component bob = message.render(new Account("Bob", 20));
```

`ResolverFactory<C>` is called once per unique compiled key, after parsing succeeds.
It returns a `ValueResolver<C>` that reads the current context. Repeated keys share one
handler call per render. No values are requested while binding. Reuse an existing
context object when available; the API does not require an allocation per render.

This API is source/binary incompatible with the previous key-based render entry point.
See [MIGRATION.md](MIGRATION.md) for conversion examples. Compile with the factory,
then pass data to `render(context)`.
The template syntax and rendered component structure are preserved. Performance
depends on value types, template structure and cache reuse.

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

Applies a drop shadow color when the receiving platform supports it.

```
$#RRGGBB   — full hex shadow color
$#RGB      — short hex shadow color
```

**Example:**
```
$#000000&#ffffff White text with black shadow
```

---

### Placeholders — `{key}`

```
{player}
{score}
{prefix}
```

Bound handlers supply placeholder values at render time. Following static text keeps the template style; replacement formatting does not leak into it.

**Example:**
```
&7Welcome, {player}&7!
```
Here `&7!` keeps the template's gray color even when the replacement has its own style.

---

### Bracketed segments — `[text](action:value)`

Applies click events, hover text, gradients, or object components to a span of text.

```
[Click me](action:value)
[Text](action1:value1, action2:value2)
[Text](action:"value with spaces", action2:value2)
```

Spaces are allowed in unquoted values. Quote values containing commas, or when leading
or trailing spaces must be preserved. Quoted values use `"` or `'`; escape the matching
quote or a backslash with `\`.

Placeholders are supported in `run`, `suggest`, `url`, `copy`, `show`, `head`, `style`
and `color`. `insert`, `page`, `sprite`, and the gradient color list are static. A
gradient's text content may still contain placeholders.

#### Available actions

| Action | Adventure mapping | Description |
|--------|------------------|-------------|
| `run` | `ClickEvent.runCommand(...)` | Run a command when clicked |
| `suggest` | `ClickEvent.suggestCommand(...)` | Put text into the player's chat box |
| `url` | `ClickEvent.openUrl(...)` | Open a URL in the browser |
| `copy` | `ClickEvent.copyToClipboard(...)` | Copy text to clipboard |
| `show` | `HoverEvent.showText(...)` | Show hover tooltip (supports full format syntax) |
| `insert` | `Style.insertion(...)` | Shift+click inserts text into chat |
| `page` | `ClickEvent.changePage(n)` | Turn to page `n` in a book |
| `style` | static or dynamic formatting | Color and decorations across the text |
| `color` | static or dynamic color | Uniform color across the text |
| `gradient` | per-code-point color interpolation | Color gradient across the text |
| `head` | `ObjectContents.playerHead(...)` | Insert a player head object |
| `sprite` | `ObjectContents.sprite(Key)` | Insert a texture sprite |

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

The gradient is applied per Unicode code point using linear interpolation across all
provided color stops, including text-node children of replacements.

---

### Combining actions

Multiple actions can be combined on one segment. One click action is selected in priority
order `run` → `suggest` → `url` → `copy` → `page`; one content action in priority order
`head` → `sprite` → `gradient`. The last occurrence of the selected action wins. The
last `show` and `insert` win independently. The first `style` and `color` win, and apply
after content processing in that order:

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

```java
// No bound handlers: placeholders remain literal.
Text<Void> literal = Text.of("Hello, {name}");
Component fallback = literal.render();

// Bind once; the registry is consulted only while compiling.
ResolverFactory<Account> factory = key -> switch (key) {
    case "name" -> (context, value) -> value.text(context.name());
    case "balance" -> (context, value) -> value.number(context.balance());
    default -> null;
};
Text<Account> text = Text.of("{name}: {balance}", factory);
Text<Account> cached = Text.cacheableOf("{name}: {balance}", factory);

Component current = text.render(account);
Component unresolved = text.render(); // Skips handlers, uses literal fallbacks.
```

`render(context)` passes the supplied context unchanged, including null. `render()`
always skips handlers. The no-factory `of(raw)` / `cacheableOf(raw)` forms compile
literal fallbacks and return `Text<Void>`; they do not support late key-based binding.

A null factory is rejected. A null handler, no reported value, `text(null)`, or
`component(null)` means literal `{key}`. An empty string/component means empty text.
The last report wins. The `Value` receiver is valid only during its handler call;
do not retain it. Text and numbers are used directly in text-only rendering paths;
components are created when needed. Styled components retain their structure.
Supplied values are not parsed as templates.

Binding follows compiler registration order, including keys from content later
replaced by an action. The factory is not retained by the text. Its returned handlers
are retained and may capture services. Replacing a registry entry does not replace a
bound handler; compile a new text to select new handlers. Changes in context data are
read by the existing handlers without rebinding.

A factory exception aborts preparation before any text is returned. A handler exception
stops the current render before later handlers, clears temporary references, and
leaves the last successful ordinary result cache available. No result is delivered
through a callback: rendering returns a Component to its caller.

### Last-result cache

`cacheableOf` stores one complete tuple/result per text and physical thread. Handlers
still run on every contextual render, before component equality is checked. Equal
component tuples reuse the result even across different report types or contexts;
A → B → A can evict A. `render()` participates using literal fallbacks. The context
object itself is not stored; cached components can retain data read from it.
This cache materializes values as components before comparison; cache misses still
create placeholder components.

### Cache for one context

Create a binding when the same context is rendered repeatedly:

```java
BoundText<Account> alice = message.bind(new Account("Alice", 10));
BoundText<Account> bob = message.bind(new Account("Bob", 20));
Component first = alice.render();
bob.render();
Component again = alice.render(); // same instance while Alice's values remain equal
```

`bind(context)` calls no handlers. Each `BoundText.render()` reads current data through
every shared handler, in the usual key order. Each binding retains its own values and
result; other contexts do not evict it. Native text/numbers are compared before creating
components. Unchanged materialized insertions are reused when another key changes;
the final template is rebuilt when the complete value tuple changes. Type transitions
use component equality. Null/no report still means literal fallback.

The binding holds a strong reference to its context. Keep it between renders and release
it when finished; creating it for every message defeats the cache. Its arrays and retained
values trade memory for less repeated work. All-changing values still pay comparison costs.
The binding uses its own cache even if created from `cacheableOf`, bypassing the ordinary
thread cache. It introduces no timers, periods or callback delivery.

Use each binding sequentially. Transfer between threads requires happens-before and a
valid execution context for its handlers. Recursive render of the same binding throws
`IllegalStateException`; other bindings and ordinary renders may nest. A handler or
renderer exception clears the binding's value/result references; the next render obtains
a complete fresh tuple. External handler effects are not rolled back.

### Ownership and execution

Compiled structure and bound handlers are shared. Temporary buffers are isolated by
physical thread and nesting depth and cleared after each render. Handlers and context
data must support the caller's execution context; a shared Text does not make external
APIs thread-safe. Texts have no periods, clocks, registrations or background work.
Number buffers are allocated lazily and reused with the other temporary buffers.

### Compiled work

Static components are constructed during compilation. Dynamic tokens preserve the
existing rendering rules. `StyleUtils` packs decoration state during compilation;
color caches have bounded slot counts and replace entries on collisions. Dynamic
gradients still depend on current content and length.

There is no fixed 16-key limit; the compiler grows its key table. Object components
such as head/sprite require a receiving platform that supports these objects. Java 21
and Adventure API are required. The build targets Adventure 5.2.0. The tested 4.26.1
path preserves that runtime's less strict head-name validation; exact results and
boundaries are described in [VALIDATION.md](VALIDATION.md).

### Dependencies and packaging

Adventure is a provided dependency: GM does not bundle it or choose the platform's
Adventure version. Consumers must declare/provide `net.kyori:adventure-api` themselves.
For a plugin whose platform supplies it:

```kotlin
dependencies {
    compileOnly("net.kyori:adventure-api:5.2.0")
    // Include GikyMessage from its JAR or your chosen publication repository.
}
```

Package GM with your application/plugin if the platform does not supply it. Do not
package a second copy of Adventure into a platform that already provides it. A
standalone application needs Adventure API on its runtime classpath. Gson and
MiniMessage are not required by GM at runtime; MiniMessage is used only in tests.

Build and check on Java 21:

```bash
./gradlew test javadoc jar sourcesJar javadocJar
```

The build produces `1.0.0-beta` prerelease artifacts in `build/libs`: the library JAR,
`GikyMessage-1.0.0-beta-sources.jar` and `GikyMessage-1.0.0-beta-javadoc.jar`.
Public release/publication configuration is prepared separately;
`publishToMavenLocal` is a local installation.

### Uniform color

```text
[Text](color:#55ff55)
[{faction}](color:{relation_color})
```

`color` accepts Minecraft color names (`red`, `dark_red`, case-insensitive),
legacy colors (`a`, `&a`, `§a`), hex colors (`#RGB`, `#RRGGBB`, `&#RGB`,
`&#RRGGBB`, and their `§` variants), and expanded hex (`&x&f&f&5&5&5&5`
or `§x§f§f§5§5§5§5`). A value must contain exactly one color.
Color placeholders are resolved at render time. Return the literal color code as the
placeholder component's text, for example `Component.text("&a")`.
The color overrides colors throughout the enclosed content, including placeholder
children, while preserving decorations and events. It does not split text into
individual characters. An invalid or unresolved color leaves the content unchanged.
`color` is applied after content actions, including `gradient`, `head`, and `sprite`.
The first `color` in an action list wins. Placeholder values are literal text,
not recursively parsed templates. The existing `cacheableOf` render cache also
covers color placeholders.

### Dynamic style

```text
[Text](style:&c&l)
[Text](style:"red bold italic")
[{name}](style:{format})
[Text](style:bold,color:{team_color})
```

`style` accepts every `color` notation, legacy decorations (`&k`, `&l`, `&m`,
`&n`, `&o`, also with `§`), and the words `obfuscated`, `bold`, `strikethrough`,
`underlined` (or `underline`), and `italic`. Words are case-insensitive and
separated by whitespace. Legacy codes can be adjacent. Values from placeholders
are parsed at render time as literal formatting values, not message templates.

Only specified properties override the enclosed content, including nested and
placeholder components. Other properties, including clicks, hover, insertion,
font and shadow, are retained. Colors keep previously specified decorations,
as in the regular GikyMessage text syntax. `&r`, `§r`, or `reset` sets white and
explicitly disables all decorations; subsequent properties can override this.
Invalid or unresolved values leave the content unchanged; empty styles do nothing.

`style` is applied after content actions and before `color`, regardless of their
order in the action list. Thus an explicit `color` overrides the color in `style`.
The first `style` and `color` entries win. An outer formatting action overrides
specified properties in nested blocks. Static values are parsed during compilation;
`cacheableOf` reuses its result while all placeholder values remain equal.
