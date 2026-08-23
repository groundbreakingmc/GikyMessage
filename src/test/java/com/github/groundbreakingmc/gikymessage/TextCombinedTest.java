package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration / combined tests: nested blocks, escape sequences, and
 * complex format strings mixing colors, decorations, placeholders,
 * click, hover, and nesting in various orders.
 */
@DisplayName("Combined & integration")
class TextCombinedTest extends TextTestBase {

    // ════════════════════════════════════════════════════════════════════════
    //  Nesting
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Nested blocks")
    class Nesting {

        @Test
        @DisplayName("Outer click, inner hover")
        void outerClickInnerHover() {
            assertRenders(
                    Component.text("outer ").clickEvent(ClickEvent.runCommand("/outer"))
                            .children(List.of(
                                    Component.text("inner").hoverEvent(HoverEvent.showText(Component.text("tip")))
                            )),
                    "[outer [inner](show:tip)](run:/outer)"
            );
        }

        @Test
        @DisplayName("Outer click, inner click (override)")
        void outerClickInnerClick() {
            assertRenders(
                    Component.text("parent ").clickEvent(ClickEvent.runCommand("/p"))
                            .children(List.of(
                                    Component.text("child").clickEvent(ClickEvent.runCommand("/c"))
                            )),
                    "[parent [child](run:/c)](run:/p)"
            );
        }

        @Test
        @DisplayName("Outer hover, inner click")
        void outerHoverInnerClick() {
            assertRenders(
                    Component.text("outer ").hoverEvent(HoverEvent.showText(Component.text("outer tip")))
                            .children(List.of(
                                    Component.text("inner").clickEvent(ClickEvent.runCommand("/inner"))
                            )),
                    "[outer [inner](run:/inner)](show:outer tip)"
            );
        }

        @Test
        @DisplayName("Outer hover, inner hover (override)")
        void outerHoverInnerHover() {
            assertRenders(
                    Component.text("outer ").hoverEvent(HoverEvent.showText(Component.text("outer tip")))
                            .children(List.of(
                                    Component.text("inner").hoverEvent(HoverEvent.showText(Component.text("inner tip")))
                            )),
                    "[outer [inner](show:inner tip)](show:outer tip)"
            );
        }

        @Test
        @DisplayName("Outer click+hover, inner hover")
        void outerClickHoverInnerHover() {
            assertRenders(
                    Component.text("outer ")
                            .clickEvent(ClickEvent.runCommand("/outer"))
                            .hoverEvent(HoverEvent.showText(Component.text("outer tip")))
                            .children(List.of(
                                    Component.text("inner").hoverEvent(HoverEvent.showText(Component.text("inner tip")))
                            )),
                    "[outer [inner](show:inner tip)](run:/outer, show:outer tip)"
            );
        }

        @Test
        @DisplayName("Three nesting levels")
        void threeLevels() {
            assertRenders(
                    Component.text("L1 ").clickEvent(ClickEvent.runCommand("/l1"))
                            .children(List.of(
                                    Component.text("L2 ").hoverEvent(HoverEvent.showText(Component.text("tip2")))
                                            .children(List.of(
                                                    Component.text("L3").clickEvent(ClickEvent.runCommand("/l3"))
                                            ))
                            )),
                    "[L1 [L2 [L3](run:/l3)](show:tip2)](run:/l1)"
            );
        }

        @Test
        @DisplayName("Nested with dynamic placeholder")
        void nestedWithPlaceholder() {
            assertRenders(
                    Component.text("outer ").clickEvent(ClickEvent.runCommand("/outer"))
                            .children(List.of(
                                    Component.text("Steve").hoverEvent(HoverEvent.showText(Component.text("player tip")))
                            )),
                    "[outer [{player}](show:player tip)](run:/outer)",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Nested with colored outer text")
        void nestedColoredOuter() {
            assertRenders(
                    Component.text("outer ", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/cmd"))
                            .children(List.of(
                                    Component.text("inner").hoverEvent(HoverEvent.showText(Component.text("tip")))
                            )),
                    "&c[outer [inner](show:tip)](run:/cmd)"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Escape sequences
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Escape sequences")
    class Escapes {

        @Test
        @DisplayName("Escaped brackets")
        void escapedBrackets() {
            assertRenders(
                    Component.text("Use [ and ] as literal"),
                    "Use \\[ and \\] as literal"
            );
        }

        @Test
        @DisplayName("Escaped newline (\\n)")
        void escapedNewline() {
            assertRenders(
                    Component.text("Line one\nLine two"),
                    "Line one\\nLine two"
            );
        }

        @Test
        @DisplayName("Escaped curly braces")
        void escapedCurlyBraces() {
            assertRenders(
                    Component.text("Has { brace }"),
                    "Has \\{ brace \\}"
            );
        }

        @Test
        @DisplayName("Escaped opening brace only")
        void escapedOpenBraceOnly() {
            assertRenders(
                    Component.text("At { start"),
                    "At \\{ start"
            );
        }

        @Test
        @DisplayName("Escaped brackets with color")
        void escapedBracketsWithColor() {
            assertRenders(
                    Component.text("See [item]", NamedTextColor.GREEN),
                    "&aSee \\[item\\]"
            );
        }

        @Test
        @DisplayName("Escaped newline in hover text")
        void escapedNewlineInHover() {
            assertRenders(
                    Component.text("X").hoverEvent(HoverEvent.showText(Component.text("Line1\nLine2"))),
                    "[X](show:Line1\\nLine2)"
            );
        }

        @Test
        @DisplayName("Multiple escaped newlines")
        void multipleEscapedNewlines() {
            assertRenders(
                    Component.text("A\nB\nC"),
                    "A\\nB\\nC"
            );
        }

        @Test
        @DisplayName("Escaped bracket adjacent to real block")
        void escapedBracketAdjacentToRealBlock() {
            assertRenders(
                    Component.text("[")
                            .append(Component.text("Run").clickEvent(ClickEvent.runCommand("/spawn")))
                            .append(Component.text("]")),
                    "\\[[Run](run:/spawn)\\]"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Combined / full integration
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Combined cases")
    class Combined {

        @Test
        @DisplayName("Invalid sprite key")
        void invalidSpriteKey() {
            assertThrows(
                    TextFormatException.class,
                    () -> Text.of("[X](sprite:INVALID KEY)")
            );
        }

        @Test
        @DisplayName("Styled text with dynamic click command")
        void styledTextDynamicClick() {
            assertRenders(
                    Component.text("Text ", NamedTextColor.RED, TextDecoration.BOLD)
                            .children(List.of(
                                    Component.text("simple text").clickEvent(ClickEvent.runCommand("/cmd"))
                            )),
                    "&c&lText [{text}](run:/{command})",
                    "text", "simple text", "command", "cmd"
            );
        }

        @Test
        @DisplayName("Styled text with nested hover inside dynamic click block")
        void styledTextNestedHoverInsideDynamicClick() {
            assertRenders(
                    Component.text("Text ", NamedTextColor.RED, TextDecoration.BOLD)
                            .children(List.of(
                                    Component.text("simple text ").clickEvent(ClickEvent.runCommand("/cmd"))
                                            .children(List.of(
                                                    Component.text("@").hoverEvent(HoverEvent.showText(Component.text("Hover text")))
                                            ))
                            )),
                    "&c&lText [{text} [@](show:Hover text)](run:/{command})",
                    "text", "simple text", "command", "cmd"
            );
        }

        @Test
        @DisplayName("Static hover with inline color code")
        void staticHoverWithColor() {
            assertRenders(
                    Component.text("@").hoverEvent(Component.text("Red hover", NamedTextColor.RED)),
                    "[@](show:&cRed hover)"
            );
        }

        @Test
        @DisplayName("Dynamic hover — color in template, text from placeholder")
        void dynamicHoverColorInTemplate() {
            assertRenders(
                    Component.text("@").hoverEvent(Component.text("Red hover", NamedTextColor.RED)),
                    "[@](show:&c{text})",
                    "text", "Red hover"
            );
        }

        @Test
        @DisplayName("Dynamic hover keeps placeholder component text unchanged")
        void dynamicHoverColorInPlaceholder() {
            assertRenders(
                    Component.text("@").hoverEvent(Component.text("&cRed hover")),
                    "[@](show:{text})",
                    "text", "&cRed hover"
            );
        }

        @Test
        @DisplayName("Color + bold + placeholder + click + hover all together")
        void everythingTogether() {
            assertRenders(
                    Component.text("Steve", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/tp Steve"))
                            .hoverEvent(HoverEvent.showText(Component.text("Teleport to Steve"))),
                    "&a&l[{player}](run:'/tp {player}', show:'Teleport to {player}')",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Color + bold + two placeholders + click + hover")
        void everythingTogetherTwoPlaceholders() {
            assertRenders(
                    Component.text("Steve paid 100", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/pay Steve 100"))
                            .hoverEvent(HoverEvent.showText(Component.text("Pay 100 to Steve"))),
                    "&6&l[{player} paid {coins}](run:'/pay {player} {coins}', show:'Pay {coins} to {player}')",
                    "player", "Steve", "coins", "100"
            );
        }

        @Test
        @DisplayName("Hex color + decoration + dynamic command")
        void hexColorDecorationDynamicCommand() {
            assertRenders(
                    Component.text("Teleport", TextColor.color(0x00aaff), TextDecoration.ITALIC)
                            .clickEvent(ClickEvent.runCommand("/tp Steve")),
                    "&#00aaff&o[Teleport](run:/tp {player})",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Plain text before and after a dynamic block")
        void plainTextAroundBlock() {
            assertRenders(
                    Component.text("Click ")
                            .append(Component.text("here").clickEvent(ClickEvent.runCommand("/spawn")))
                            .append(Component.text(" to teleport")),
                    "Click [here](run:/spawn) to teleport"
            );
        }

        @Test
        @DisplayName("Two consecutive dynamic blocks")
        void twoConsecutiveBlocks() {
            assertRenders(
                    Component.empty()
                            .append(Component.text("Steve").clickEvent(ClickEvent.runCommand("/tp Steve")))
                            .append(Component.text(" "))
                            .append(Component.text("Alex").clickEvent(ClickEvent.runCommand("/tp Alex"))),
                    "[{p1}](run:/tp {p1}) [{p2}](run:/tp {p2})",
                    "p1", "Steve", "p2", "Alex"
            );
        }

        @Test
        @DisplayName("Colored prefix, dynamic block, colored suffix")
        void coloredPrefixBlockColoredSuffix() {
            assertRenders(
                    Component.text("[", NamedTextColor.GRAY)
                            .append(Component.text("Steve", NamedTextColor.GOLD)
                                    .clickEvent(ClickEvent.runCommand("/whois Steve")))
                            .append(Component.text("]")),
                    "&7\\[&6[{player}](run:/whois {player})&7]",
                    "player", "Steve"
            );
        }

        @Test
        @DisplayName("Nested block with two placeholders in inner actions")
        void nestedBlockTwoPlaceholders() {
            assertRenders(
                    Component.text("Stats ").clickEvent(ClickEvent.runCommand("/stats Steve"))
                            .children(List.of(
                                    Component.text("100 coins")
                                            .hoverEvent(HoverEvent.showText(Component.text("Steve has 100 coins")))
                            )),
                    "[Stats [{coins} coins](show:'{player} has {coins} coins')](run:/stats {player})",
                    "player", "Steve", "coins", "100"
            );
        }
    }
}
