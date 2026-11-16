# GikyMessage documentation

**English** · [Русский](ru/README.md) · [← Back to the project](../README.md)

GikyMessage turns short strings like `[&a&nSpawn](run:/spawn)` into Minecraft chat components. The
documentation has two halves: the **format**, for anyone who writes messages, and the **Java API**,
for plugin developers.

## Start here

- **[Quick start](quick-start.md)** — build your first colored, clickable message in five minutes.
- **[Cheat sheet](cheatsheet.md)** — the whole format on one page.
- **[llms.txt](../llms.txt)** — the whole format in one file for AI assistants, with the rules for
  converting MiniMessage and `&` codes.

## The format

| Page | What it covers |
|---|---|
| [Colors and styles](colors.md) | `&a`, hex colors like `&#ff8800`, bold and italic, text shadows |
| [Placeholders](placeholders.md) | `{player}` and other values that the plugin fills in when it sends the message |
| [Segments](segments.md) | Parts that react to the mouse: `[text](run:/spawn, show:tooltip)`, player heads, fonts |
| [Gradients](gradients.md) | Smooth color transitions, and gradients that move |
| [Conditions](conditions.md) | Show a part only when a value is set or matches: clan tags, ranks, online status |
| [Translations](translations.md) | Text in each player's own game language |
| [Transforms](transforms.md) | `1234567` → `1.2M`, UPPER CASE, ꜱᴍᴀʟʟ ᴄᴀᴘꜱ, length limits |
| [Escaping](escapes.md) | Writing `[`, `{` and `\` as plain text, line breaks, quotes in YAML |

## Practice

- **[Recipes](recipes.md)** — ready-to-copy messages: chat format, join messages, teleport requests,
  shop, help menu and more.
- **[Troubleshooting](troubleshooting.md)** — answers to "why does my message look like this?".

## For developers

- **[Java API](java-api.md)** — compiling and rendering, reporting values, bindings for frequent
  updates, threads, errors and version requirements.
