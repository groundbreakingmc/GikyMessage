package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionalSegmentTest extends TextTestBase {

    /** Reports strings as text, longs as numbers and components as they are; anything else is absent. */
    private static TestValues values(Object... pairs) {
        final Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        return (key, value) -> {
            final Object current = values.get(key);
            if (current instanceof String text) value.text(text);
            else if (current instanceof Long number) value.number(number);
            else if (current instanceof Component component) value.component(component);
        };
    }

    private static String plain(Component component) {
        final StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent text) builder.append(text.content());
        for (final Component child : component.children()) append(builder, child);
    }

    @Test
    void ifShowsTheSegmentOnlyWhileTheValueHasText() {
        // Brackets meant as text inside a segment are escaped.
        final Text<TestValues> text = bound("[&7\\[{clan}&7\\] ](if:{clan}){name}");
        assertEquals("[Knights] Steve", plain(text.render(values("clan", "Knights", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("clan", "", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("clan", "   ", "name", "Steve"))));
    }

    @Test
    void unlessShowsTheSegmentOnlyWhileTheValueHasNoText() {
        final Text<TestValues> text = bound("{name}[ &8(no clan)](unless:{clan})");
        assertEquals("Steve (no clan)", plain(text.render(values("clan", "", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("clan", "Knights", "name", "Steve"))));
    }

    @Test
    void componentsWithOnlyFormattingAreEmptyAndNumbersHaveText() {
        final Text<TestValues> text = bound("[<{prefix}> ](if:{prefix})x");
        assertEquals("x", plain(text.render(values("prefix", Component.text("", NamedTextColor.RED)))));
        assertEquals("x", plain(text.render(values("prefix",
                Component.text("").append(Component.text(" ", NamedTextColor.GOLD))))));
        assertEquals("<A> x", plain(text.render(values("prefix",
                Component.text("").append(Component.text("A", NamedTextColor.GOLD))))));
        assertEquals("<0> x", plain(text.render(values("prefix", 0L))));
        assertTrue(text.render(values("prefix", Component.translatable("myserver.rank"))).children().size() > 1);
    }

    @Test
    void missingValueRendersLiterallyAndCountsAsText() {
        assertEquals("[{clan}] Steve", plain(bound("[\\[{clan}\\] ](if:{clan}){name}").render(values("name", "Steve"))));
    }

    @Test
    void anyPlaceholderWithTextSatisfiesTheCondition() {
        final Text<TestValues> text = bound("[shown](if:\"{a} {b}\")");
        assertEquals("shown", plain(text.render(values("a", "", "b", "x"))));
        assertEquals("", plain(text.render(values("a", "", "b", ""))));
    }

    @Test
    void ifAndUnlessMustBothHold() {
        final Text<TestValues> text = bound("[{prefix} ](if:{prefix}, unless:{hidden}){name}");
        assertEquals("VIP Steve", plain(text.render(values("prefix", "VIP", "hidden", "", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("prefix", "VIP", "hidden", "yes", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("prefix", "", "hidden", "", "name", "Steve"))));
        assertEquals("x", plain(bound("[x](if:{a}, if:{b})").render(values("a", "", "b", "1"))));
    }

    @Test
    void staticConditionsAreDecidedWhileCompiling() {
        assertEquals("x", plain(Text.of("[x](if:yes)").render()));
        assertEquals("", plain(Text.of("[x](if:\"\")").render()));
        assertEquals("", plain(Text.of("[x](unless:yes)").render()));
        assertEquals("x", plain(Text.of("[x](unless:\" \")").render()));
        assertTrue(Text.of("[x](if:yes) [y](unless:yes)").keys().isEmpty());
    }

    @Test
    void conditionsCombineWithActionsAndNest() {
        final Component shown = bound("[{clan}](if:{clan}, run:\"/clan {clan}\")").render(values("clan", "Knights"));
        assertEquals(ClickEvent.runCommand("/clan Knights"), shown.clickEvent());

        final Text<TestValues> nested = bound("[[{a}](if:{a})/[{b}](if:{b})](unless:{off})");
        assertEquals("1/2", plain(nested.render(values("a", "1", "b", "2", "off", ""))));
        assertEquals("/2", plain(nested.render(values("a", "", "b", "2", "off", ""))));
        assertEquals("", plain(nested.render(values("a", "1", "b", "2", "off", "on"))));
    }

    @Test
    void conditionKeysAreBoundInOrderAndBindingsReuseResults() {
        final Text<TestValues> text = bound("[{name}](if:{clan}, unless:{hidden})");
        assertEquals(List.of("name", "clan", "hidden"), text.keys());
        final BoundText<TestValues> binding = text.bind(values("name", "Steve", "clan", "", "hidden", ""));
        assertSame(binding.render(), binding.render());
    }
}
