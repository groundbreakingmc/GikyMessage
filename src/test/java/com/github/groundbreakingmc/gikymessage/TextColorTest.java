package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextColorTest extends TextTestBase {

    @Test
    void acceptsLiteralColorFormatsWithoutSplittingText() {
        for (final String value : new String[]{"a", "&a", "§a", "#5f5", "#55ff55", "&#55ff55", "§#5f5",
                "&x&5&5&f&f&5&5", "§x§5§5§f§f§5§5", "green", "GREEN"}) {
            final Component result = bound("[Hello](color:" + value + ")").render();
            assertEquals("Hello", ((TextComponent) result).content());
            assertEquals(NamedTextColor.GREEN, result.color());
            assertTrue(result.children().isEmpty());
        }
    }

    @Test
    void resolvesColorAgainOnEachCachedRender() {
        final Text<TestValues> text = cached("[{name}](color:{relation})");
        for (final String value : new String[]{"&a", "§c", "#123456"}) {
            final Component result = text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? Component.text("Team") : slotKey.equals("relation") ? Component.text(value) : null));
            assertEquals(StyleUtils.parseColor(value), result.color());
        }
    }

    @Test
    void recolorsChildrenAndPreservesEventsAndDecorations() {
        final Component name = Component.text("Team", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/team"))
                .append(Component.text("!", NamedTextColor.BLUE));
        final Component result = bound("[{name}](color:{relation})")
                .render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? name : slotKey.equals("relation") ? Component.text("&a") : null));
        assertEquals(name.color(NamedTextColor.GREEN)
                .children(java.util.List.of(Component.text("!", NamedTextColor.GREEN))).compact(), result.compact());
    }

    @Test
    void invalidColorKeepsExistingFormatting() {
        final Component name = Component.text("Team", NamedTextColor.RED);
        assertEquals(name.compact(), bound("[{name}](color:{relation})")
                .render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("name") ? name : slotKey.equals("relation") ? Component.text("invalid") : null)).compact());
    }

    @Test
    void rejectsPartialColorsAndFormattingSequences() {
        for (final String value : new String[]{"&c&l", "#abcxyz", "#12345", "red bold", "&x&1&2&3", ""}) {
            assertNull(StyleUtils.parseColor(value), value);
        }
    }

    @Test
    void preservesDynamicMetadataAndUsesExistingRenderCache() {
        final Text<TestValues> text = cached("[Team](color:{color},run:/{command},show:{hover})");
        final Component first = text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("color") ? Component.text("red") : slotKey.equals("command") ? Component.text("team") : slotKey.equals("hover") ? Component.text("Info") : null));
        assertEquals(NamedTextColor.RED, first.color());
        assertEquals(ClickEvent.runCommand("/team"), first.clickEvent());
        assertNotNull(first.hoverEvent());
        assertSame(first, text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("color") ? Component.text("red") : slotKey.equals("command") ? Component.text("team") : slotKey.equals("hover") ? Component.text("Info") : null)));
    }

    @Test
    void colorCanModifyGradientAndDoesNotEscapeItsBlock() {
        final Component result = bound("&b[AB](gradient:c-e,color:green)C").render();
        assertEquals(Component.empty().append(Component.text("AB", NamedTextColor.GREEN))
                .append(Component.text("C", NamedTextColor.AQUA)), result.compact());
    }

    @Test
    void unresolvedColorKeepsContent() {
        final Component result = bound("[Team](color:{missing})").render();
        assertEquals(Component.text("Team"), result.compact());
    }

    @Test
    void nestedDynamicColorIsNotFrozen() {
        final Text<TestValues> text = cached("[[Team](color:{relation})](run:/team)");
        final Component first = text.render((slotKey, slotValue) -> slotValue.component(slotKey.equals("relation") ? Component.text("&a") : null));
        final Component second = text.render((slotKey, slotValue) -> slotValue.component(slotKey.equals("relation") ? Component.text("&c") : null));
        assertNotEquals(first, second);
        assertEquals(ClickEvent.runCommand("/team"), first.clickEvent());
    }
}
