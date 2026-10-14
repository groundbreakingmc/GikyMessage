package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextStyleTest extends TextTestBase {

    @Test
    void acceptsWordsLegacyAndHexWithDecorations() {
        final Style expected = Style.style(NamedTextColor.RED, TextDecoration.BOLD);
        for (final String value : new String[]{"&c&l", "§c§l", "red bold", "RED BOLD", "#f55 bold",
                "&#ff5555&l", "§#f55§l", "&x&f&f&5&5&5&5&l", "§x§f§f§5§5§5§5§l"}) {
            assertEquals(expected, StyleUtils.parseStyle(value), value);
            final Component result = bound("[Hello](style:{format})").render((slotKey, slotValue) -> slotValue.component(slotKey.equals("format") ? Component.text(value) : null));
            assertEquals(Component.text("Hello", expected), result);
        }
    }

    @Test
    void staticStyleKeepsWholeTextAndReusesResult() {
        final Text<TestValues> text = bound("[Hello](style:\"red bold italic\")");
        final Component first = text.render();
        assertEquals("Hello", ((TextComponent) first).content());
        assertEquals(NamedTextColor.RED, first.color());
        assertEquals(TextDecoration.State.TRUE, first.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, first.decoration(TextDecoration.ITALIC));
        assertTrue(first.children().isEmpty());
        assertSame(first, text.render());
    }

    @Test
    void updatesStyleAndSharesExistingCache() {
        final Text<TestValues> text = cached("[[{name}](style:{format})](run:/{command},show:{hover})");
        final Component first = text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? Component.text("Team") : slotKey.equals("format") ? Component.text("&c&l") : slotKey.equals("command") ? Component.text("team") : slotKey.equals("hover") ? Component.text("Info") : null));
        assertSame(first, text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? Component.text("Team") : slotKey.equals("format") ? Component.text("&c&l") : slotKey.equals("command") ? Component.text("team") : slotKey.equals("hover") ? Component.text("Info") : null)));
        final Component second = text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? Component.text("Team") : slotKey.equals("format") ? Component.text("&a&o") : slotKey.equals("command") ? Component.text("team") : slotKey.equals("hover") ? Component.text("Info") : null));
        assertNotEquals(first, second);
        assertEquals(ClickEvent.runCommand("/team"), second.clickEvent());
        assertNotNull(second.hoverEvent());
        assertEquals(NamedTextColor.GREEN, second.compact().color());
    }

    @Test
    void preservesUnspecifiedFormattingAndEvents() {
        final Component name = Component.text("Team", NamedTextColor.BLUE)
                .decorate(TextDecoration.ITALIC)
                .clickEvent(ClickEvent.runCommand("/team"))
                .hoverEvent(HoverEvent.showText(Component.text("Info")))
                .insertion("insert")
                .font(Key.key("minecraft:uniform"))
                .shadowColor(ShadowColor.shadowColor(0xff112233))
                .append(Component.text("!", NamedTextColor.GREEN));
        final Component actual = bound("[{name}](style:{format})")
                .render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? name : slotKey.equals("format") ? Component.text("&c&l") : null));
        final Component expected = name.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .children(java.util.List.of(Component.text("!", NamedTextColor.RED).decorate(TextDecoration.BOLD)));
        assertEquals(expected.compact(), actual.compact());
    }

    @Test
    void resetClearsInheritedDecorationsButKeepsEvents() {
        final Component result = bound("&a&l&o[Text](style:&r&n,run:/test)").render().compact();
        assertEquals(NamedTextColor.WHITE, result.color());
        assertEquals(TextDecoration.State.FALSE, result.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.FALSE, result.decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE, result.decoration(TextDecoration.UNDERLINED));
        assertEquals(ClickEvent.runCommand("/test"), result.clickEvent());
        assertEquals(StyleUtils.parseStyle("&r&n"), StyleUtils.parseStyle("reset underlined"));
    }

    @Test
    void colorDoesNotResetDecorationsAndLaterValuesWin() {
        final Style result = StyleUtils.parseStyle("&l&c&r&a&o");
        assertEquals(NamedTextColor.GREEN, result.color());
        assertEquals(TextDecoration.State.FALSE, result.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, result.decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE, StyleUtils.parseStyle("&l&c").decoration(TextDecoration.BOLD));
    }

    @Test
    void explicitColorWinsOverStyleRegardlessOfOrder() {
        for (final String actions : new String[]{"color:green,style:red bold", "style:red bold,color:green"}) {
            assertEquals(Component.text("Text", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    bound("[Text](" + actions + ")").render());
        }
    }

    @Test
    void unknownOrUnresolvedStylesDoNotPartiallyApply() {
        for (final String value : new String[]{"red unknown", "&c&z", "#abcxyz", "&x&f&f", "&c&", ""}) {
            assertEquals(Component.text("Text"), bound("[Text](style:{format})")
                    .render((slotKey, slotValue) -> slotValue.component(slotKey.equals("format") ? Component.text(value) : null)).compact());
        }
        assertEquals(Component.text("Text"), bound("[Text](style:{missing})").render().compact());
    }

    @Test
    void partialStylePreservesGradientColors() {
        final Component expected = bound("&l[AB](gradient:c-e)").render();
        final Component result = bound("[AB](gradient:c-e,style:bold)").render();
        assertEquals(expected.compact(), result.compact());
    }

    @Test
    void styleCanContainSeveralPlaceholders() {
        assertEquals(Component.text("Text", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                bound("[Text](style:{color} {decoration})")
                        .render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("color") ? Component.text("red") : slotKey.equals("decoration") ? Component.text("bold") : null)));
    }

    @Test
    void firstStyleWinsAndFormattingDoesNotLeak() {
        final Component result = bound("[A](style:red bold,style:green)B").render().compact();
        assertEquals(Component.empty().append(Component.text("A", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("B")), result);
    }
}
