package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BoundTextTest {

    private static final class Context {
        Object value = "first";
        String suffix = "tail";
        String failKey;
        boolean error;
        final List<String> calls = new ArrayList<>();
    }

    private static void report(Object value, ValueResolver.Value out) {
        if (value == null) return;
        if (value instanceof String string) out.text(string);
        else if (value instanceof Long number) out.number(number);
        else out.component((Component) value);
    }

    private static Text<Context> compile(String raw, boolean cached) {
        ResolverFactory<Context> factory = key -> (context, out) -> {
            context.calls.add(key);
            report(key.equals("suffix") ? context.suffix : context.value, out);
            if (key.equals(context.failKey)) {
                if (context.error) throw new AssertionError("failed");
                throw new IllegalStateException("failed");
            }
        };
        return cached ? Text.cacheableOf(raw, factory) : Text.of(raw, factory);
    }

    @Test
    void creationDoesNotResolveAndBindingsDoNotEvictEachOther() {
        Context a = new Context(), b = new Context();
        b.value = "second";
        Text<Context> text = compile("{value}/{suffix}", false);
        BoundText<Context> left = text.bind(a), right = text.bind(b);
        assertTrue(a.calls.isEmpty());
        assertTrue(b.calls.isEmpty());
        Component first = left.render(), second = right.render();
        assertEquals(Component.text("first/tail"), first);
        assertEquals(Component.text("second/tail"), second);
        assertSame(first, left.render());
        assertSame(second, right.render());
        a.value = "changed";
        assertEquals(Component.text("changed/tail"), left.render());
        assertSame(second, right.render());
    }

    @Test
    void uniqueKeyOrderAndDiscardedCallsAreKeptOnHits() {
        Context c = new Context();
        BoundText<Context> binding = compile("{b}/{a}/{b}[{dead}](head:Steve)[x](copy:'{c}')", false).bind(c);
        for (int n = 0; n < 3; n++) {
            c.calls.clear();
            binding.render();
            assertEquals(List.of("b", "a", "dead", "c"), c.calls);
        }
    }

    @Test
    void allNativeComponentAndAbsentTransitionsMatchOrdinaryRender() {
        Component rich = Component.text("red", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/command"))
                .append(Component.translatable("test").append(Component.text("child")));
        Object[] inputs = {null, "", "Ж😀é", "42", 42L, Component.text(42), Long.MIN_VALUE,
                Long.MAX_VALUE, rich, Component.empty(), "{value}"};
        List<String> templates = List.of("{value}", "{value}/{suffix}/{value}",
                "[{value}/{suffix}](color:red,style:bold)",
                "[{value}{value}/{suffix}](gradient:c-e)",
                "[x](copy:'{value}/{suffix}',show:'[{value}](color:blue)')",
                "{value}[{suffix}](style:italic){value}");
        for (String raw : templates) for (boolean cached : new boolean[]{false, true}) {
            Context c = new Context();
            Text<Context> text = compile(raw, cached);
            BoundText<Context> binding = text.bind(c);
            for (Object before : inputs) for (Object after : inputs) {
                c.value = before;
                binding.render();
                c.value = after;
                c.suffix = c.suffix.equals("tail") ? "other" : "tail";
                Component expected = text.render(c), actual = binding.render();
                assertEquals(expected, actual, raw + ": " + before + " -> " + after);
                assertSame(actual, binding.render(), raw);
            }
        }
    }

    @Test
    void equalReportsOfDifferentTypesReuseResultIncludingFallback() {
        Context c = new Context();
        BoundText<Context> binding = compile("{value}/{suffix}", false).bind(c);
        c.value = "42";
        Component result = binding.render();
        for (Object value : new Object[]{42L, Component.text(42), new String("42"), 42L}) {
            c.value = value;
            assertSame(result, binding.render());
        }
        c.value = null;
        Component literal = binding.render();
        c.value = "{value}";
        assertSame(literal, binding.render());
        c.value = Component.text("{value}");
        assertSame(literal, binding.render());
        c.value = null;
        assertSame(literal, binding.render());
    }

    @Test
    void noReportAndNullDiscardEarlierReportsAndEmptyIsDifferent() {
        Text<Integer> text = Text.of("{value}", key -> (mode, out) -> {
            if (mode == 0) out.number(99);
            else if (mode == 1) { out.number(99); out.text(null); }
            else if (mode == 2) { out.text("discard"); out.component(null); }
            else if (mode == 3) { out.text("discard"); out.text(""); }
            else if (mode == 4) { out.text("discard"); out.number(-1); }
        });
        for (int mode = 0; mode < 6; mode++) {
            BoundText<Integer> binding = text.bind(mode);
            assertEquals(text.render(mode), binding.render());
            assertEquals(text.render(mode), binding.render());
        }
        Context c = new Context();
        BoundText<Context> binding = compile("{value}", false).bind(c);
        binding.render();
        c.value = null;
        assertEquals(Component.text("{value}"), binding.render());
        c.value = "";
        assertEquals(Component.empty(), binding.render());
    }

    @Test
    void missingHandlersNullContextsAndStaticTextsWork() {
        Text<Object> text = Text.of("{present}/{missing}", key -> key.equals("present")
                ? (context, out) -> { assertNull(context); out.text("yes"); } : null);
        assertEquals(Component.text("yes/{missing}"), text.bind(null).render());
        for (Text<Void> literal : List.of(Text.EMPTY, Text.of("static"), Text.of("[x](color:red)"),
                Text.of("{literal}"), Text.cacheableOf("{literal}"))) {
            BoundText<Void> binding = literal.bind(null);
            Component result = binding.render();
            assertEquals(literal.render(), result);
            assertSame(result, binding.render());
        }
    }

    @Test
    void materializedNumberRemainsCorrectWhenOtherKeysChange() {
        Context c = new Context();
        c.value = Long.MIN_VALUE;
        Text<Context> text = compile("{value}/{value}/{suffix}[copy](copy:'{value}')", false);
        BoundText<Context> binding = text.bind(c);
        for (String suffix : List.of("a", "b", "c")) {
            c.suffix = suffix;
            assertEquals(text.render(c), binding.render());
        }
        c.value = Long.MAX_VALUE;
        assertEquals(text.render(c), binding.render());
    }

    @Test
    void unchangedRichInsertionsRetainTheirInstancesOnPartialChanges() throws Exception {
        Context c = new Context();
        c.value = "stable";
        BoundText<Context> binding = compile("{value}[{suffix}](style:bold)", false).bind(c);
        c.suffix = "tail";
        binding.render();
        Object[] values = (Object[]) field(binding, "values");
        Object insertion = values[0];
        assertInstanceOf(Component.class, insertion);
        assertEquals(Component.text("stable"), insertion);
        c.suffix = "different";
        binding.render();
        assertSame(insertion, values[0]);
    }

    @Test
    void failuresAtEveryPositionClearReferencesAndRecoverCompletely() throws Exception {
        for (String key : List.of("a", "b", "c")) for (boolean error : new boolean[]{false, true}) {
            Context c = new Context();
            BoundText<Context> binding = compile("{a}/{b}/{c}", false).bind(c);
            Component saved = binding.render();
            c.failKey = key;
            c.error = error;
            c.value = Component.text("failed", NamedTextColor.RED);
            c.calls.clear();
            if (error) assertThrows(AssertionError.class, binding::render);
            else assertThrows(IllegalStateException.class, binding::render);
            assertEquals(List.of("a", "b", "c").subList(0, List.of("a", "b", "c").indexOf(key) + 1), c.calls);
            assertNull(field(binding, "result"));
            for (String array : List.of("previous", "values")) for (Object value : (Object[]) field(binding, array)) assertNull(value);
            assertNull(field(field(binding, "pending"), "value"));
            c.failKey = null;
            c.value = "recovered";
            c.calls.clear();
            assertEquals(Component.text("recovered/recovered/recovered"), binding.render());
            assertEquals(List.of("a", "b", "c"), c.calls);
            assertEquals(Component.text("first/first/first"), saved);
        }
    }

    @Test
    void rendererFailureDoesNotLeaveAPartiallyValidCache() throws Exception {
        Context c = new Context();
        Text<Context> text = compile("[x](head:{value})", false);
        c.value = "Steve";
        BoundText<Context> binding = text.bind(c);
        Component good = binding.render();
        c.value = "a".repeat(17);
        assertThrows(IllegalArgumentException.class, () -> text.render(c));
        assertThrows(IllegalArgumentException.class, binding::render);
        assertNull(field(binding, "result"));
        c.value = "Steve";
        assertEquals(good, binding.render());
    }

    @Test
    void sameBindingReentryIsRejectedAndOtherBindingsCanNest() {
        AtomicReference<BoundText<Context>> self = new AtomicReference<>();
        Context c = new Context();
        Text<Context> text = Text.of("{value}", key -> (context, out) -> {
            if (context.value.equals("reenter")) self.get().render();
            out.text((String) context.value);
        });
        BoundText<Context> binding = text.bind(c);
        self.set(binding);
        c.value = "reenter";
        assertThrows(IllegalStateException.class, binding::render);
        c.value = "good";
        assertEquals(Component.text("good"), binding.render());
        BoundText<String> inner = Text.<String>of("{inner}", key -> (context, out) -> out.text(context)).bind("inner");
        Text<String> outer = Text.of("{outer}", key -> (context, out) -> {
            out.text(context);
            assertEquals(Component.text("inner"), inner.render());
            Context ordinary = new Context();
            ordinary.value = "ordinary";
            assertEquals(Component.text("ordinary"), text.render(ordinary));
        });
        assertEquals(Component.text("outer"), outer.bind("outer").render());
    }

    @Test
    void bindingDoesNotReplaceOrdinaryThreadCache() {
        Context a = new Context(), b = new Context();
        b.value = "b";
        Text<Context> text = compile("{value}/{suffix}", true);
        Component ordinary = text.render(a);
        BoundText<Context> binding = text.bind(b);
        binding.render();
        assertSame(ordinary, text.render(a));
    }

    @Test
    void sequentialOwnershipCanBeTransferredWithFutureHappensBefore() throws Exception {
        Context c = new Context();
        BoundText<Context> binding = compile("{value}/{suffix}", false).bind(c);
        Component first = binding.render();
        try (var worker = Executors.newSingleThreadExecutor()) {
            assertSame(first, worker.submit(binding::render).get(5, TimeUnit.SECONDS));
            c.value = "changed";
            assertEquals(Component.text("changed/tail"), worker.submit(binding::render).get(5, TimeUnit.SECONDS));
        }
        assertEquals(Component.text("changed/tail"), binding.render());
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
