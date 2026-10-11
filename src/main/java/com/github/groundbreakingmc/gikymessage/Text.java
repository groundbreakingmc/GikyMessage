package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A compiled Adventure template with formatting, events, and {@code {key}} placeholders.
 * Keep the instance for repeated rendering.
 *
 * <p>Keys are case-sensitive and omit braces. Missing or {@code null} values use
 * literal {@code {key}} fallbacks; use {@link Component#empty()} for empty text.
 * Replacements are not parsed as templates. Enclosing formatting actions can
 * modify them; following static text keeps the template's style.
 * String-valued actions use text-node content, recursively visiting children.
 *
 * <p>Key/value overloads ignore unused keys and use the first pair for duplicate
 * keys. Rendering runs on the calling thread; callers must arrange safe access
 * to resolver/map data and obey any external API's thread requirements.
 *
 * @since 1.0.0
 */
public sealed interface Text permits TextImpl {

    /**
     * The shared empty text.
     *
     * @since 1.0.0
     */
    Text EMPTY = new TextImpl(new Token.Plain("", Style.empty()), new String[0], false);

    /**
     * Compiles a template without the optional result cache; static parts are reused.
     * Parsing is permissive: unclosed groups are closed at end of input, unclosed
     * placeholders stay literal, and unknown actions are ignored.
     *
     * @param raw the format string
     * @return the compiled text, or {@link #EMPTY} for empty or {@code null} input
     * @throws TextFormatException      if a selected sprite key is invalid, including in hover text
     * @throws IllegalArgumentException if Adventure rejects another action value
     * @since 1.0.0
     */
    static Text of(@NotNull String raw) {
        return Compiler.compile(raw, false);
    }

    /**
     * Compiles a template with a last-result cache per instance and physical thread.
     * Each render still obtains all values, applies fallbacks, and compares the full
     * set using component equality. Equal sets reuse the result; different sets
     * replace the retained values and result. This also applies to {@link #render()}.
     * Texts without compiled keys need no result cache.
     *
     * <p>Compiles on every factory call; parsing is the same as {@link #of(String)}.
     *
     * @param raw the format string
     * @return the compiled text, or {@link #EMPTY} for empty or {@code null} input
     * @throws TextFormatException      if a selected sprite key is invalid, including in hover text
     * @throws IllegalArgumentException if Adventure rejects another action value
     * @since 1.0.0
     */
    static Text cacheableOf(@NotNull String raw) {
        return Compiler.compile(raw, true);
    }

    /**
     * Renders with literal {@code {key}} fallbacks for all placeholders.
     * Formatting and actions still apply; previous values are not reused.
     *
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render();

    /**
     * Resolves each distinct compiled key once, before checking the result cache.
     * Calls follow compiler registration order, which can differ from written action
     * order and includes keys in content replaced by an action. Repeated keys share
     * a value; no compiled keys means no calls. The resolver is not retained.
     *
     * @param resolver the current value provider; {@code null} results use literal fallbacks
     * @return the rendered component
     * @throws NullPointerException     if resolver is {@code null} and the text has compiled keys
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @throws RuntimeException         if resolution fails; remaining keys are not resolved
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull Resolver resolver);

    /**
     * Reads {@link Map#get(Object)} once per compiled key, in resolver order,
     * even on a cache hit. Missing entries and {@code null} values use literal
     * fallbacks. The map is not copied or retained; keep it stable during a call
     * when a consistent set is required.
     *
     * @param replacements the current values, keyed without braces
     * @return the rendered component
     * @throws NullPointerException     if replacements is {@code null} and the text has compiled keys
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @throws RuntimeException         if a map lookup fails
     * @see #render(Resolver)
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull Map<String, Component> replacements);

    /**
     * Renders with one key/component pair.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0);

    /**
     * Renders with two key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1);

    /**
     * Renders with three key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2);

    /**
     * Renders with four key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3);

    /**
     * Renders with five key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @param k4 a placeholder key without braces
     * @param v4 the replacement for {@code k4}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4);

    /**
     * Renders with six key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @param k4 a placeholder key without braces
     * @param v4 the replacement for {@code k4}
     * @param k5 a placeholder key without braces
     * @param v5 the replacement for {@code k5}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5);

    /**
     * Renders with seven key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @param k4 a placeholder key without braces
     * @param v4 the replacement for {@code k4}
     * @param k5 a placeholder key without braces
     * @param v5 the replacement for {@code k5}
     * @param k6 a placeholder key without braces
     * @param v6 the replacement for {@code k6}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6);

    /**
     * Renders with eight key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @param k4 a placeholder key without braces
     * @param v4 the replacement for {@code k4}
     * @param k5 a placeholder key without braces
     * @param v5 the replacement for {@code k5}
     * @param k6 a placeholder key without braces
     * @param v6 the replacement for {@code k6}
     * @param k7 a placeholder key without braces
     * @param v7 the replacement for {@code k7}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6,
                              @NotNull String k7, @NotNull Component v7);

    /**
     * Renders with nine key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @param k4 a placeholder key without braces
     * @param v4 the replacement for {@code k4}
     * @param k5 a placeholder key without braces
     * @param v5 the replacement for {@code k5}
     * @param k6 a placeholder key without braces
     * @param v6 the replacement for {@code k6}
     * @param k7 a placeholder key without braces
     * @param v7 the replacement for {@code k7}
     * @param k8 a placeholder key without braces
     * @param v8 the replacement for {@code k8}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
    @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                              @NotNull String k1, @NotNull Component v1,
                              @NotNull String k2, @NotNull Component v2,
                              @NotNull String k3, @NotNull Component v3,
                              @NotNull String k4, @NotNull Component v4,
                              @NotNull String k5, @NotNull Component v5,
                              @NotNull String k6, @NotNull Component v6,
                              @NotNull String k7, @NotNull Component v7,
                              @NotNull String k8, @NotNull Component v8);

    /**
     * Renders with ten key/component pairs.
     *
     * @param k0 a placeholder key without braces
     * @param v0 the replacement for {@code k0}
     * @param k1 a placeholder key without braces
     * @param v1 the replacement for {@code k1}
     * @param k2 a placeholder key without braces
     * @param v2 the replacement for {@code k2}
     * @param k3 a placeholder key without braces
     * @param v3 the replacement for {@code k3}
     * @param k4 a placeholder key without braces
     * @param v4 the replacement for {@code k4}
     * @param k5 a placeholder key without braces
     * @param v5 the replacement for {@code k5}
     * @param k6 a placeholder key without braces
     * @param v6 the replacement for {@code k6}
     * @param k7 a placeholder key without braces
     * @param v7 the replacement for {@code k7}
     * @param k8 a placeholder key without braces
     * @param v8 the replacement for {@code k8}
     * @param k9 a placeholder key without braces
     * @param v9 the replacement for {@code k9}
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0.0
     */
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
