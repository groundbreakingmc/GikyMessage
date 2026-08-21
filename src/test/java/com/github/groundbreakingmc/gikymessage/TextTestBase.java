package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared helpers for all {@link Text} / {@link Compiler} test classes.
 */
abstract class TextTestBase {

    static final MiniMessage MM = MiniMessage.miniMessage();

    static void assertRenders(Component expected, String input) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(Text.of(input).render()),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(Text.of(input).render(k0, Component.text(v0))),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0,
                              String k1, String v1) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(Text.of(input).render(k0, Component.text(v0), k1, Component.text(v1))),
                "format: " + input
        );
    }

    static void assertRenders(Component expected, String input,
                              String k0, String v0,
                              String k1, String v1,
                              String k2, String v2) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(Text.of(input).render(k0, Component.text(v0), k1, Component.text(v1), k2, Component.text(v2))),
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
                MM.serialize(Text.of(input).render(k0, Component.text(v0), k1, Component.text(v1), k2, Component.text(v2), k3, Component.text(v3))),
                "format: " + input
        );
    }
}
