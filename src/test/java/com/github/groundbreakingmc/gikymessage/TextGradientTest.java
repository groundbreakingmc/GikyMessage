package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Gradients in {@code color}, {@code shadow} and {@code style}, and their forced forms: a value
 * of colors joined by {@code -} is spread over the characters of the segment.
 */
class TextGradientTest extends TextTestBase {

    private static final TextColor RED = TextColor.color(0xFF0000);
    private static final TextColor BLUE = TextColor.color(0x0000FF);
    // Halfway between red and blue, as the gradient rounds it.
    private static final TextColor PURPLE = TextColor.color(0x800080);

    private static final ShadowColor BLACK_SHADOW = ShadowColor.shadowColor(0xFF000000);
    private static final ShadowColor BLUE_SHADOW = ShadowColor.shadowColor(0xFF0000FF);
    private static final ShadowColor NAVY_SHADOW = ShadowColor.shadowColor(0xFF000080);

    /** The text of every non-empty text node, in order. */
    private static List<String> texts(Component component) {
        final List<String> texts = new ArrayList<>();
        walk(component, null, null, texts, null, null);
        return texts;
    }

    /** The effective color of every non-empty text node, in order. */
    private static List<TextColor> colors(Component component) {
        final List<TextColor> colors = new ArrayList<>();
        walk(component, null, null, null, colors, null);
        return colors;
    }

    /** The effective shadow of every non-empty text node, in order. */
    private static List<ShadowColor> shadows(Component component) {
        final List<ShadowColor> shadows = new ArrayList<>();
        walk(component, null, null, null, null, shadows);
        return shadows;
    }

    private static void walk(Component component, TextColor color, ShadowColor shadow,
                             List<String> texts, List<TextColor> colors, List<ShadowColor> shadows) {
        final TextColor ownColor = component.color() != null ? component.color() : color;
        final ShadowColor ownShadow = component.shadowColor() != null ? component.shadowColor() : shadow;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            if (texts != null) texts.add(text.content());
            if (colors != null) colors.add(ownColor);
            if (shadows != null) shadows.add(ownShadow);
        }
        for (final Component child : component.children()) walk(child, ownColor, ownShadow, texts, colors, shadows);
    }

    /** The first text node with this text. */
    private static Component find(Component component, String content) {
        if (component instanceof TextComponent text && text.content().equals(content)) return component;
        for (final Component child : component.children()) {
            final Component found = find(child, content);
            if (found != null) return found;
        }
        return null;
    }

    private static Component render(String template, String value) {
        return bound(template).render((key, out) -> out.text(value));
    }

    private static Component render(String template, Component value) {
        return bound(template).render((key, out) -> out.component(value));
    }

    @Test
    void colorTakesAGradient() {
        assertEquals(List.of(RED, PURPLE, BLUE), colors(Text.of("[ABC](color:#ff0000-#0000ff)").render()));
        assertEquals(List.of("A", "B", "C"), texts(Text.of("[ABC](color:#ff0000-#0000ff)").render()));
        for (final String value : new String[]{"c-e", "&c-&e", "#ff5555-#ffff55", "&#ff5555-&#ffff55", "red-yellow"}) {
            assertEquals(List.of(0xFF5555, 0xFFFF55), values(Text.of("[AB](color:" + value + ")").render()), value);
        }
        assertEquals(List.of(0xFF5555, 0xFFAA00, 0x5555FF), values(Text.of("[ABC](color:red-gold-blue)").render()));
    }

    private static List<Integer> values(Component component) {
        return colors(component).stream().map(TextColor::value).toList();
    }

    @Test
    void aGradientFromAPlaceholderMatchesAFixedOne() {
        assertEquals(Text.of("[ABC](color:#ff0000-#0000ff)").render(),
                render("[ABC](color:{format})", "#ff0000-#0000ff"));
        assertEquals(Text.of("[ABC](force-color:c-e)").render(), render("[ABC](force-color:{format})", "c-e"));
        // A single color from the same placeholder stays a color of the whole segment.
        assertEquals(Component.text("ABC", NamedTextColor.RED), render("[ABC](color:{format})", "red"));
    }

    @Test
    void aPlainGradientKeepsTheColorsInsideAndGoesOnAfterThem() {
        assertEquals(List.of(RED, NamedTextColor.YELLOW, BLUE),
                colors(Text.of("[A[B](color:yellow)C](color:#ff0000-#0000ff)").render()));
        assertEquals(List.of(RED, NamedTextColor.YELLOW),
                colors(Text.of("[A&eB](color:#ff0000-#0000ff)").render()));

        final Component mention = Component.text("A")
                .append(Component.text("B", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/msg B")))
                .append(Component.text("C"));
        final Component rendered = render("[{message}](color:#ff0000-#0000ff)", mention);
        assertEquals(List.of("A", "B", "C"), texts(rendered));
        assertEquals(List.of(RED, NamedTextColor.YELLOW, BLUE), colors(rendered));
    }

    @Test
    void aForcedGradientColorsEveryCharacter() {
        final Component mention = Component.text("A")
                .append(Component.text("B", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/msg B")))
                .append(Component.text("C"));
        final Component rendered = render("[{message}](force-color:#ff0000-#0000ff)", mention);
        assertEquals(List.of(RED, PURPLE, BLUE), colors(rendered));
        assertEquals(ClickEvent.runCommand("/msg B"), find(rendered, "B").clickEvent());
        assertEquals(List.of(RED, PURPLE, BLUE), colors(Text.of("[A&eBC](force-color:#ff0000-#0000ff)").render()));
    }

    @Test
    void shadowTakesAGradient() {
        assertEquals(List.of(BLACK_SHADOW, NAVY_SHADOW, BLUE_SHADOW),
                shadows(Text.of("[ABC](shadow:#000000-#0000ff)").render()));
        for (final String value : new String[]{"$#000000-$#0000ff", "$#000000-#0000ff", "000000-$0000ff", "#000000:ff-0000ff"}) {
            assertEquals(Text.of("[ABC](shadow:#000000-#0000ff)").render(),
                    Text.of("[ABC](shadow:" + value + ")").render(), value);
        }
        // Alpha is spread like the color.
        assertEquals(List.of(ShadowColor.shadowColor(0x80000000), ShadowColor.shadowColor(0x601A1A1A),
                        ShadowColor.shadowColor(0x40333333)),
                shadows(Text.of("[ABC](shadow:#000000:80-#333333:40)").render()));
        assertEquals(Text.of("[ABC](shadow:#000000-#0000ff)").render(),
                render("[ABC](shadow:{format})", "#000000-#0000ff"));
    }

    @Test
    void aPlainShadowGradientKeepsTheShadowsInside() {
        final ShadowColor red = ShadowColor.shadowColor(0xFFFF0000);
        // BC keeps its shadow and is not split; the gradient still counts it.
        final Component plain = Text.of("[A$#ff0000BC](shadow:#000000-#0000ff)").render();
        assertEquals(List.of("A", "BC"), texts(plain));
        assertEquals(List.of(BLACK_SHADOW, red), shadows(plain));
        assertEquals(List.of(BLACK_SHADOW, NAVY_SHADOW, BLUE_SHADOW),
                shadows(Text.of("[A$#ff0000BC](force-shadow:#000000-#0000ff)").render()));
    }

    @Test
    void styleTakesAColorAndAShadowGradient() {
        final Component rendered = Text.of("[ABC](style:\"#ff0000-#0000ff bold $#000000-#0000ff\")").render();
        assertEquals(List.of(RED, PURPLE, BLUE), colors(rendered));
        assertEquals(List.of(BLACK_SHADOW, NAVY_SHADOW, BLUE_SHADOW), shadows(rendered));
        assertEquals(TextDecoration.State.TRUE, rendered.decoration(TextDecoration.BOLD));
        assertEquals(rendered, render("[ABC](style:{format})", "#ff0000-#0000ff bold $#000000-#0000ff"));
        // Codes may follow each other without spaces.
        assertEquals(Text.of("[ABC](style:\"c-e bold\")").render(), Text.of("[ABC](style:&c-&e&l)").render());
    }

    @Test
    void invalidGradientsInAStyleMakeItInvalid() {
        for (final String value : new String[]{"c-", "-c", "c--e", "c-bold", "bold-italic", "c-&l", "$#000000-",
                "$#000000-red", "#f00-#00f"}) {
            assertNull(Look.parse(Look.STYLE, value, 0f), value);
            assertEquals(Component.text("Text"), render("[Text](style:{format})", value).compact(), value);
        }
    }

    @Test
    void theLastColorOfAStyleWins() {
        assertEquals(Component.text("AB", NamedTextColor.GREEN), Text.of("[AB](style:\"c-e green\")").render());
        assertEquals(List.of(0xFF5555, 0xFFFF55), values(Text.of("[AB](style:\"green c-e\")").render()));
    }

    @Test
    void colorAndShadowWinOverTheGradientsOfStyle() {
        assertEquals(Component.text("AB", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Text.of("[AB](style:\"c-e bold\", color:green)").render().compact());
        assertEquals(Text.of("[AB](shadow:#0000ff)").render(),
                Text.of("[AB](style:\"$#000000-#333333\", shadow:#0000ff)").render());
        assertEquals(List.of(RED, BLUE),
                colors(Text.of("[AB](style:green, color:#ff0000-#0000ff)").render()));
    }

    @Test
    void forcedWinsOverPlain() {
        assertEquals(Component.text("AB", NamedTextColor.GREEN), Text.of("[AB](color:c-e, force-color:green)").render().compact());
        assertEquals(List.of(RED, BLUE), colors(Text.of("[AB](color:green, force-color:#ff0000-#0000ff)").render()));
        assertEquals(List.of(RED, BLUE), colors(Text.of("[AB](force-style:green, force-color:#ff0000-#0000ff)").render()));
    }

    @Test
    void anInvalidOrEmptyValueKeepsTheLookOfTheTextBefore() {
        for (final String value : new String[]{"", "red-", "red-nope", "#ff0000-#00f", "-"}) {
            assertEquals(Component.text("Text", NamedTextColor.GREEN),
                    render("&a[Text](color:{format})", value).compact(), value);
        }
        for (final String value : new String[]{"", "#000000-", "#000000-red", "none-#000000"}) {
            assertEquals(Component.text("Text"), render("[Text](shadow:{format})", value).compact(), value);
        }
    }

    @Test
    void phaseShiftsColorAndShadowGradients() {
        assertEquals(List.of(BLUE, PURPLE, RED), colors(Text.of("[ABC](color:#ff0000-#0000ff, phase:0.5)").render()));
        assertEquals(List.of(BLUE_SHADOW, NAVY_SHADOW, BLACK_SHADOW),
                shadows(Text.of("[ABC](shadow:#000000-#0000ff, phase:0.5)").render()));
        final Component styled = render("[ABC](style:{format}, phase:0.5)", "#ff0000-#0000ff $#000000-#0000ff");
        assertEquals(List.of(BLUE, PURPLE, RED), colors(styled));
        assertEquals(List.of(BLUE_SHADOW, NAVY_SHADOW, BLACK_SHADOW), shadows(styled));
    }

    @Test
    void contentThatIsNotTextTakesTheFirstColor() {
        assertEquals(Component.translatable("myserver.ab", "AB").color(RED),
                Text.of("[AB](color:#ff0000-#0000ff, lang:myserver.ab)").render());
        assertEquals(Component.translatable("myserver.ab", "AB").shadowColor(BLACK_SHADOW),
                Text.of("[AB](shadow:#000000-#0000ff, lang:myserver.ab)").render());
        assertEquals(Component.translatable("item.minecraft.diamond").color(RED),
                render("[{item}](color:#ff0000-#0000ff)", Component.translatable("item.minecraft.diamond")).compact());
    }

    @Test
    void theCacheOfPlaceholderValuesKeepsEveryValueApart() {
        final Text<TestValues> text = cached("[{name}](color:{format})");
        final String[] values = {"#ff0000-#0000ff", "#00ff00-#0000ff", "red", "nope", "#ff0000-#0000ff", "c-e"};
        for (int round = 0; round < 2; round++) {
            for (final String value : values) {
                for (final String name : new String[]{"AB", "ABCD"}) {
                    final Component rendered = text.render((key, out) -> out.text(key.equals("name") ? name : value));
                    assertEquals(Text.of("[" + name + "](color:" + value + ")").render().compact(), rendered.compact(),
                            value + " " + name);
                }
            }
        }
    }

    @Test
    void aShadowFromAPlaceholderInsideAnotherSegmentIsNotFrozen() {
        final Text<TestValues> text = bound("[[x](shadow:{format})](run:/a)");
        assertEquals(BLACK_SHADOW, text.render((key, out) -> out.text("#000000")).compact().shadowColor());
        assertEquals(BLUE_SHADOW, text.render((key, out) -> out.text("#0000ff")).compact().shadowColor());
    }
}
