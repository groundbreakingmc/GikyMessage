package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for dynamic (placeholder-driven) content: text substitution, click/hover
 * actions with placeholders, and multiple-placeholder combinations.
 */
@DisplayName("Dynamic / placeholder rendering")
class TextDynamicTest extends TextTestBase {

    // ════════════════════════════════════════════════════════════════════════
    //  Dynamic text placeholders
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dynamic text placeholders")
    class DynamicText {

        @Test
        @DisplayName("Placeholder only")
        void plainPlaceholder() {
            assertRenders(Component.text("Hello, Steve!"), "Hello, {player}!", "player", "Steve");
        }

        @Test
        @DisplayName("Unresolved placeholder")
        void unresolvedPlaceholder() {
            assertRenders(Component.text("Hello, {player}!"), "Hello, {player}!");
        }

        @Test
        @DisplayName("Placeholder with color")
        void coloredPlaceholder() {
            assertRenders(
                    Component.text("Hello, Steve!", NamedTextColor.YELLOW),
                    "&eHello, {player}!",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder as entire bracketed content with click")
        void placeholderAsBlockWithClick() {
            assertRenders(
                    Component.text("Steve").clickEvent(ClickEvent.runCommand("/spawn")),
                    "[{player}](run:/spawn)",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder inside bracketed content with static click")
        void placeholderInsideBlockWithStaticClick() {
            assertRenders(
                    Component.text("Hello, Steve!").clickEvent(ClickEvent.runCommand("/spawn")),
                    "[Hello, {player}!](run:/spawn)",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder at start of text")
        void placeholderAtStart() {
            assertRenders(Component.text("Steve joined the game"), "{player} joined the game", "player", "Steve");
        }

        @Test
        @DisplayName("Placeholder at end of text")
        void placeholderAtEnd() {
            assertRenders(Component.text("Welcome, Steve"), "Welcome, {player}", "player", "Steve");
        }

        @Test
        @DisplayName("Placeholder with bold surrounding text")
        void placeholderWithBoldContext() {
            assertRenders(
                    Component.text("Player: Steve", NamedTextColor.GREEN, TextDecoration.BOLD),
                    "&a&lPlayer: {player}",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder value contains spaces")
        void placeholderValueWithSpaces() {
            assertRenders(Component.text("Hello, Steve Jobs!"), "Hello, {player}!", "player", "Steve Jobs");
        }

        @Test
        @DisplayName("Placeholder value contains numbers")
        void placeholderValueIsNumber() {
            assertRenders(Component.text("Kills: 42"), "Kills: {count}", "count", "42");
        }

        @Test
        @DisplayName("Same placeholder used twice in text")
        void samePlaceholderTwice() {
            assertRenders(
                    Component.text("Steve vs Steve"),
                    "{player} vs {player}",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Styled placeholder keeps its component structure")
        void styledPlaceholderKeepsStructure() {
            final Component player = Component.text("Steve", NamedTextColor.RED)
                    .append(Component.text("!", NamedTextColor.YELLOW));

            org.junit.jupiter.api.Assertions.assertEquals(
                    MM.serialize(Component.text("Hello, ").append(player)),
                    MM.serialize(Text.of("Hello, {player}").render("player", player))
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Dynamic click — placeholder in command / URL
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dynamic click actions")
    class DynamicClick {

        @Test
        @DisplayName("Placeholder in run command")
        void placeholderInRunCommand() {
            assertRenders(
                    Component.text("TP").clickEvent(ClickEvent.runCommand("/tp Steve")),
                    "[TP](run:/tp {player})",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder in suggest command")
        void placeholderInSuggestCommand() {
            assertRenders(
                    Component.text("Msg").clickEvent(ClickEvent.suggestCommand("/msg Alex ")),
                    "[Msg](suggest:/msg {player} )",
                    "player", "Alex"
            );
        }

        @Test
        @DisplayName("Placeholder in URL")
        void placeholderInUrl() {
            assertRenders(
                    Component.text("Link").clickEvent(ClickEvent.openUrl("https://example.com/Steve")),
                    "[Link](url:https://example.com/{player})",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder in text AND in run command")
        void placeholderInTextAndCommand() {
            assertRenders(
                    Component.text("TP to Steve").clickEvent(ClickEvent.runCommand("/tp Steve")),
                    "[TP to {player}](run:/tp {player})",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Two placeholders in run command")
        void twoPlaceholdersInCommand() {
            assertRenders(
                    Component.text("Pay").clickEvent(ClickEvent.runCommand("/pay Steve 100")),
                    "[Pay](run:/pay {player} {coins})",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Placeholder in suggest command — value with spaces")
        void placeholderWithSpacesInSuggest() {
            assertRenders(
                    Component.text("Msg").clickEvent(ClickEvent.suggestCommand("/msg Steve Jobs ")),
                    "[Msg](suggest:/msg {player} )",
                    "player", "Steve Jobs"
            );
        }

        @Test
        @DisplayName("Color on text, placeholder in command")
        void coloredTextPlaceholderInCommand() {
            assertRenders(
                    Component.text("Kick", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/kick Steve")),
                    "&c[Kick](run:/kick {player})",
                    "player", "Steve"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Dynamic hover — placeholder in show value
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dynamic hover actions")
    class DynamicHover {

        @Test
        @DisplayName("Placeholder in quoted show value")
        void placeholderInQuotedShow() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(Component.text("Hello, Steve!"))),
                    "[Hover](show:'Hello, {player}!')",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Color code + placeholder in show value")
        void colorCodeAndPlaceholderInShow() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(
                            Component.text("Steve", NamedTextColor.GOLD))),
                    "[Hover](show:&6{player})",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Placeholder in show keeps component text unchanged")
        void placeholderValueContainsColorCode() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(
                            Component.text("&cSteve"))),
                    "[Hover](show:{player})",
                    "player", "&cSteve"
            );
        }

        @Test
        @DisplayName("Placeholder in both text and show value")
        void placeholderInTextAndShow() {
            assertRenders(
                    Component.text("Steve").hoverEvent(HoverEvent.showText(Component.text("Hello, Steve!"))),
                    "[{player}](show:'Hello, {player}!')",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Two placeholders in show value")
        void twoPlaceholdersInShow() {
            assertRenders(
                    Component.text("Info").hoverEvent(HoverEvent.showText(
                            Component.text("Steve: 100"))),
                    "[Info](show:'{player}: {coins}')",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Colored trigger with placeholder in show")
        void coloredTriggerPlaceholderShow() {
            assertRenders(
                    Component.text("Steve", NamedTextColor.GOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Click to inspect Steve"))),
                    "&6[{player}](show:'Click to inspect {player}')",
                    "player", "Steve"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Dynamic — placeholder in both click and hover
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dynamic click + hover combined")
    class DynamicClickAndHover {

        @Test
        @DisplayName("Placeholder in text, command, and show")
        void placeholderInTextCommandShow() {
            assertRenders(
                    Component.text("Steve")
                            .clickEvent(ClickEvent.runCommand("/inspect Steve"))
                            .hoverEvent(HoverEvent.showText(Component.text("Inspect Steve"))),
                    "[{player}](run:/inspect {player}, show:'Inspect {player}')",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Two placeholders — text, command, and show")
        void twoPlaceholdersTextCommandShow() {
            assertRenders(
                    Component.text("Steve paid 100")
                            .clickEvent(ClickEvent.runCommand("/pay Steve 100"))
                            .hoverEvent(HoverEvent.showText(Component.text("Pay 100 to Steve"))),
                    "[{player} paid {coins}](run:/pay {player} {coins}, show:'Pay {coins} to {player}')",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Colored text, placeholder in command and show (reversed action order)")
        void coloredTextReversedActions() {
            assertRenders(
                    Component.text("Steve", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/tp Steve"))
                            .hoverEvent(HoverEvent.showText(Component.text("Teleport to Steve"))),
                    "&a[{player}](show:'Teleport to {player}', run:/tp {player})",
                    "player", "Steve"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Multiple placeholders
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multiple placeholders")
    class MultiplePlaceholders {

        @Test
        @DisplayName("Two placeholders in plain text")
        void twoPlaceholdersPlain() {
            assertRenders(
                    Component.text("Steve has 100 coins"),
                    "{player} has {coins} coins",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Two placeholders — reversed order in format string")
        void twoPlaceholdersReversedOrder() {
            assertRenders(
                    Component.text("100 coins for Steve"),
                    "{coins} coins for {player}",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Two placeholders in text and command")
        void twoPlaceholdersTextAndCommand() {
            assertRenders(
                    Component.text("Steve has 100 coins").clickEvent(ClickEvent.runCommand("/pay Steve 100")),
                    "[{player} has {coins} coins](run:/pay {player} {coins})",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Two placeholders in text and hover")
        void twoPlaceholdersTextAndHover() {
            assertRenders(
                    Component.text("Steve: 100").hoverEvent(HoverEvent.showText(Component.text("Player: Steve, Coins: 100"))),
                    "[{player}: {coins}](show:'Player: {player}, Coins: {coins}')",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Three placeholders in plain text")
        void threePlaceholdersPlain() {
            assertRenders(
                    Component.text("Steve killed Alex with diamond_sword"),
                    "{killer} killed {victim} with {weapon}",
                    "killer", "Steve", "victim", "Alex", "weapon", "diamond_sword"
            );
        }

        @Test
        @DisplayName("Three placeholders — different orders in text vs command")
        void threePlaceholdersDifferentOrders() {
            assertRenders(
                    Component.text("Steve killed Alex")
                            .clickEvent(ClickEvent.runCommand("/spectate Alex Steve diamond_sword")),
                    "[{killer} killed {victim}](run:/spectate {victim} {killer} {weapon})",
                    "killer", "Steve", "victim", "Alex", "weapon", "diamond_sword"
            );
        }

        @Test
        @DisplayName("Four placeholders in text and command")
        void fourPlaceholders() {
            assertRenders(
                    Component.text("Steve:SkyBlock > Alex:Survival")
                            .clickEvent(ClickEvent.runCommand("/challenge Steve SkyBlock Alex Survival")),
                    "[{p1}:{mode1} > {p2}:{mode2}](run:/challenge {p1} {mode1} {p2} {mode2})",
                    "p1", "Steve", "mode1", "SkyBlock", "p2", "Alex", "mode2", "Survival"
            );
        }

        @Test
        @DisplayName("Two placeholders — same value for both keys")
        void twoPlaceholdersSameValue() {
            assertRenders(
                    Component.text("Steve teleported to Steve"),
                    "{source} teleported to {target}",
                    "source", "Steve", "target", "Steve"
            );
        }
    }
}
