package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextFontTest extends TextTestBase {

    private static final Key ICONS = Key.key("myserver", "icons");

    @Test
    void appliesANamespacedOrDefaultNamespaceFont() {
        assertEquals(Component.text("").font(ICONS), Text.of("[](font:myserver:icons)").render());
        assertEquals(Component.text("").font(ICONS), Text.of("[](font:\"myserver:icons\")").render());
        assertEquals(Component.text("A").font(Key.key("minecraft", "uniform")), Text.of("[A](font:uniform)").render());
    }

    @Test
    void fontStaysOnItsOwnSegment() {
        assertEquals(Component.text()
                        .append(Component.text("").font(ICONS))
                        .append(Component.text(" Name"))
                        .build(),
                Text.of("[](font:myserver:icons) Name").render());
        assertEquals(Component.text("Name ", NamedTextColor.GREEN)
                        .append(Component.text("").font(ICONS))
                        .append(Component.text("!")),
                Text.of("&aName [](font:myserver:icons)!").render());
    }

    @Test
    void fontCoversPlaceholderContentAndItsChildren() {
        final Component rendered = bound("[{icon}{name}](font:myserver:icons)").render((key, value) -> {
            if (key.equals("icon")) value.component(Component.text("", NamedTextColor.GOLD));
            else value.text("Steve");
        });
        // The font sits on the segment's root, so both children inherit it.
        assertEquals(ICONS, rendered.style().font());
        assertEquals(Component.text("", Style.style().font(ICONS).build())
                        .append(Component.text()
                                .append(Component.text("", NamedTextColor.GOLD))
                                .append(Component.text("Steve"))
                                .build()),
                rendered);
    }

    @Test
    void combinesWithOtherActionsAndFormatting() {
        assertEquals(Component.text("A", Style.style()
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/spawn"))
                        .insertion("x")
                        .font(ICONS)
                        .build()),
                Text.of("&c[A](run:\"/spawn\", font:myserver:icons, insert:x)").render());

        final Component gradient = Text.of("[AB](gradient:#ff0000-#0000ff, font:myserver:icons)").render();
        assertEquals(ICONS, gradient.style().font());
        assertEquals(1, gradient.children().size());
        assertNotNull(gradient.children().get(0).children().get(0).color());
    }

    @Test
    void lastFontWinsAndAnEmptyFontIsIgnored() {
        assertEquals(Component.text("A").font(Key.key("b", "second")),
                Text.of("[A](font:a:first, font:b:second)").render());
        assertEquals(Component.text("A"), Text.of("[A](font:\"\")").render());
        assertNull(Text.of("[A](font:a:first, font:\"\")").render().style().font());
    }

    @Test
    void invalidFontKeyIsReportedWithItsCause() {
        final TextFormatException failure = assertThrows(TextFormatException.class,
                () -> Text.of("[A](font:\"Not A Key\")"));
        assertEquals("Invalid font key: Not A Key", failure.getMessage());
        assertNotNull(failure.getCause());
        // Only the font that wins is parsed.
        assertEquals(Component.text("A").font(ICONS),
                Text.of("[A](font:\"Not A Key\", font:myserver:icons)").render());
    }

    @Test
    void fontValuesAreStatic() {
        // A placeholder is not resolved here; braces are not valid in a key.
        assertThrows(TextFormatException.class, () -> Text.of("[A](font:{font})"));
    }
}
