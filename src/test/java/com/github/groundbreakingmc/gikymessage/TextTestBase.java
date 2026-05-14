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

    static void assertRenders(Component expected, String input, String... pairs) {
        assertEquals(
                MM.serialize(expected),
                MM.serialize(Text.of(input).render(pairsToResolver(pairs))),
                "format: " + input
        );
    }

    static Resolver pairsToResolver(String[] pairs) {
        return switch (pairs.length) {
            case 2 -> new Resolver.Resolver1(
                    pairs[0], Component.text(pairs[1]));
            case 4 -> new Resolver.Resolver2(
                    pairs[0], Component.text(pairs[1]),
                    pairs[2], Component.text(pairs[3]));
            case 6 -> new Resolver.Resolver3(
                    pairs[0], Component.text(pairs[1]),
                    pairs[2], Component.text(pairs[3]),
                    pairs[4], Component.text(pairs[5]));
            case 8 -> new Resolver.Resolver4(
                    pairs[0], Component.text(pairs[1]),
                    pairs[2], Component.text(pairs[3]),
                    pairs[4], Component.text(pairs[5]),
                    pairs[6], Component.text(pairs[7]));
            default -> throw new IllegalArgumentException("Add a ResolverN for arity " + pairs.length / 2);
        };
    }
}
