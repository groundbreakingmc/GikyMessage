package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextColorTest {
    @Test
    void acceptsLiteralColorFormatsWithoutSplittingText() {
        for (String value : new String[]{"a", "&a", "§a", "#5f5", "#55ff55", "&#55ff55", "§#5f5",
                "&x&5&5&f&f&5&5", "§x§5§5§f§f§5§5", "green", "GREEN"}) {
            Component result = Text.of("[Hello](color:" + value + ")").render();
            assertEquals("Hello", ((TextComponent) result).content());
            assertEquals(NamedTextColor.GREEN, result.color());
            assertTrue(result.children().isEmpty());
        }
    }

    @Test
    void resolvesColorAgainOnEachCachedRender() {
        Text text = Text.cacheableOf("[{name}](color:{relation})");
        for (String value : new String[]{"&a", "§c", "#123456"}) {
            Component result = text.render("name", Component.text("Team"),
                    "relation", Component.text(value));
            assertEquals(StyleUtils.parseColor(value), result.color());
        }
    }

    @Test
    void recolorsChildrenAndPreservesEventsAndDecorations() {
        Component name = Component.text("Team", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/team"))
                .append(Component.text("!", NamedTextColor.BLUE));
        Component result = Text.of("[{name}](color:{relation})")
                .render("name", name, "relation", Component.text("&a"));
        assertEquals(name.color(NamedTextColor.GREEN)
                .children(java.util.List.of(Component.text("!", NamedTextColor.GREEN))).compact(), result.compact());
    }

    @Test
    void invalidColorKeepsExistingFormatting() {
        Component name = Component.text("Team", NamedTextColor.RED);
        assertEquals(name.compact(), Text.of("[{name}](color:{relation})")
                .render("name", name, "relation", Component.text("invalid")).compact());
    }

    @Test
    void rejectsPartialColorsAndFormattingSequences() {
        for (String value : new String[]{"&c&l", "#abcxyz", "#12345", "red bold", "&x&1&2&3", ""}) {
            assertNull(StyleUtils.parseColor(value), value);
        }
    }

    @Test
    void preservesDynamicMetadataAndUsesExistingRenderCache() {
        Text text = Text.cacheableOf("[Team](color:{color},run:/{command},show:{hover})");
        Component first = text.render("color", Component.text("red"),
                "command", Component.text("team"), "hover", Component.text("Info"));
        assertEquals(NamedTextColor.RED, first.color());
        assertEquals(ClickEvent.runCommand("/team"), first.clickEvent());
        assertNotNull(first.hoverEvent());
        assertSame(first, text.render("color", Component.text("red"),
                "command", Component.text("team"), "hover", Component.text("Info")));
    }

    @Test
    void colorCanModifyGradientAndDoesNotEscapeItsBlock() {
        Component result = Text.of("&b[AB](gradient:c-e,color:green)C").render();
        assertEquals(Component.empty().append(Component.text("AB", NamedTextColor.GREEN))
                .append(Component.text("C", NamedTextColor.AQUA)), result.compact());
    }

    @Test
    void unresolvedColorKeepsContent() {
        Component result = Text.of("[Team](color:{missing})").render();
        assertEquals(Component.text("Team"), result.compact());
    }

    @Test
    void nestedDynamicColorIsNotFrozen() {
        Text text = Text.cacheableOf("[[Team](color:{relation})](run:/team)");
        Component first = text.render("relation", Component.text("&a"));
        Component second = text.render("relation", Component.text("&c"));
        assertNotEquals(first, second);
        assertEquals(ClickEvent.runCommand("/team"), first.clickEvent());
    }
}
