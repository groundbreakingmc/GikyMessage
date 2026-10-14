package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared helpers for all {@link Text} / {@link Compiler} test classes.
 */
abstract class TextTestBase {

    static final MiniMessage MM = MiniMessage.miniMessage();

    // Existing rendering fixtures use a functional context; production has no key-based adapter.
    @FunctionalInterface
    interface TestValues {
        void resolve(String key, ValueResolver.Value value);
    }

    static Text<TestValues> bound(String raw) {
        return Text.of(raw, key -> (context, value) -> context.resolve(key, value));
    }

    static Text<TestValues> cached(String raw) {
        return Text.cacheableOf(raw, key -> (context, value) -> context.resolve(key, value));
    }


    static void assertRenders(Component expected, String input) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(bound(input).render()),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(bound(input).render((slotKey, slotValue) -> slotValue.component(slotKey.equals(k0) ? Component.text(v0) : null))),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0,
                              String k1, String v1) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(bound(input).render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals(k0) ? Component.text(v0) : slotKey.equals(k1) ? Component.text(v1) : null))),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0,
                              String k1, String v1,
                              String k2, String v2) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(bound(input).render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals(k0) ? Component.text(v0) : slotKey.equals(k1) ? Component.text(v1) : slotKey.equals(k2) ? Component.text(v2) : null))),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0,
                              String k1, String v1,
                              String k2, String v2,
                              String k3, String v3) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(bound(input).render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals(k0) ? Component.text(v0) : slotKey.equals(k1) ? Component.text(v1) : slotKey.equals(k2) ? Component.text(v2) : slotKey.equals(k3) ? Component.text(v3) : null))),
                "format: " + input
        );
    }
}
