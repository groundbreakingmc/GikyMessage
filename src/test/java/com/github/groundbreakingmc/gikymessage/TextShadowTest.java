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
        assertEquals(shadowed("Text", 0xFFFF0000), Text.of("$#ff0000Text").render());
        assertEquals(Component.text("Text", Style.style()
                        .color(NamedTextColor.WHITE)
                        .shadowColor(ShadowColor.shadowColor(0xFF112233))
                        .build()),
                Text.of("$#112233&fText").render());
    }

    @Test
    void alphaFollowsTheColorAfterAColon() {
        assertEquals(shadowed("Text", 0x80000000), Text.of("$#000000:80Text").render());
        assertEquals(shadowed("Text", 0x40FF8800), Text.of("$#ff8800:40Text").render());
        assertEquals(shadowed("Text", 0xFFFF8800), Text.of("$#FF8800:FFText").render());
    }

    @Test
    void zeroAlphaHidesTheShadow() {
        assertEquals(ShadowColor.none(), Text.of("$#000000:00Text").render().style().shadowColor());
    }

    @Test
    void textAfterAShadowIsNeverReadAsAlpha() {
        assertEquals(shadowed("Bad", 0xFFFF5555), Text.of("$#ff5555Bad").render());
        assertEquals(shadowed("80Text", 0xFF000000), Text.of("$#00000080Text").render());
        // A colon is alpha only with two hex digits after it.
        assertEquals(shadowed(":8 x", 0xFF000000), Text.of("$#000000:8 x").render());
        assertEquals(shadowed(": x", 0xFF000000), Text.of("$#000000: x").render());
        assertEquals(shadowed("0", 0x80000000), Text.of("$#000000:800").render());
    }

    @Test
    void otherDigitCountsAreNoShadowCode() {
        // More than six digits: a six-digit shadow followed by text.
        assertEquals(shadowed("7x", 0xFF123456), Text.of("$#1234567x").render());
        // Fewer than six digits, or none, are no shadow code at all.
        assertEquals(Component.text("$#12345 x"), Text.of("$#12345 x").render());
        assertEquals(Component.text("$#f008 x"), Text.of("$#f008 x").render());
        assertEquals(Component.text("$#f00 x"), Text.of("$#f00 x").render());
        assertEquals(Component.text("$# x"), Text.of("$# x").render());
        assertEquals(Component.text("$5"), Text.of("$5").render());
    }

    @Test
    void theHashIsOptional() {
        assertEquals(shadowed("Text", 0xFFFF5555), Text.of("$ff5555Text").render());
        assertEquals(shadowed("Text", 0x80000000), Text.of("$000000:80Text").render());
        assertEquals(shadowed("0 coins", 0xFF100000), Text.of("$1000000 coins").render());
        // Fewer than six digits are no code, and a backslash keeps six of them as text.
        assertEquals(Component.text("$99999 and $5"), Text.of("$99999 and $5").render());
        assertEquals(Component.text("$100000"), Text.of("\\$100000").render());
    }

    @Test
    void textColorsTakeNoAlpha() {
        assertEquals(Component.text("80X", TextColor.color(0xFF0000)), Text.of("&#ff000080X").render());
        assertEquals(Component.text(":80X", TextColor.color(0xFF0000)), Text.of("&#ff0000:80X").render());
    }

    @Test
    void alphaShadowCoversPlaceholdersAndLaterColors() {
        final Component rendered = bound("$#000000:80{name} &cand more").render((key, value) -> value.text("Steve"));
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
