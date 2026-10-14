package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class NativeRenderTest {

    private record Values(String text, long number, Component component) {}

    private static ResolverFactory<Values> factory(boolean components) {
        return key -> switch (key) {
            case "s" -> (c, v) -> {
                if (components) v.component(c.text == null ? null : Component.text(c.text));
                else v.text(c.text);
            };
            case "n" -> (c, v) -> {
                if (components) v.component(Component.text(c.number));
                else v.number(c.number);
            };
            case "c" -> (c, v) -> v.component(c.component);
            default -> null;
        };
    }

    @Test
    void nativeAndComponentValuesProduceTheSameFullTree() {
        Component rich = Component.text("red", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/red"))
                .append(Component.translatable("translation").append(Component.text("child")));
        List<String> templates = List.of("{s}", "{n}", "{n}{n}{n}", "&e{s}/{n}/{c}/{missing}",
                "[{s}/{n}/{c}](copy:'{n}/{s}/{c}/{n}',show:'{n}/{s}') {n}",
                "[{n}{n}{s}](gradient:c-e) {n}",
                "[{n}/{c}](style:bold,color:#123456) {n}",
                "[copy](copy:'{n}{n}{n}')", "[head](head:{s})");
        for (String raw : templates) for (boolean cached : new boolean[]{false, true}) {
            Text<Values> nativeText = cached ? Text.cacheableOf(raw, factory(false)) : Text.of(raw, factory(false));
            Text<Values> componentText = cached ? Text.cacheableOf(raw, factory(true)) : Text.of(raw, factory(true));
            for (long number : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, 0, -1, 10, -10}) {
                for (String string : new String[]{"Ж😀é", "", null}) {
                    Values context = new Values(string, number, rich);
                    assertSameOutcome(() -> componentText.render(context), () -> nativeText.render(context), raw);
                    assertSameOutcome(componentText::render, nativeText::render, raw);
                }
            }
        }
    }

    private static void assertSameOutcome(Supplier<Component> expected, Supplier<Component> actual, String raw) {
        final Component component;
        try {
            component = expected.get();
        } catch (IllegalArgumentException failure) {
            IllegalArgumentException other = assertThrows(failure.getClass(), actual::get, raw);
            assertEquals(failure.getMessage(), other.getMessage(), raw);
            return;
        }
        assertEquals(component, actual.get(), raw);
    }

    @Test
    void materializingRepeatedNumberDoesNotChangeLaterTextUses() {
        Values values = new Values("suffix", Long.MIN_VALUE, Component.text("styled", NamedTextColor.RED));
        Text<Values> text = Text.of("{n}/{c}/{n}[copy](copy:'{n}') {n}", factory(false));
        assertEquals(Text.of("{n}/{c}/{n}[copy](copy:'{n}') {n}", factory(true)).render(values), text.render(values));
        Component saved = text.render(values);
        text.render(new Values("new", 42, Component.text("new")));
        assertEquals(saved, text.render(values));
    }

    @Test
    void numericBufferGrowthAndReuseKeepEachSlotIndependent() {
        for (int count : new int[]{1, 8, 33, 300, 2, 65, 1}) {
            StringBuilder raw = new StringBuilder();
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < count; i++) {
                raw.append('{').append(i).append("}/");
                expected.append(Long.MIN_VALUE + i).append('/');
            }
            Text<Long> text = Text.of(raw.toString(), key -> {
                int offset = Integer.parseInt(key);
                return (base, value) -> value.number(base + offset);
            });
            assertEquals(Component.text(expected.toString()), text.render(Long.MIN_VALUE));
        }
    }

    @Test
    void lastReportReplacesNumbersAndMissingNeverReusesAnOldNumber() {
        Text<Integer> text = Text.of("{a}/{b}", key -> (mode, value) -> {
            if (mode == 0) value.number(Long.MIN_VALUE);
            else if (mode == 1) { value.number(42); value.text("final"); }
            else if (mode == 2) { value.text("old"); value.number(-9); }
            else if (mode == 3) { value.number(42); value.text(null); }
        });
        assertEquals(Component.text(Long.MIN_VALUE + "/" + Long.MIN_VALUE), text.render(0));
        assertEquals(Component.text("final/final"), text.render(1));
        assertEquals(Component.text("-9/-9"), text.render(2));
        assertEquals(Component.text("{a}/{b}"), text.render(3));
        assertEquals(Component.text("{a}/{b}"), text.render(4));
    }

    @Test
    void nestedNumericRenderDoesNotOverwriteAnOuterPendingValue() {
        AtomicReference<Text<Long>> reference = new AtomicReference<>();
        Text<Long> text = Text.of("{a}/{b}", key -> (context, value) -> {
            value.number(context);
            if (context == Long.MIN_VALUE && key.equals("a")) {
                assertEquals(Component.text("42/42"), reference.get().render(42L));
            }
        });
        reference.set(text);
        assertEquals(Component.text(Long.MIN_VALUE + "/" + Long.MIN_VALUE), text.render(Long.MIN_VALUE));
    }

    @Test
    void numericFailurePreservesPriorCacheAndAllowsRecovery() {
        Text<Long> text = Text.cacheableOf("{a}/{b}", key -> (context, value) -> {
            value.number(context);
            if (context == 0 && key.equals("b")) throw new IllegalStateException("failed");
        });
        Component good = text.render(Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> text.render(0L));
        assertSame(good, text.render(Long.MAX_VALUE));
        assertEquals(Component.text("-1/-1"), text.render(-1L));
    }

    @Test
    void nestedCachedAndUncachedCallsKeepTheirOwnValueMode() {
        for (boolean cached : new boolean[]{false, true}) {
            ResolverFactory<Long> innerFactory = key -> (context, value) -> value.number(context);
            Text<Long> inner = cached ? Text.of("{n}", innerFactory) : Text.cacheableOf("{n}", innerFactory);
            ResolverFactory<Long> outerFactory = key -> (context, value) -> {
                if (key.equals("n")) value.number(context);
                else value.text("outer");
                assertEquals(Component.text(42), inner.render(42L));
            };
            Text<Long> outer = cached ? Text.cacheableOf("{n}/{s}", outerFactory) : Text.of("{n}/{s}", outerFactory);
            assertEquals(Component.text(Long.MIN_VALUE + "/outer"), outer.render(Long.MIN_VALUE));
            assertEquals(Component.text(Long.MAX_VALUE + "/outer"), outer.render(Long.MAX_VALUE));
        }
    }
}
