package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Components that a handler reports as a string and the function that parses it. */
class ParsedValueTest {

    /** A report through {@code component(source, parser)}; any other object is reported as itself. */
    private record Parsed(String source, Function<String, Component> parser) {
    }

    private static final class Context {
        Object value;
        String suffix = "tail";
    }

    /** Parses like a formatter would and counts how often it had to. */
    private static final class CountingParser implements Function<String, Component> {
        int calls;

        @Override
        public Component apply(String source) {
            this.calls++;
            return Component.text(source, NamedTextColor.RED);
        }
    }

    private static final Function<String, Component> PLAIN = Component::text;
    private static final Function<String, Component> RED = source -> Component.text(source, NamedTextColor.RED);
    private static final Function<String, Component> NESTED = source ->
            Component.text("<").append(Component.text(source, NamedTextColor.GOLD)).append(Component.text(">"));
    private static final Function<String, Component> NOTHING = source -> null;

    private static Text<Context> compile(String raw, boolean cached) {
        final ResolverFactory<Context> factory = key -> (context, out) -> {
            final Object value = key.equals("suffix") ? context.suffix : context.value;
            if (value instanceof Parsed parsed) out.component(parsed.source(), parsed.parser());
            else if (value instanceof String string) out.text(string);
            else if (value instanceof Long number) out.number(number);
            else if (value != null) out.component((Component) value);
        };
        return cached ? Text.cacheableOf(raw, factory) : Text.of(raw, factory);
    }

    @Test
    void bindingParsesOnlyForAnotherStringOrParser() {
        final Context context = new Context();
        final CountingParser parser = new CountingParser();
        final BoundText<Context> binding = compile("{value}/{suffix}", false).bind(context);

        context.value = new Parsed("Admin", parser);
        final Component first = binding.render();
        assertEquals(withComponent("Admin", "tail"), first);
        assertEquals(1, parser.calls);

        assertSame(first, binding.render());
        context.value = new Parsed(new String("Admin"), parser);
        assertSame(first, binding.render());
        assertEquals(1, parser.calls);

        // Another key changes: the template is rebuilt around the component already parsed.
        context.suffix = "other";
        assertEquals(withComponent("Admin", "other"), binding.render());
        assertEquals(1, parser.calls);

        context.value = new Parsed("VIP", parser);
        assertEquals(withComponent("VIP", "other"), binding.render());
        assertEquals(2, parser.calls);

        // The same string through another parser instance is parsed by that instance.
        final CountingParser replacement = new CountingParser();
        context.value = new Parsed("VIP", replacement);
        binding.render();
        assertEquals(2, parser.calls);
        assertEquals(1, replacement.calls);
        binding.render();
        assertEquals(1, replacement.calls);
    }

    /** The same template with the value reported as the component that CountingParser produces. */
    private static Component withComponent(String value, String suffix) {
        final Context context = new Context();
        context.value = Component.text(value, NamedTextColor.RED);
        context.suffix = suffix;
        return compile("{value}/{suffix}", false).render(context);
    }

    @Test
    void rendersWithoutBindingParseEveryTime() {
        for (final boolean cached : new boolean[]{false, true}) {
            final Context context = new Context();
            final CountingParser parser = new CountingParser();
            final Text<Context> text = compile("&7<{value}>", cached);
            context.value = new Parsed("Admin", parser);
            final Component first = text.render(context);
            final Component again = text.render(context);
            assertEquals(2, parser.calls);
            assertEquals(first, again);

            context.value = Component.text("Admin", NamedTextColor.RED);
            assertEquals(text.render(context), first);
        }
    }

    @Test
    void nullSourceAndNullResultAreLiteralFallbacks() {
        final Context context = new Context();
        final Text<Context> text = compile("{value}!", false);
        final Component fallback = text.render();

        for (final Object absent : new Object[]{new Parsed(null, RED), new Parsed("anything", NOTHING)}) {
            context.value = absent;
            assertEquals(fallback, text.render(context));
            assertEquals(fallback, compile("{value}!", true).render(context));
            assertEquals(fallback, text.bind(context).render());
        }
    }

    @Test
    void nullParserIsRejectedEvenForANullSource() {
        final Context context = new Context();
        final Text<Context> text = compile("{value}", false);
        for (final String source : new String[]{"text", null}) {
            context.value = new Parsed(source, null);
            assertThrows(NullPointerException.class, () -> text.render(context));
            assertThrows(NullPointerException.class, () -> compile("{value}", true).render(context));
            assertThrows(NullPointerException.class, () -> text.bind(context).render());
        }
    }

    @Test
    void laterReportReplacesAParsedOneWithoutParsingIt() {
        final CountingParser parser = new CountingParser();
        final Text<Object> text = Text.of("{value}", key -> (context, out) -> {
            out.component("ignored", parser);
            out.text("kept");
        });
        final BoundText<Object> binding = text.bind(null);
        assertEquals(Component.text("kept"), binding.render());
        assertEquals(Component.text("kept"), binding.render());
        assertEquals(0, parser.calls);
    }

    @Test
    void everyTransitionMatchesAnOrdinaryRender() {
        final Component rich = Component.text("red", NamedTextColor.RED).append(Component.text("child"));
        final Object[] inputs = {null, "", "42", 42L, Component.text(42), rich, "{value}",
                new Parsed("42", PLAIN), new Parsed("42", RED), new Parsed("", PLAIN), new Parsed("x", NESTED),
                new Parsed("{value}", PLAIN), new Parsed("gone", NOTHING), new Parsed(null, PLAIN)};
        final List<String> templates = List.of("{value}", "{value}/{suffix}/{value}",
                "[{value}/{suffix}](color:red,style:bold)",
                "[{value}{value}/{suffix}](gradient:c-e)",
                "[x](copy:'{value}/{suffix}',show:'[{value}](color:blue)')",
                "{value}[{suffix}](style:italic){value}");
        for (final String raw : templates) {
            final Context context = new Context();
            final Text<Context> text = compile(raw, false);
            final BoundText<Context> binding = text.bind(context);
            for (final Object before : inputs) {
                for (final Object after : inputs) {
                    context.value = before;
                    binding.render();
                    context.value = after;
                    context.suffix = context.suffix.equals("tail") ? "other" : "tail";
                    final Component expected = text.render(context);
                    final Component actual = binding.render();
                    assertEquals(expected, actual, raw + ": " + before + " -> " + after);
                    assertSame(actual, binding.render(), raw);
                }
            }
        }
    }

    @Test
    void equalValueOfAnotherTypeKeepsTheResultAndIsNotParsedAgain() {
        final Context context = new Context();
        final CountingParser parser = new CountingParser();
        final BoundText<Context> binding = compile("{value}/{suffix}", false).bind(context);

        context.value = Component.text("Admin", NamedTextColor.RED);
        final Component result = binding.render();

        // The parser runs once to learn that the result is the same, then never for this string.
        context.value = new Parsed("Admin", parser);
        assertSame(result, binding.render());
        assertSame(result, binding.render());
        assertEquals(1, parser.calls);

        context.value = Component.text("Admin", NamedTextColor.RED);
        assertSame(result, binding.render());
        context.value = "Admin";
        assertNotSame(result, binding.render());
        assertEquals(1, parser.calls);
    }

    @Test
    void parserFailureClearsTheBindingAndTheNextRenderParsesAgain() {
        final Context context = new Context();
        final CountingParser parser = new CountingParser();
        final Function<String, Component> failing = source -> {
            throw new IllegalStateException("failed");
        };
        final BoundText<Context> binding = compile("{value}/{suffix}", false).bind(context);

        context.value = new Parsed("Admin", parser);
        final Component first = binding.render();
        context.value = new Parsed("Broken", failing);
        assertThrows(IllegalStateException.class, binding::render);

        context.value = new Parsed("Admin", parser);
        final Component recovered = binding.render();
        assertEquals(first, recovered);
        assertNotSame(first, recovered);
        assertEquals(2, parser.calls);
    }
}
