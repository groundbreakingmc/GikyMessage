package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TextTransformTest extends TextTestBase {

    private static String plain(Component component) {
        final StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent text) builder.append(text.content());
        for (final Component child : component.children()) append(builder, child);
    }

    private static String transformed(String text, String steps) {
        return plain(bound("[{v}](transform:\"" + steps + "\")").render((key, value) -> value.text(text)));
    }

    @Test
    void caseAndSmallCapitals() {
        assertEquals("PLAYER_1", transformed("Player_1", "upper"));
        assertEquals("player_1", transformed("Player_1", "lower"));
        assertEquals("ᴘʟᴀʏᴇʀ_1 xx", transformed("Player_1 xX", "smallcaps"));
        assertEquals("Привет 123", transformed("Привет 123", "smallcaps"));
    }

    @Test
    void compactShortensFreeStandingNumbers() {
        assertEquals("1.2M", transformed("1234567", "compact"));
        assertEquals("999", transformed("999", "compact"));
        assertEquals("1K", transformed("1000", "compact"));
        assertEquals("1.1K", transformed("1050", "compact"));
        assertEquals("1M", transformed("999999", "compact"));
        assertEquals("-1.5K", transformed("-1500", "compact"));
        assertEquals("1.2K", transformed("1234.56", "compact"));
        assertEquals("12345.7T", transformed("12345678901234567", "compact"));
        assertEquals("$1.2M coins, 2K gems", transformed("$1234567 coins, 2000 gems", "compact"));
        assertEquals("1 2K 30M", transformed("1 2000 30000000", "compact"));
    }

    @Test
    void compactLeavesDigitsInsideWordsAlone() {
        assertEquals("Steve1234", transformed("Steve1234", "compact"));
        assertEquals("Player_1500", transformed("Player_1500", "compact"));
        assertEquals("1500x", transformed("1500x", "compact"));
        assertEquals("a-1.5K", transformed("a-1500", "compact"));
    }

    @Test
    void maxKeepsCharactersOfTheWholeSegment() {
        final Component rendered = bound("[{a}{b}](transform:max:4)").render((key, value) -> value.text(key.equals("a") ? "Hel" : "lo!"));
        assertEquals("Hell", plain(rendered));
        assertEquals("😀😀", transformed("😀😀😀", "max:2"));
        assertEquals("HEL", plain(Text.of("[Hello](transform:\"max:3 upper\")").render()));
        assertEquals("HEL", plain(Text.of("[Hello](transform:\"upper max:3\")").render()));
        assertEquals("He", plain(Text.of("[Hello](transform:\"max:3 max:2\")").render()));
        assertEquals("Hello", plain(Text.of("[Hello](transform:\"max:0\")").render()));
        assertEquals("Hello", plain(Text.of("[Hello](transform:\"max:x\")").render()));
    }

    @Test
    void stepsRunInOrderAndUnknownStepsAreIgnored() {
        assertEquals("abc", transformed("AbC", "upper lower"));
        assertEquals("ABC", transformed("AbC", "lower upper"));
        // Small capitals have no case of their own, so a later case step leaves them.
        assertEquals("ᴀʙᴄ", transformed("ABC", "smallcaps lower"));
        assertEquals(Component.text("HELLO", NamedTextColor.RED), Text.of("[&cHello](transform:\"shout upper\")").render());
        assertEquals(Text.of("[Hello]").render(), Text.of("[Hello](transform:shout)").render());
    }

    @Test
    void componentValuesKeepTheirStructureAndStyles() {
        final Component value = Component.text("ab", NamedTextColor.RED).append(Component.text("cd", NamedTextColor.BLUE));
        final Component rendered = bound("[{v}](transform:upper)").render((key, out) -> out.component(value));
        final Component expected = bound("[{v}]").render((key, out) -> out.component(
                Component.text("AB", NamedTextColor.RED).append(Component.text("CD", NamedTextColor.BLUE))));
        assertEquals(expected, rendered);
    }

    @Test
    void transformsRunBeforeGradientsAndTranslations() {
        final Component gradient = Text.of("[ab](transform:upper, gradient:#ff0000-#0000ff)").render();
        assertEquals("AB", plain(gradient));
        assertEquals(Text.of("[AB](gradient:#ff0000-#0000ff)").render(), gradient);
        assertEquals(Component.translatable("myserver.vip", "VIP"),
                Text.of("[vip](transform:upper, lang:myserver.vip)").render());
    }

    @Test
    void staticTextIsTransformedOnceAndBindingsReuseResults() {
        final Text<Void> constant = Text.of("[Steve](transform:smallcaps)");
        assertEquals("ꜱᴛᴇᴠᴇ", plain(constant.render()));
        assertSame(constant.render(), constant.render());

        final BoundText<TestValues> binding = bound("[{name}](transform:smallcaps)").bind((key, value) -> value.text("Steve"));
        assertSame(binding.render(), binding.render());
    }
}
