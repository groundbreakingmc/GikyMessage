# Gradients

**English** · [Русский](ru/gradients.md) · [← Documentation](README.md)

A gradient fills a [segment](segments.md) with colors that blend smoothly from one to the next,
letter by letter. Write two or more colors joined by `-` in `color:`:

```text
[Skyblock Season 3](color:#55ffff-#5555ff)
```

![Skyblock Season 3 fading from aqua to blue](images/en/grad-intro.svg)

## Choosing colors

Each color is written as in [`color:`](colors.md#coloring-one-part-color-and-style): a hex code
such as `#ff5555` or `&#ff5555`, a code such as `c` or `&c`, or a name such as `red`:

```text
[&lRAINBOW SALE](color:#ff5555-#ffff55-#55ff55-#55ffff-#5555ff-#ff55ff)
[Royal Mage](color:gold-yellow)
```

![A bold rainbow and a gold-to-yellow title](images/en/grad-stops.svg)

Put no spaces around `-`. If the list isn't valid, the segment keeps the colors it had.

A gradient sets only the colors: `[&lRAINBOW SALE](color:…)` is bold and multicolored.

## Colors inside the segment

`color:` colors only the letters that have no color of their own. Codes inside the segment and
colored parts that placeholders insert, such as a highlighted mention, keep their colors, and the
gradient goes on after them. `force-color:` colors every letter:

```text
[Welcome to &eSkyCraft](color:#55ffff-#5555ff)         SkyCraft stays yellow
[Welcome to &eSkyCraft](force-color:#55ffff-#5555ff)   the whole line is a gradient
```

## Gradients in `style:`

The color in `style:` can be a gradient as well:

```text
[Event starts now!](style:"#ff5555-#ffaa00 bold")
```

When a segment has both, `color:` wins over the color in `style:`.

## Shadow gradients

`shadow:` takes a gradient of [shadows](colors.md#text-shadow) the same way. Each shadow is written
as in `shadow:`, with or without `$` and `#`, and may have a transparency after a colon:

```text
[&fSkyCraft](shadow:#ff5555-#5555ff)
[&fSkyCraft](shadow:#000000:80-#333333:40)
```

In `style:` a shadow starts with `$`, so a shadow gradient there does too:

```text
[SkyCraft](style:"#55ffff-#5555ff $#000000-#333333")
```

Letters with a shadow of their own keep it; `force-shadow:` covers every letter. Shadow gradients
need what shadows need: Minecraft 1.21.4 or newer for players and Adventure 4.18 or newer on the
server.

## With placeholders

The gradient is spread over the final text, so it fits a short name and a long one alike:

```text
&7Top killer: [{player}](color:#ff5555-#ffaa00)
```

![The gradient spans 'Steve' and also the longer 'xXDragonSlayerXx'](images/en/grad-placeholder.svg)

The colors can come from a placeholder too. With `{rank_colors}` = `#ff5555-#ffaa00` the name gets
a gradient, with `red` one color:

```text
[{player}](color:{rank_colors})
```

## A gradient that moves

`phase:` shifts the colors along the text. A whole cycle is `1`, so `phase:0.25` moves them a
quarter of the way, and `phase:1` looks the same as `phase:0`:

```text
[SkyCraft](color:#ff5555-#ffaa00-#ffff55, phase:0)
[SkyCraft](color:#ff5555-#ffaa00-#ffff55, phase:0.25)
[SkyCraft](color:#ff5555-#ffaa00-#ffff55, phase:0.5)
[SkyCraft](color:#ff5555-#ffaa00-#ffff55, phase:0.75)
```

![The same word with four phases: the colors shift along it](images/en/grad-phases.svg)

Show these one after another — in a boss bar, the tab list or a scoreboard title — and the colors
run along the text:

![Animation: the colors flow along the word SkyCraft](images/en/grad-animated.svg)

GikyMessage doesn't animate anything by itself: your plugin re-sends the text with the next phase.
Negative phases move the colors the other way. `phase:` shifts every gradient of the segment: the
colors and the shadows.

## Good to know

- A [translation](translations.md), a player head and a sprite can't be split into letters. A
  gradient gives them its first color.
- A part of a placeholder value that isn't plain text, such as a translated item name, gets the
  color at its place in the gradient.

## For developers

Here is how to animate the text of a [boss bar](https://docs.papermc.io/adventure/bossbar/): compile
the frames once, then switch between them on a timer.

```java
// 20 frames: the colors make a full round in one second at 20 ticks per second.
List<Text<Void>> frames = IntStream.range(0, 20)
        .mapToObj(i -> Text.of("[&lSkyCraft](color:#ff5555-#ffaa00-#ffff55, phase:" + i / 20.0 + ")"))
        .toList();

BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
AtomicInteger frame = new AtomicInteger();
Bukkit.getScheduler().runTaskTimer(plugin,
        () -> bar.name(frames.get(frame.getAndUpdate(i -> (i + 1) % frames.size())).render()), 0L, 1L);
```

A text without placeholders builds its component once, so each `render()` here returns a ready
object.

A color placeholder is reported as text, one color or a whole gradient, such as
`value.text("#ff5555-#ffaa00")`.
A segment remembers the last values it has read, so a value that stays the same isn't parsed
again.

---

[← Segments](segments.md) · [Documentation](README.md) · [Conditions →](conditions.md)
