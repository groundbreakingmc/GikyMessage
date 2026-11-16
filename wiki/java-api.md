# Java API

**English** · [Русский](ru/java-api.md) · [← Documentation](README.md)

**On this page:** [a complete example](#a-complete-example) · [reporting
values](#reporting-values) · [your own contexts](#contexts-other-than-a-player) · [rendering
often](#rendering-often) · [checking configs](#checking-configs-with-keys) · [threads](#threads) ·
[errors](#errors) · [version requirements](#version-requirements) · [adding it to a
project](#adding-it-to-a-project)

## A complete example

Most plugins keep all messages in one file and share one set of placeholders between them:

```yaml
# messages.yml
join: '&8\[&a+&8\] &7{player} joined from &f{world}'
ping: '&7Your ping: [&a{ping} ms|&e{ping} ms|&c{ping} ms](if:{ping}<100, if:{ping}<250)'
```

```java
public final class Messages {

    // Every placeholder the plugin offers, in one place.
    private static final Map<String, ValueResolver<Player>> PLACEHOLDERS = Map.of(
            "player", (player, value) -> value.text(player.getName()),
            "world", (player, value) -> value.text(player.getWorld().getName()),
            "ping", (player, value) -> value.number(player.getPing())
    );

    private final Text<Player> join;
    private final Text<Player> ping;

    public Messages(ConfigurationSection config) {
        // A map works as a factory: names it doesn't contain stay as {name}.
        this.join = Text.of(config.getString("join"), PLACEHOLDERS::get);
        this.ping = Text.of(config.getString("ping"), PLACEHOLDERS::get);
    }

    public void sendJoin(Player player) {
        Bukkit.getServer().sendMessage(this.join.render(player));
    }

    public void sendPing(Player player) {
        player.sendMessage(this.ping.render(player));
    }
}
```

Create `Messages` again when the config is reloaded; there's nothing else to clean up.

## Reporting values

A handler, which your factory returns for a placeholder name, reports one value per render:

| Call | Use it for |
|---|---|
| `value.text(String)` | Plain text: names, worlds, anything players can influence |
| `value.number(long)` | Whole numbers such as balances, without turning them into strings first |
| `value.component(Component)` | A ready component with its own colors, events or translations |
| `value.component(String, Function<String, Component>)` | A formatted string and the parser for it |
| *nothing*, or `null` | Keeps `{key}` in the text |

GikyMessage never parses values, so `value.text("&cAdmin")` shows the characters `&cAdmin`. That's
what keeps player input safe. When a value *is* meant to be formatted, report it with its parser,
such as Adventure's [legacy serializer](https://docs.papermc.io/adventure/serializer/legacy/):

```java
private static final Function<String, Component> LEGACY =
        LegacyComponentSerializer.legacyAmpersand()::deserialize;

case "prefix" -> (player, value) -> value.component(prefixOf(player), LEGACY); // e.g. from LuckPerms
```

Keep the parser in a constant. A binding parses the string again only when the string or the parser
changes, and a lambda created on every call counts as a new parser.

### PlaceholderAPI as a fallback

A factory can hand every name it doesn't know to
[PlaceholderAPI](https://wiki.placeholderapi.com/developers/using-placeholderapi/), so
`{vault_eco_balance}` works in your messages:

```java
private static final Function<String, Component> SECTION =
        LegacyComponentSerializer.legacySection()::deserialize;

ResolverFactory<Player> factory = key -> {
    ValueResolver<Player> own = PLACEHOLDERS.get(key);
    if (own != null) return own;
    String papi = "%" + key + "%";
    return (player, value) -> value.component(PlaceholderAPI.setPlaceholders(player, papi), SECTION);
};
```

PlaceholderAPI returns text with `§` color codes, which is why the parser is `legacySection()`. Some
expansions only work on the main thread; keep that in mind if you render elsewhere.

## Contexts other than a player

The context can be any object. A record that holds everything a message needs is often the cleanest:

```java
record Purchase(Player buyer, int amount, String itemName, String itemKey, long price) {}

// Once, when the messages are loaded:
Text<Purchase> bought = Text.of(config.getString("bought"), key -> switch (key) {
    case "amount" -> (p, value) -> value.number(p.amount());
    case "item" -> (p, value) -> value.text(p.itemName());
    case "item_key" -> (p, value) -> value.text(p.itemKey());
    case "price" -> (p, value) -> value.number(p.price());
    default -> null;
});

// Every time someone buys something:
purchase.buyer().sendMessage(bought.render(purchase));
```

A chat format gets the message from Paper's
[`AsyncChatEvent`](https://docs.papermc.io/paper/dev/chat-events/) as a component and inserts it as
it is:

```java
record ChatLine(Player sender, Component message) {}

Text<ChatLine> format = Text.of(config.getString("chat"), key -> switch (key) {
    case "player" -> (line, value) -> value.text(line.sender().getName());
    case "message" -> (line, value) -> value.component(line.message());
    default -> null;
});

@EventHandler
public void onChat(AsyncChatEvent event) {
    event.renderer((sender, displayName, message, viewer) -> format.render(new ChatLine(sender, message)));
}
```

`render()` without a context skips the handlers, and every placeholder stays `{key}`. A text without
placeholders doesn't need a factory at all: `Text.of("&aSaved!")` returns a `Text<Void>` whose
`render()` gives the same prebuilt component every time.

## Rendering often

`render(context)` is cheap: it calls the handlers and assembles the result. For values that are
shown again and again, two tools save even that work.

| Situation | Use |
|---|---|
| A message sent now and then: join, command replies | `text.render(context)` |
| One context rendered repeatedly: tab list, scoreboard, boss bar of a player | `text.bind(context)` per player |
| Many renders in a row that usually end up with the same values | `Text.cacheableOf(…)` |
| The same component for everyone | render once and send it to all |

### The same player again and again: `bind`

A binding keeps the last values and the last result for one context. Each `render()` still asks the
handlers, but rebuilds nothing while the values stay equal, and returns **the same instance**. That
makes it easy to skip sending updates that change nothing:

```java
private Text<Player> footer; // compiled in onEnable, e.g. '&7Ping: &f{ping} ms  &7Online: &f{online}'
private final Map<UUID, BoundText<Player>> footers = new HashMap<>();
private final Map<UUID, Component> lastSent = new HashMap<>();

@EventHandler
public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    this.footers.put(player.getUniqueId(), this.footer.bind(player));
}

@EventHandler
public void onQuit(PlayerQuitEvent event) {
    // A binding holds on to its player; let it go when the player leaves.
    this.footers.remove(event.getPlayer().getUniqueId());
    this.lastSent.remove(event.getPlayer().getUniqueId());
}

// Every second, on the main thread:
for (Player player : Bukkit.getOnlinePlayers()) {
    Component footer = this.footers.get(player.getUniqueId()).render();
    if (footer != this.lastSent.put(player.getUniqueId(), footer)) {
        player.sendPlayerListFooter(footer); // only when something changed
    }
}
```

Keep the binding between renders: a new binding per render saves nothing. Use one binding from one
thread at a time.

### Same values, any context: `cacheableOf`

`Text.cacheableOf(template, factory)` compiles like `Text.of` and also remembers the last values and
result of each thread. When the next render, for whatever context, comes up with equal values, it
returns the previous component. It helps where the same values come in a row, such as a server-wide
line rendered for each player. For one context rendered repeatedly, a binding is the better tool.

## Checking configs with `keys()`

`keys()` lists the placeholder names of a template, in order. Use it to warn about typos when the
plugin loads, instead of letting players find `{plyer}` in chat:

```java
Text<Player> join = Text.of(raw, PLACEHOLDERS::get);
for (String key : join.keys()) {
    if (!PLACEHOLDERS.containsKey(key)) {
        getLogger().warning("messages.yml, 'join': unknown placeholder {" + key + "}");
    }
}
```

An empty list means `render()` always returns the same component.

## Threads

- A compiled `Text` can be shared between threads freely.
- Handlers run on the thread that renders. `AsyncChatEvent`, for example, isn't the main thread, so
  handlers used there must only call APIs that are safe off the main thread.
- A `BoundText` is meant for one thread at a time.

## Errors

**While compiling**, `Text.of` throws `TextFormatException` when a message can't work: a fixed link
that isn't a valid address, an invalid head name or key, or a feature the server's Adventure doesn't
have. The message names the problem. Catch it when loading, so that one broken line doesn't stop the
whole plugin:

```java
Text<Player> load(ConfigurationSection config, String path) {
    try {
        return Text.of(config.getString(path), PLACEHOLDERS::get);
    } catch (TextFormatException e) {
        getLogger().severe("Message '" + path + "' is broken: " + e.getMessage());
        return Text.of("&cMessage '" + path + "' is broken, see the console", key -> null);
    }
}
```

**While rendering**, a value from a placeholder that Adventure rejects, such as a link with spaces
or a head name with Cyrillic letters, never fails: that part simply stays plain text. An exception
thrown by your own handler stops the render and reaches the caller.

## Version requirements

GikyMessage needs Java 21. It is built against Adventure 5.2.0 and runs on older Adventure versions
too; a few features need a minimum version, and a template that uses them fails to compile with a
clear `TextFormatException` if the server's Adventure is older:

| Feature | Adventure on the server | Minecraft on the player's side |
|---|---|---|
| [`lang:` translations](translations.md) | 4.13 or newer | 1.19.4 or newer for the backup text |
| [`$#` shadow colors](colors.md#text-shadow) | 4.18 or newer | 1.21.4 or newer |
| [`head:` and `sprite:`](segments.md#player-heads-and-sprites) | 4.25 or newer | 1.21.9 or newer |

## Adding it to a project

GikyMessage is published on [JitPack](https://jitpack.io/#groundbreakingmc/GikyMessage). Put the
latest version from this badge in place of `VERSION`:
[![JitPack](https://jitpack.io/v/groundbreakingmc/GikyMessage.svg)](https://jitpack.io/#groundbreakingmc/GikyMessage)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.groundbreakingmc:GikyMessage:VERSION")
}
```

Maven, Groovy and how to bundle the library into your plugin's jar:
[Installation](../README.md#installation).

To build it yourself, run `./gradlew build`: it runs the tests and puts the jar into `build/libs`.
