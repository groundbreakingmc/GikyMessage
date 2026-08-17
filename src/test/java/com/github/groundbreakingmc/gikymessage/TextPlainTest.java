package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for plain text rendering: no-style text, legacy color codes (all 16),
 * hex colors, and decoration-only formatting.
 */
@DisplayName("Plain text & colors")
class TextPlainTest extends TextTestBase {

    // ════════════════════════════════════════════════════════════════════════
    //  Plain text
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plain text")
    class PlainText {

        @Test
        @DisplayName("No style")
        void noStyle() {
            assertRenders(Component.text("Hello world"), "Hello world");
        }

        @Test
        @DisplayName("Empty string")
        void emptyString() {
            assertRenders(Component.empty(), "");
        }

        @Test
        @DisplayName("Whitespace only")
        void whitespaceOnly() {
            assertRenders(Component.text("   "), "   ");
        }

        @Test
        @DisplayName("Green color")
        void greenColor() {
            assertRenders(Component.text("Hello world", NamedTextColor.GREEN), "&aHello world");
        }

        @Test
        @DisplayName("Bold red")
        void boldRed() {
            assertRenders(
                    Component.text("Bold red", NamedTextColor.RED, TextDecoration.BOLD),
                    "&c&lBold red"
            );
        }

        @Test
        @DisplayName("Bold then color (reversed code order)")
        void boldThenColor() {
            // &l before &c — order of codes must not matter for the final style
            assertRenders(
                    Component.text("Bold red", NamedTextColor.RED, TextDecoration.BOLD),
                    "&l&cBold red"
            );
        }

        @Test
        @DisplayName("Italic gold")
        void italicGold() {
            assertRenders(
                    Component.text("Italic", NamedTextColor.GOLD).decorate(TextDecoration.ITALIC),
                    "&6&oItalic"
            );
        }

        @Test
        @DisplayName("Underlined")
        void underlined() {
            assertRenders(Component.text("Underlined").decorate(TextDecoration.UNDERLINED), "&nUnderlined");
        }

        @Test
        @DisplayName("Strikethrough")
        void strikethrough() {
            assertRenders(Component.text("Strike").decorate(TextDecoration.STRIKETHROUGH), "&mStrike");
        }

        @Test
        @DisplayName("Obfuscated")
        void obfuscated() {
            assertRenders(Component.text("Obfuscated").decorate(TextDecoration.OBFUSCATED), "&kObfuscated");
        }

        @Test
        @DisplayName("All decorations stacked on one segment")
        void allDecorations() {
            // bold + italic + underline + strikethrough
            assertRenders(
                    Component.text("All")
                            .decorate(TextDecoration.BOLD)
                            .decorate(TextDecoration.ITALIC)
                            .decorate(TextDecoration.UNDERLINED)
                            .decorate(TextDecoration.STRIKETHROUGH),
                    "&l&o&n&mAll"
            );
        }

        @Test
        @DisplayName("Color reset mid-string (&r)")
        void colorResetMidString() {
            // &r resets all formatting; text after reset should be unstyled
            assertRenders(
                    Component.text("Red")
                            .color(NamedTextColor.RED)
                            .append(Component.text(" Normal")),
                    "&cRed&r Normal"
            );
        }

        @Test
        @DisplayName("Multiple color changes in sequence")
        void multipleColorChanges() {
            assertRenders(
                    Component.text("Red")
                            .color(NamedTextColor.RED)
                            .append(Component.text("Green", NamedTextColor.GREEN))
                            .append(Component.text("Blue", NamedTextColor.BLUE)),
                    "&cRed&aGreen&9Blue"
            );
        }

        @Test
        @DisplayName("Decoration then reset then decoration")
        void decorationResetDecoration() {
            assertRenders(
                    Component.text("Bold")
                            .decorate(TextDecoration.BOLD)
                            .append(Component.text(" Normal"))
                            .append(Component.text(" Italic").decorate(TextDecoration.ITALIC)),
                    "&lBold&r Normal&o Italic"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Legacy color codes (all 16)
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legacy color codes")
    class LegacyColors {

        @Test
        void black() {
            assertRenders(Component.text("x", NamedTextColor.BLACK), "&0x");
        }

        @Test
        void darkBlue() {
            assertRenders(Component.text("x", NamedTextColor.DARK_BLUE), "&1x");
        }

        @Test
        void darkGreen() {
            assertRenders(Component.text("x", NamedTextColor.DARK_GREEN), "&2x");
        }

        @Test
        void darkAqua() {
            assertRenders(Component.text("x", NamedTextColor.DARK_AQUA), "&3x");
        }

        @Test
        void darkRed() {
            assertRenders(Component.text("x", NamedTextColor.DARK_RED), "&4x");
        }

        @Test
        void darkPurple() {
            assertRenders(Component.text("x", NamedTextColor.DARK_PURPLE), "&5x");
        }

        @Test
        void gold() {
            assertRenders(Component.text("x", NamedTextColor.GOLD), "&6x");
        }

        @Test
        void gray() {
            assertRenders(Component.text("x", NamedTextColor.GRAY), "&7x");
        }

        @Test
        void darkGray() {
            assertRenders(Component.text("x", NamedTextColor.DARK_GRAY), "&8x");
        }

        @Test
        void blue() {
            assertRenders(Component.text("x", NamedTextColor.BLUE), "&9x");
        }

        @Test
        void green() {
            assertRenders(Component.text("x", NamedTextColor.GREEN), "&ax");
        }

        @Test
        void aqua() {
            assertRenders(Component.text("x", NamedTextColor.AQUA), "&bx");
        }

        @Test
        void red() {
            assertRenders(Component.text("x", NamedTextColor.RED), "&cx");
        }

        @Test
        void lightPurple() {
            assertRenders(Component.text("x", NamedTextColor.LIGHT_PURPLE), "&dx");
        }

        @Test
        void yellow() {
            assertRenders(Component.text("x", NamedTextColor.YELLOW), "&ex");
        }

        @Test
        void white() {
            assertRenders(Component.text("x", NamedTextColor.WHITE), "&fx");
        }

        @Test
        @DisplayName("Color overrides previous color")
        void colorOverridesPrevious() {
            // Second color replaces first for the same text run
            assertRenders(
                    Component.text("x", NamedTextColor.RED),
                    "&a&cx"
            );
        }

        @Test
        @DisplayName("All 16 colors rendered in a single format string")
        void allColorsInOneString() {
            // Builds the format string programmatically to keep the test readable;
            // the expected component is assembled the same way.
            final String[] codes = {"&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
                    "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f"};
            final NamedTextColor[] colors = {
                    NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN,
                    NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
                    NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
                    NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
                    NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW,
                    NamedTextColor.WHITE
            };

            final StringBuilder format = new StringBuilder();
            Component root = Component.text("0", colors[0]);
            for (int i = 0; i < codes.length; i++) {
                format.append(codes[i]).append(i);
                if (i > 0) root = root.append(Component.text(String.valueOf(i), colors[i]));
            }
            assertRenders(root, format.toString());
        }

        @Test
        @DisplayName("Bold before color (reversed order)")
        void boldBeforeColor() {
            assertRenders(
                    Component.text("x", NamedTextColor.RED, TextDecoration.BOLD),
                    "&l&cx"
            );
        }

        @Test
        @DisplayName("Italic before hex color (reversed order)")
        void italicBeforeColor() {
            assertRenders(
                    Component.text("x", NamedTextColor.RED, TextDecoration.ITALIC),
                    "&o&cx"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Hex colors
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Hex colors")
    class HexColors {

        @Test
        @DisplayName("6-digit hex")
        void sixDigit() {
            assertRenders(Component.text("Hex", TextColor.color(0xff5555)), "&#ff5555Hex");
        }

        @Test
        @DisplayName("3-digit shorthand (#f55 == #ff5555)")
        void threeDigitShorthand() {
            assertRenders(Component.text("Short", TextColor.color(0xff5555)), "&#f55Short");
        }

        @Test
        @DisplayName("Hex color with bold decoration")
        void hexWithBold() {
            assertRenders(
                    Component.text("Bold hex", TextColor.color(0x55ff55), TextDecoration.BOLD),
                    "&#55ff55&lBold hex"
            );
        }

        @Test
        @DisplayName("Bold before hex color (reversed order)")
        void boldBeforeHex() {
            assertRenders(
                    Component.text("Bold hex", TextColor.color(0x55ff55), TextDecoration.BOLD),
                    "&l&#55ff55Bold hex"
            );
        }

        @Test
        @DisplayName("Hex color with italic decoration")
        void hexWithItalic() {
            assertRenders(
                    Component.text("Italic hex", TextColor.color(0xaa00ff), TextDecoration.ITALIC),
                    "&#aa00ff&oItalic hex"
            );
        }

        @Test
        @DisplayName("Hex color with italic decoration")
        void italicBeforeHex() {
            assertRenders(
                    Component.text("Italic hex", TextColor.color(0xaa00ff), TextDecoration.ITALIC),
                    "&o&#aa00ffItalic hex"
            );
        }

        @Test
        @DisplayName("Hex color with all decorations")
        void hexWithAllDecorations() {
            assertRenders(
                    Component.text("Full", TextColor.color(0x123456))
                            .decorate(TextDecoration.BOLD)
                            .decorate(TextDecoration.ITALIC)
                            .decorate(TextDecoration.UNDERLINED),
                    "&#123456&l&o&nFull"
            );
        }

        @Test
        @DisplayName("Two different hex colors in sequence")
        void twoHexColors() {
            assertRenders(
                    Component.text("First", TextColor.color(0xff0000))
                            .append(Component.text("Second", TextColor.color(0x0000ff))),
                    "&#ff0000First&#0000ffSecond"
            );
        }

        @Test
        @DisplayName("Hex color followed by legacy color")
        void hexThenLegacy() {
            assertRenders(
                    Component.text("Hex", TextColor.color(0xff5500))
                            .append(Component.text("Legacy", NamedTextColor.GREEN)),
                    "&#ff5500Hex&aLegacy"
            );
        }

        @Test
        @DisplayName("Legacy color followed by hex color")
        void legacyThenHex() {
            assertRenders(
                    Component.text("Legacy", NamedTextColor.GREEN)
                            .append(Component.text("Hex", TextColor.color(0xff5500))),
                    "&aLegacy&#ff5500Hex"
            );
        }
    }
}
