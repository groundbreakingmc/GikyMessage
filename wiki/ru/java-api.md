# Java API

[English](../java-api.md) · **Русский** · [← Документация](README.md)

**На странице:** [полный пример](#полный-пример) · [передача значений](#передача-значений) · [свои
контексты](#контексты-кроме-игрока) · [частый рендер](#частый-рендер) · [проверка
конфигов](#проверка-конфигов-через-keys) · [потоки](#потоки) · [ошибки](#ошибки) · [требования к
версиям](#требования-к-версиям) · [подключение к проекту](#подключение-к-проекту)

## Полный пример

Обычно плагин держит все сообщения в одном файле, и плейсхолдеры у них общие:

```yaml
# messages.yml
join: '&8\[&a+&8\] &7{player} зашёл из мира &f{world}'
ping: '&7Ваш пинг: [&a{ping} мс|&e{ping} мс|&c{ping} мс](if:{ping}<100, if:{ping}<250)'
```

```java
public final class Messages {

    // Все плейсхолдеры плагина в одном месте.
    private static final Map<String, ValueResolver<Player>> PLACEHOLDERS = Map.of(
            "player", (player, value) -> value.text(player.getName()),
            "world", (player, value) -> value.text(player.getWorld().getName()),
            "ping", (player, value) -> value.number(player.getPing())
    );

    private final Text<Player> join;
    private final Text<Player> ping;

    public Messages(ConfigurationSection config) {
        // Map подходит как фабрика: имена, которых в ней нет, остаются как {name}.
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

При перезагрузке конфига просто создайте `Messages` заново — больше ничего чистить не нужно.

## Передача значений

Обработчик — то, что ваша фабрика возвращает для имени плейсхолдера, — сообщает одно значение за
рендер:

| Вызов | Для чего |
|---|---|
| `value.text(String)` | Обычный текст: ники, миры, всё, на что могут влиять игроки |
| `value.number(long)` | Целые числа, например баланс, без предварительного превращения в строку |
| `value.component(Component)` | Готовый компонент со своими цветами, событиями или переводами |
| `value.component(String, Function<String, Component>)` | Отформатированная строка и парсер для неё |
| *ничего* или `null` | В тексте остаётся `{key}` |

GikyMessage никогда не разбирает значения, поэтому `value.text("&cАдмин")` покажет символы
`&cАдмин`. Именно это делает ввод игроков безопасным. Если же значение *должно* быть
отформатированным, передайте его вместе с парсером, например
[legacy-сериализатором](https://docs.papermc.io/adventure/serializer/legacy/) Adventure:

```java
private static final Function<String, Component> LEGACY =
        LegacyComponentSerializer.legacyAmpersand()::deserialize;

case "prefix" -> (player, value) -> value.component(prefixOf(player), LEGACY); // например, из LuckPerms
```

Храните парсер в константе. Привязка разбирает строку заново, только когда меняется строка или
парсер, а лямбда, созданная на каждый вызов, считается новым парсером.

### PlaceholderAPI для всего остального

Фабрика может отдавать
[PlaceholderAPI](https://wiki.placeholderapi.com/developers/using-placeholderapi/) все имена,
которых не знает сама, — тогда в сообщениях заработает, например, `{vault_eco_balance}`:

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

PlaceholderAPI возвращает текст с кодами цвета через `§`, поэтому парсер — `legacySection()`.
Некоторые расширения работают только в основном потоке; помните об этом, если рендерите где-то ещё.

## Контексты кроме игрока

Контекстом может быть любой объект. Часто удобнее всего запись (record), в которой есть всё нужное
сообщению:

```java
record Purchase(Player buyer, int amount, String itemName, String itemKey, long price) {}

// Один раз, при загрузке сообщений:
Text<Purchase> bought = Text.of(config.getString("bought"), key -> switch (key) {
    case "amount" -> (p, value) -> value.number(p.amount());
    case "item" -> (p, value) -> value.text(p.itemName());
    case "item_key" -> (p, value) -> value.text(p.itemKey());
    case "price" -> (p, value) -> value.number(p.price());
    default -> null;
});

// Каждый раз, когда кто-то что-то покупает:
purchase.buyer().sendMessage(bought.render(purchase));
```

Формат чата получает сообщение из [`AsyncChatEvent`](https://docs.papermc.io/paper/dev/chat-events/)
в Paper в виде компонента и вставляет его как есть:

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

`render()` без контекста не вызывает обработчики, и все плейсхолдеры остаются `{key}`. Тексту без
плейсхолдеров фабрика не нужна вовсе: `Text.of("&aСохранено!")` возвращает `Text<Void>`, чей
`render()` каждый раз отдаёт один и тот же заранее собранный компонент.

## Частый рендер

`render(context)` и так дешёвый: он вызывает обработчики и собирает результат. Для значений, которые
показываются снова и снова, есть два инструмента, которые экономят даже это.

| Ситуация | Что использовать |
|---|---|
| Сообщение время от времени: вход, ответы на команды | `text.render(context)` |
| Один контекст рендерится многократно: таб, скорборд, боссбар игрока | `text.bind(context)` для каждого игрока |
| Много рендеров подряд, которые обычно дают одинаковые значения | `Text.cacheableOf(…)` |
| Один и тот же компонент для всех | отрендерите один раз и отправьте всем |

### Один и тот же игрок снова и снова: `bind`

Привязка хранит последние значения и последний результат для одного контекста. Каждый `render()`
по-прежнему опрашивает обработчики, но ничего не пересобирает, пока значения те же, и возвращает
**тот же самый объект**. Благодаря этому легко пропускать обновления, которые ничего не меняют:

```java
private Text<Player> footer; // компилируется в onEnable, например '&7Пинг: &f{ping} мс  &7Онлайн: &f{online}'
private final Map<UUID, BoundText<Player>> footers = new HashMap<>();
private final Map<UUID, Component> lastSent = new HashMap<>();

@EventHandler
public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    this.footers.put(player.getUniqueId(), this.footer.bind(player));
}

@EventHandler
public void onQuit(PlayerQuitEvent event) {
    // Привязка держит своего игрока — отпускайте её, когда игрок выходит.
    this.footers.remove(event.getPlayer().getUniqueId());
    this.lastSent.remove(event.getPlayer().getUniqueId());
}

// Раз в секунду, в основном потоке:
for (Player player : Bukkit.getOnlinePlayers()) {
    Component footer = this.footers.get(player.getUniqueId()).render();
    if (footer != this.lastSent.put(player.getUniqueId(), footer)) {
        player.sendPlayerListFooter(footer); // только если что-то изменилось
    }
}
```

Храните привязку между рендерами: новая привязка на каждый рендер ничего не экономит. Используйте
одну привязку из одного потока за раз.

### Те же значения, любой контекст: `cacheableOf`

`Text.cacheableOf(шаблон, фабрика)` компилирует так же, как `Text.of`, и вдобавок запоминает
последние значения и результат каждого потока. Если следующий рендер — для любого контекста — дал те
же значения, возвращается прошлый компонент. Это помогает, когда одинаковые значения идут подряд,
например строка для всего сервера, которая рендерится для каждого игрока. Для одного контекста,
который рендерится снова и снова, лучше привязка.

## Проверка конфигов через `keys()`

`keys()` возвращает имена плейсхолдеров шаблона по порядку. Предупредите об опечатках при загрузке
плагина, а не тогда, когда игроки увидят `{plyer}` в чате:

```java
Text<Player> join = Text.of(raw, PLACEHOLDERS::get);
for (String key : join.keys()) {
    if (!PLACEHOLDERS.containsKey(key)) {
        getLogger().warning("messages.yml, 'join': неизвестный плейсхолдер {" + key + "}");
    }
}
```

Пустой список значит, что `render()` всегда возвращает один и тот же компонент.

## Потоки

- Скомпилированный `Text` можно свободно использовать из разных потоков.
- Обработчики выполняются в потоке, который рендерит. `AsyncChatEvent`, например, приходит не в
  основном потоке, так что обработчики там должны вызывать только то, что безопасно вне основного
  потока.
- `BoundText` рассчитан на один поток за раз.

## Ошибки

**При компиляции** `Text.of` выбрасывает `TextFormatException`, если сообщение не сможет работать:
постоянная ссылка, которая не является адресом, неверное имя головы или ключ, возможность, которой
нет в Adventure сервера. В тексте ошибки названа причина. Ловите её при загрузке, чтобы одна
сломанная строка не останавливала весь плагин:

```java
Text<Player> load(ConfigurationSection config, String path) {
    try {
        return Text.of(config.getString(path), PLACEHOLDERS::get);
    } catch (TextFormatException e) {
        getLogger().severe("Сообщение '" + path + "' сломано: " + e.getMessage());
        return Text.of("&cСообщение '" + path + "' сломано, подробности в консоли", key -> null);
    }
}
```

**При рендере** значение из плейсхолдера, которое Adventure отвергает, — ссылка с пробелами или имя
головы с русскими буквами — никогда не приводит к ошибке: эта часть просто остаётся обычным текстом.
А исключение из вашего собственного обработчика останавливает рендер и доходит до вызывающего кода.

## Требования к версиям

GikyMessage нужна Java 21. Библиотека собрана под Adventure 5.2.0 и работает и на более старых
версиях Adventure; нескольким возможностям нужна минимальная версия, и если Adventure на сервере
старее, шаблон с ними не скомпилируется — `TextFormatException` прямо скажет почему:

| Возможность | Adventure на сервере | Minecraft у игрока |
|---|---|---|
| [Переводы `lang:`](translations.md) | 4.13 или новее | 1.19.4 или новее для запасного текста |
| [Цвет тени `$#`](colors.md#тень-текста), `shadow:`, `force-shadow:`, [градиенты тени](gradients.md#градиенты-тени) | 4.18 или новее | 1.21.4 или новее |
| [`head:` и `sprite:`](segments.md#головы-игроков-и-спрайты) | 4.25 или новее | 1.21.9 или новее |

## Подключение к проекту

GikyMessage публикуется на [JitPack](https://jitpack.io/#groundbreakingmc/GikyMessage). Вместо
`VERSION` подставьте последнюю версию с этого значка:
[![JitPack](https://jitpack.io/v/groundbreakingmc/GikyMessage.svg)](https://jitpack.io/#groundbreakingmc/GikyMessage)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.groundbreakingmc:GikyMessage:VERSION")
}
```

Maven, Groovy и упаковка библиотеки в jar вашего плагина — в разделе
[Установка](../../README_RU.md#установка).

Чтобы собрать библиотеку самому, выполните `./gradlew build`: команда прогонит тесты и положит jar в
`build/libs`.
