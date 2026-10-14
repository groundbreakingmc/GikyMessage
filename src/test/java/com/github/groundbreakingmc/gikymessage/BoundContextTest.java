package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BoundContextTest {

    @Test
    void bindsUniqueKeysOnceBeforeAnyValuesAndKeepsDiscardedKeyCalls() {
        List<String> bound = new ArrayList<>();
        List<String> resolved = new ArrayList<>();
        Text<String> text = Text.of(
                "{b}/{a}/{b}[x](run:'/{c}',show:'{a}/{d}')[{dead}](head:Steve)", key -> {
                    bound.add(key);
                    return (context, value) -> {
                        resolved.add(context + ":" + key);
                        value.text(context);
                    };
                });
        List<String> keys = List.of("b", "a", "c", "d", "dead");
        assertEquals(keys, bound);
        assertTrue(resolved.isEmpty());
        for (String context : List.of("first", "second", "first")) {
            resolved.clear();
            text.render(context);
            assertEquals(keys.stream().map(key -> context + ":" + key).toList(), resolved);
            assertEquals(keys, bound);
        }
    }

    @Test
    void typedHandlersReadEachNewContextWithoutRebinding() {
        AtomicInteger bindings = new AtomicInteger();
        Text<Account> text = Text.of("{name}: {balance}", key -> {
            bindings.incrementAndGet();
            return switch (key) {
                case "name" -> (context, value) -> value.text(context.name());
                case "balance" -> (context, value) -> value.number(context.balance());
                default -> throw new AssertionError(key);
            };
        });
        assertEquals(Component.text("Alice: " + Long.MIN_VALUE), text.render(new Account("Alice", Long.MIN_VALUE)));
        assertEquals(Component.text("Bob: " + Long.MAX_VALUE), text.render(new Account("Bob", Long.MAX_VALUE)));
        assertEquals(Component.text("Alice: 0"), text.render(new Account("Alice", 0)));
        assertEquals(2, bindings.get());
    }

    @Test
    void missingHandlerAndAbsentNullEmptyAndLastReportsKeepTheirMeaning() {
        Text<String> text = Text.of("{unknown}/{absent}/{nil}/{empty}/{last}", key -> switch (key) {
            case "unknown" -> null;
            case "absent" -> (context, value) -> {};
            case "nil" -> (context, value) -> { value.number(7); value.text(null); };
            case "empty" -> (context, value) -> value.text("");
            case "last" -> (context, value) -> {
                value.text("old");
                value.component(null);
                value.text(context);
            };
            default -> throw new AssertionError(key);
        });
        assertEquals(Component.text("{unknown}/{absent}/{nil}//new"), text.render("new"));
    }

    @Test
    void registryReplacementRequiresNewBindingButContextsRemainCurrent() {
        Map<String, ValueResolver<Account>> registry = new HashMap<>();
        registry.put("name", (context, value) -> value.text(context.name()));
        Text<Account> first = Text.of("{name}", registry::get);
        registry.put("name", (context, value) -> value.text("new:" + context.name()));
        Text<Account> second = Text.of("{name}", registry::get);
        Account account = new Account("Alice", 1);
        assertEquals(Component.text("Alice"), first.render(account));
        assertEquals(Component.text("new:Alice"), second.render(account));
        assertEquals(Component.text("Bob"), first.render(new Account("Bob", 2)));
        registry.clear();
        assertEquals(Component.text("Alice"), first.render(account));
        assertEquals(Component.text("new:Alice"), second.render(account));
    }

    @Test
    void factoryFailureStopsBindingWithoutCallingValues() {
        for (Throwable failure : List.of(new IllegalStateException("bind"), new AssertionError("bind"))) {
            List<String> bound = new ArrayList<>();
            Throwable actual = assertThrows(failure.getClass(), () -> Text.of("{a}/{b}/{c}", key -> {
                bound.add(key);
                if (key.equals("b")) throwFailure(failure);
                return (context, value) -> fail("Binding must not obtain values");
            }));
            assertSame(failure, actual);
            assertEquals(List.of("a", "b"), bound);
        }
    }

    @Test
    void syntaxFailurePrecedesBindingAndNullFactoryIsRejectedEvenForEmptyText() {
        assertThrows(TextFormatException.class, () -> Text.of("{a}[x](sprite:INVALID KEY)", key -> {
            fail("Malformed template must fail before binding");
            return null;
        }));
        assertThrows(NullPointerException.class, () -> Text.of("", null));
        assertThrows(NullPointerException.class, () -> Text.cacheableOf(null, null));
    }

    @Test
    void emptyAndStaticTextsNeverBindOrReadContexts() {
        ResolverFactory<Object> factory = key -> { throw new AssertionError(key); };
        assertSame(Text.EMPTY, Text.of(null, factory));
        assertSame(Text.EMPTY, Text.cacheableOf("", factory));
        for (Text<Object> text : List.of(Text.of("static", factory), Text.cacheableOf("&astatic", factory))) {
            assertSame(text.render(), text.render(new Object()));
            assertSame(text.render(), text.render(null));
        }
        assertEquals(Component.text("{literal}"), Text.of("{literal}").render(null));
    }

    @Test
    void literalRenderSkipsHandlersWhileNullContextIsForwarded() {
        AtomicInteger calls = new AtomicInteger();
        Text<String> text = Text.cacheableOf("{value}", key -> (context, value) -> {
            calls.incrementAndGet();
            assertNull(context);
            value.text("null-context");
        });
        assertEquals(Component.text("{value}"), text.render());
        assertEquals(0, calls.get());
        assertEquals(Component.text("null-context"), text.render(null));
        assertEquals(1, calls.get());
        assertEquals(Component.text("{value}"), text.render());
        assertEquals(1, calls.get());
    }

    @Test
    void cacheUsesComponentEqualityAcrossContextsAndNativeTypes() {
        AtomicInteger calls = new AtomicInteger();
        Text<Object> text = Text.cacheableOf("{value}", key -> (context, value) -> {
            calls.incrementAndGet();
            if (context instanceof Long number) value.number(number);
            else if (context instanceof String string) value.text(string);
            else value.component((Component) context);
        });
        Component first = text.render(42L);
        assertSame(first, text.render("42"));
        assertSame(first, text.render(Component.text("42")));
        Component changed = text.render(43L);
        assertNotEquals(first, changed);
        assertSame(changed, text.render(43L));
        Component restored = text.render(42L);
        assertEquals(first, restored);
        assertSame(restored, text.render("42"));
        assertEquals(7, calls.get());
        Component fallback = text.render(null);
        assertSame(fallback, text.render("{value}"));
    }

    @Test
    void nestedRenderOfSameBoundTextKeepsOuterReportedValue() {
        AtomicReference<Text<String>> reference = new AtomicReference<>();
        List<String> calls = new ArrayList<>();
        Text<String> text = Text.cacheableOf("{a}/{b}", key -> (context, value) -> {
            calls.add(context + ":" + key);
            value.text(context);
            if (context.equals("outer") && key.equals("a")) {
                assertEquals(Component.text("inner/inner"), reference.get().render("inner"));
            }
        });
        reference.set(text);
        assertEquals(Component.text("outer/outer"), text.render("outer"));
        assertEquals(List.of("outer:a", "inner:a", "inner:b", "outer:b"), calls);
    }

    @Test
    void failedHandlerStopsCallsAndPreservesLastSuccessfulCache() {
        for (Throwable failure : List.of(new IllegalStateException("value"), new AssertionError("value"))) {
            List<String> calls = new ArrayList<>();
            Text<String> text = Text.cacheableOf("{a}/{b}/{c}", key -> (context, value) -> {
                calls.add(key);
                value.text(context);
                if (context.equals("bad") && key.equals("b")) throwFailure(failure);
            });
            Component good = text.render("good");
            calls.clear();
            assertSame(failure, assertThrows(failure.getClass(), () -> text.render("bad")));
            assertEquals(List.of("a", "b"), calls);
            assertSame(good, text.render("good"));
            assertEquals(Component.text("new/new/new"), text.render("new"));
        }
    }

    @Test
    void sharedHandlersKeepConcurrentContextsAndThreadCachesIndependent() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Text<String> text = Text.cacheableOf("{value}", key -> (context, value) -> {
            value.text(context);
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> repeated(text, "A"));
            var b = executor.submit(() -> repeated(text, "B"));
            assertEquals(Component.text("A"), a.get(10, TimeUnit.SECONDS));
            assertEquals(Component.text("B"), b.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void parameterizedKeysBindBeyondHashThresholdAndRepeatOnlyOnce() {
        int count = 64;
        StringBuilder raw = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        long[] context = new long[count];
        for (int index = 0; index < count; index++) {
            raw.append("{v:").append(index).append("}/");
            context[index] = index * 3L;
            expected.append(context[index]).append('/');
        }
        raw.append("{v:0}");
        expected.append('0');
        AtomicInteger bindings = new AtomicInteger();
        List<Integer> calls = new ArrayList<>();
        Text<long[]> text = Text.of(raw.toString(), key -> {
            int index = Integer.parseInt(key.substring(2));
            bindings.incrementAndGet();
            return (values, value) -> { calls.add(index); value.number(values[index]); };
        });
        assertEquals(Component.text(expected.toString()), text.render(context));
        assertEquals(count, bindings.get());
        assertEquals(java.util.stream.IntStream.range(0, count).boxed().toList(), calls);
    }

    @Test
    void arbitraryComponentsKeepStylesEventsAndStaticTailIsolation() {
        Component context = Component.text("red", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/red"))
                .append(Component.text("child"));
        Text<Component> text = Text.of("&eX{value}Y", key -> (component, value) -> value.component(component));
        assertEquals(Component.text("X", NamedTextColor.YELLOW).append(context).append(Component.text("Y")),
                text.render(context));
    }

    private static Component repeated(Text<String> text, String context) {
        Component first = text.render(context);
        assertSame(first, text.render(context));
        return first;
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw (Error) failure;
    }

    private record Account(String name, long balance) {}
}
