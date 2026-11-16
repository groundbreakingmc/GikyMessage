# Translations

[← Documentation](README.md)

`lang:` makes a [segment](segments.md) appear in each player's own game language. The server sends a
translation key, and every player's game looks it up in the language they play in:

```text
&7You found a [&bDiamond](lang:item.minecraft.diamond)&7!
```

![The same message seen by a player with English and with Russian game language: Diamond and
Алмаз](images/en/lang-intro.svg)

## Where the keys come from

- **The game's own keys** cover every item, block, mob and effect: `item.minecraft.diamond_sword`,
  `block.minecraft.chest`, `entity.minecraft.creeper`. All of them are listed in the English
  language file inside the game jar, `assets/minecraft/lang/en_us.json`.
- **Your own keys** come from a server resource pack, with one [language
  file](https://minecraft.wiki/w/Resource_pack#Language) per language:

`assets/myserver/lang/en_us.json`
```json
{ "myserver.welcome": "Welcome to the server!" }
```

`assets/myserver/lang/ru_ru.json`
```json
{ "myserver.welcome": "Добро пожаловать на сервер!" }
```

Then use the key in a message:

```text
[&aWelcome to SkyCraft!](lang:myserver.welcome)
```

![With the resource pack each player sees the translation in their language; without it, the text
from the brackets](images/en/lang-fallback.svg)

## The text in brackets is the backup

Players whose game doesn't know the key — for example, because they declined the resource pack — see
the text inside the brackets, as in the last line above. Write it in your server's main language.
The [backup text](https://minecraft.wiki/w/Text_component_format#Translated_Text) needs Minecraft
1.19.4 or newer; older versions show the key itself.

## Keys with placeholders

The key can be built from placeholders, so one line covers many translations:

```text
&7Rank: [&6{rank}](lang:myserver.rank.{rank})
```

With `{rank}` = `vip` the game looks up `myserver.rank.vip`, and players without it see `vip`.

## Good to know

- The translation takes the segment's style as a whole: write `[&6VIP](lang:…)` or
  `&6[VIP](lang:…)`. Colors in the middle of the text, as in `[V&cIP](lang:…)`, are dropped.
- A segment shows one kind of content. If it has several of `head:`, `sprite:`, `lang:` and
  `gradient:`, only the first one in that list is used, so a translated segment can't have a
  gradient. `color:` and `style:` work with `lang:` as usual.
- If the key is empty, for example because the placeholder is empty, the segment stays ordinary
  text.

> [!NOTE]
> Translations need Adventure 4.13 or newer on the server; otherwise the message fails to load with
> an error that says so.

---

[← Conditions](conditions.md) · [Documentation](README.md) · [Transforms →](transforms.md)
