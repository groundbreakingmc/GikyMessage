package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradientPhaseTest extends TextTestBase {

    private static final TextColor RED = TextColor.color(0xFF0000);
    private static final TextColor BLUE = TextColor.color(0x0000FF);
    // Halfway between red and blue, as the gradient rounds it.
    private static final TextColor PURPLE = TextColor.color(0x800080);

    /** The effective color of every non-empty text node, in order. */
    private static List<TextColor> colors(Component component) {
        final List<TextColor> colors = new ArrayList<>();
        collect(component, null, colors);
        return colors;
    }

    private static void collect(Component component, TextColor inherited, List<TextColor> colors) {
        final TextColor color = component.color() != null ? component.color() : inherited;
        if (component instanceof TextComponent text && !text.content().isEmpty()) colors.add(color);
        for (final Component child : component.children()) collect(child, color, colors);
    }

    @Test
    void zeroPhaseIsThePlainGradientAndWholeCyclesChangeNothing() {
        final Component plain = Text.of("[Hello](gradient:#ff0000-#00ff00-#0000ff)").render();
        assertEquals(plain, Text.of("[Hello](gradient:#ff0000-#00ff00-#0000ff, phase:0)").render());
        assertEquals(plain, Text.of("[Hello](gradient:#ff0000-#00ff00-#0000ff, phase:1)").render());
        assertEquals(plain, Text.of("[Hello](gradient:#ff0000-#00ff00-#0000ff, phase:-2)").render());
    }

    @Test
    void phaseMovesTheColorsAlongTheLoop() {
        assertEquals(List.of(RED, PURPLE, BLUE), colors(Text.of("[ABC](gradient:#ff0000-#0000ff)").render()));
        // Half of the loop red -> blue -> red: the text now runs from blue back to red.
        assertEquals(List.of(BLUE, PURPLE, RED), colors(Text.of("[ABC](gradient:#ff0000-#0000ff, phase:0.5)").render()));
        assertEquals(List.of(PURPLE, BLUE, PURPLE), colors(Text.of("[ABC](gradient:#ff0000-#0000ff, phase:0.25)").render()));
    }

    @Test
    void negativePhasesMoveTheOtherWay() {
        assertEquals(colors(Text.of("[ABCDE](gradient:#ff0000-#00ff00-#0000ff, phase:0.75)").render()),
                colors(Text.of("[ABCDE](gradient:#ff0000-#00ff00-#0000ff, phase:-0.25)").render()));
        assertEquals(colors(Text.of("[ABCDE](gradient:#ff0000-#00ff00-#0000ff, phase:0.4)").render()),
                colors(Text.of("[ABCDE](gradient:#ff0000-#00ff00-#0000ff, phase:\"1.4\")").render()));
    }

    @Test
    void placeholderContentUsesThePhase() {
        final Component rendered = bound("[{name}](gradient:#ff0000-#0000ff, phase:0.5)")
                .render((key, value) -> value.text("ABC"));
        assertEquals(List.of(BLUE, PURPLE, RED), colors(rendered));
    }

    @Test
    void invalidPhaseIsIgnoredAndAPhaseWithoutGradientDoesNothing() {
        final Component plain = Text.of("[ABC](gradient:#ff0000-#0000ff)").render();
        assertEquals(plain, Text.of("[ABC](gradient:#ff0000-#0000ff, phase:half)").render());
        assertEquals(plain, Text.of("[ABC](gradient:#ff0000-#0000ff, phase:1e5)").render());
        assertEquals(Text.of("[ABC]").render(), Text.of("[ABC](phase:0.5)").render());
    }

    @Test
    void lastPhaseWins() {
        assertEquals(Text.of("[ABC](gradient:#ff0000-#0000ff, phase:0.5)").render(),
                Text.of("[ABC](gradient:#ff0000-#0000ff, phase:0.1, phase:0.5)").render());
    }
}
