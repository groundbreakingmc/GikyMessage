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

class TextStyleTest {

    @Test
    void acceptsWordsLegacyAndHexWithDecorations() {
        final Style expected = Style.style(NamedTextColor.RED, TextDecoration.BOLD);
        for (final String value : new String[]{"&c&l", "§c§l", "red bold", "RED BOLD", "#f55 bold",
                "&#ff5555&l", "§#f55§l", "&x&f&f&5&5&5&5&l", "§x§f§f§5§5§5§5§l"}) {
            assertEquals(expected, StyleUtils.parseStyle(value), value);
            final Component result = Text.of("[Hello](style:{format})").render("format", Component.text(value));
            assertEquals(Component.text("Hello", expected), result);
        }
    }

    @Test
    void staticStyleKeepsWholeTextAndReusesResult() {
        final Text text = Text.of("[Hello](style:\"red bold italic\")");
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
        final Text text = Text.cacheableOf("[[{name}](style:{format})](run:/{command},show:{hover})");
        final Component first = text.render("name", Component.text("Team"), "format", Component.text("&c&l"),
                "command", Component.text("team"), "hover", Component.text("Info"));
        assertSame(first, text.render("name", Component.text("Team"), "format", Component.text("&c&l"),
                "command", Component.text("team"), "hover", Component.text("Info")));
        final Component second = text.render("name", Component.text("Team"), "format", Component.text("&a&o"),
                "command", Component.text("team"), "hover", Component.text("Info"));
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
        final Component actual = Text.of("[{name}](style:{format})")
                .render("name", name, "format", Component.text("&c&l"));
        final Component expected = name.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .children(java.util.List.of(Component.text("!", NamedTextColor.RED).decorate(TextDecoration.BOLD)));
        assertEquals(expected.compact(), actual.compact());
    }

    @Test
    void resetClearsInheritedDecorationsButKeepsEvents() {
        final Component result = Text.of("&a&l&o[Text](style:&r&n,run:/test)").render().compact();
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
                    Text.of("[Text](" + actions + ")").render());
        }
    }

    @Test
    void unknownOrUnresolvedStylesDoNotPartiallyApply() {
        for (final String value : new String[]{"red unknown", "&c&z", "#abcxyz", "&x&f&f", "&c&", ""}) {
            assertEquals(Component.text("Text"), Text.of("[Text](style:{format})")
                    .render("format", Component.text(value)).compact());
        }
        assertEquals(Component.text("Text"), Text.of("[Text](style:{missing})").render().compact());
    }

    @Test
    void partialStylePreservesGradientColors() {
        final Component expected = Text.of("&l[AB](gradient:c-e)").render();
        final Component result = Text.of("[AB](gradient:c-e,style:bold)").render();
        assertEquals(expected.compact(), result.compact());
    }

    @Test
    void styleCanContainSeveralPlaceholders() {
        assertEquals(Component.text("Text", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Text.of("[Text](style:{color} {decoration})")
                        .render("color", Component.text("red"), "decoration", Component.text("bold")));
    }

    @Test
    void firstStyleWinsAndFormattingDoesNotLeak() {
        final Component result = Text.of("[A](style:red bold,style:green)B").render().compact();
        assertEquals(Component.empty().append(Component.text("A", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("B")), result);
    }
}
