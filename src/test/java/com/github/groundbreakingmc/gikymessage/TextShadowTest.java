package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextShadowTest extends TextTestBase {

    private static Component shadowed(String text, int argb) {
        return Component.text(text, Style.style().shadowColor(ShadowColor.shadowColor(argb)).build());
    }

    @Test
    void colorsWithoutAlphaAreOpaque() {
        assertEquals(shadowed("Text", 0xFF000000), Text.of("$#000000Text").render());
        assertEquals(shadowed("Text", 0xFFFF0000), Text.of("$#f00Text").render());
        assertEquals(Component.text("Text", Style.style()
                        .color(NamedTextColor.WHITE)
                        .shadowColor(ShadowColor.shadowColor(0xFF112233))
                        .build()),
                Text.of("$#112233&fText").render());
    }

    @Test
    void alphaComesLastInLongAndShortForms() {
        assertEquals(shadowed("Text", 0x80000000), Text.of("$#00000080Text").render());
        assertEquals(shadowed("Text", 0x40FF8800), Text.of("$#ff880040Text").render());
        assertEquals(shadowed("Text", 0x88FF0000), Text.of("$#f008Text").render());
        assertEquals(shadowed("Text", 0xFF00FF00), Text.of("$#0f0fText").render());
    }

    @Test
    void zeroAlphaHidesTheShadow() {
        assertEquals(ShadowColor.none(), Text.of("$#00000000Text").render().style().shadowColor());
        assertEquals(ShadowColor.none(), Text.of("$#0000Text").render().style().shadowColor());
    }

    @Test
    void otherDigitCountsKeepTheirEarlierMeaning() {
        // Seven digits: a six-digit shadow followed by one character of text.
        assertEquals(shadowed("7x", 0xFF123456), Text.of("$#1234567x").render());
        // Nine digits: an eight-digit shadow followed by the ninth.
        assertEquals(shadowed("9", 0x78123456), Text.of("$#123456789").render());
        // Five digits, or none, are no shadow code at all.
        assertEquals(Component.text("$#12345 x"), Text.of("$#12345 x").render());
        assertEquals(Component.text("$# x"), Text.of("$# x").render());
        assertEquals(Component.text("$5"), Text.of("$5").render());
    }

    @Test
    void textColorsTakeNoAlpha() {
        assertEquals(Component.text("80X", TextColor.color(0xFF0000)), Text.of("&#ff000080X").render());
    }

    @Test
    void alphaShadowCoversPlaceholdersAndLaterColors() {
        final Component rendered = bound("$#00000080{name} &cand more").render((key, value) -> value.text("Steve"));
        assertEquals("Steve and more", MM.stripTags(MM.serialize(rendered)));
        assertEveryTextShadowed(rendered, null, ShadowColor.shadowColor(0x80000000));
    }

    private static void assertEveryTextShadowed(Component component, ShadowColor inherited, ShadowColor expected) {
        final ShadowColor own = component.style().shadowColor();
        final ShadowColor effective = own != null ? own : inherited;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            assertEquals(expected, effective, text.content());
        }
        for (final Component child : component.children()) assertEveryTextShadowed(child, effective, expected);
    }
}
