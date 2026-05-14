package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A pre-compiled, renderable text that may contain inline styles, gradients,
 * click/hover events, and named placeholders ({@code {key}}).
 *
 * <p>Compile once, render many times:
 * <pre>{@code
 * Text greeting = Text.of("&aHello, {player}!");
 * Component rendered = greeting.render("player", playerName);
 * }</pre>
 *
 * <p>For texts that are compiled repeatedly from the same template string,
 * prefer {@link #cacheableOf} to enable per-placeholder result caching.
 */
public sealed interface Text permits TextImpl {

    Text EMPTY = new TextImpl(new Token.Plain("", StyleImpl.EMPTY), new String[0], false);

    /**
     * Compiles a format string into a {@link Text}.
     */
    static Text of(@NotNull String raw) {
        return Compiler.compile(raw, false);
    }

    /**
     * Compiles a format string into a {@link Text} with placeholder value caching enabled.
     *
     * <p>When the same placeholder key receives the same value repeatedly, the cached
     * resolved component is reused, reducing allocations on hot render paths.
     */
    static Text cacheableOf(@NotNull String raw) {
        return Compiler.compile(raw, true);
    }

    /**
     * Renders this text with no placeholder substitutions.
     */
    @NotNull Component render();

    /**
     * Renders this text, resolving placeholders via the given {@link Resolver}.
     */
    @NotNull Component render(@NotNull Resolver resolver);

    /**
     * Renders this text, resolving placeholders from the given map.
     */
    @NotNull Component render(@NotNull Map<String, Component> replacements);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6,
                              @NotNull String k7, @NotNull Component v7);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6,
                              @NotNull String k7, @NotNull Component v7,
                              @NotNull String k8, @NotNull Component v8);

    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6,
                              @NotNull String k7, @NotNull Component v7,
                              @NotNull String k8, @NotNull Component v8,
                              @NotNull String k9, @NotNull Component v9);
}
