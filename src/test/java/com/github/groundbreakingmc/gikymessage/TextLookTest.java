package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code color}, {@code style} and {@code shadow} set what the segment's text leaves unset;
 * {@code force-color}, {@code force-style} and {@code force-shadow} set it throughout.
 */
class TextLookTest extends TextTestBase {

    private static final Component MENTION = Component.text("hi ")
            .append(Component.text("@Steve", NamedTextColor.YELLOW));

    @Test
    void replacesTheLookOfTheTextBefore() {
        assertEquals(Component.text("Text", NamedTextColor.RED), Text.of("&a[Text](color:red)").render().compact());
        assertEquals(Component.text("Text", NamedTextColor.GOLD, TextDecoration.BOLD),
                Text.of("&7&o[Text](style:\"gold bold\")").render().compact()
                        .decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET));
    }

    @Test
    void keepsTheCodesInsideTheSegment() {
        assertEquals(Component.text("Text", NamedTextColor.AQUA), Text.of("&a[&bText](color:red)").render().compact());
    }

    @Test
    void keepsTheColorsOfAPlaceholderComponent() {
        final Component rendered = bound("&7[{name}](style:{format})").render((key, value) -> {
            if (key.equals("name")) value.component(MENTION);
            else value.text("gold");
        });
        assertEquals(Component.text("hi ", NamedTextColor.GOLD)
                .append(Component.text("@Steve", NamedTextColor.YELLOW)), rendered.compact());
    }

    @Test
    void anEmptyValueKeepsTheLookOfTheTextBefore() {
        assertEquals(Component.text("Text", NamedTextColor.GREEN),
                bound("&a[Text](color:{format})").render((key, value) -> value.text("")).compact());
    }

    @Test
    void colorWinsOverTheColorOfStyle() {
        assertEquals(Component.text("Text", NamedTextColor.RED, TextDecoration.BOLD),
                Text.of("[Text](style:\"gold bold\",color:red)").render().compact());
    }

    @Test
    void forcedLooksOverrideEverything() {
        assertEquals(Component.text("Text", NamedTextColor.RED), Text.of("&a[&bText](force-color:red)").render().compact());
        assertEquals(Component.text("hi @Steve", NamedTextColor.GOLD),
                bound("[{name}](force-style:gold)").render((key, value) -> value.component(MENTION)).compact());
        assertEquals(Component.text("Text", NamedTextColor.BLUE),
                Text.of("[Text](color:red, force-color:blue)").render().compact());
    }

    @Test
    void forcedShadowOverridesTheShadowInside() {
        final Style shadowed = Style.style().shadowColor(ShadowColor.shadowColor(0xFF0000FF)).build();
        assertEquals(Component.text("Text", shadowed), Text.of("[$#ff0000Text](force-shadow:#0000ff)").render().compact());
        assertEquals(ShadowColor.shadowColor(0xFFFF0000),
                Text.of("[$#ff0000Text](shadow:#0000ff)").render().compact().style().shadowColor());
    }
}
