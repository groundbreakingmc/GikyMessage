package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A compiled Adventure template with handlers bound to its unique placeholder keys.
 * Keep the instance and supply the current context to {@link #render(Object)}.
 *
 * <p>Keys are case-sensitive and omit braces. Missing or null values use literal
 * {@code {key}} fallbacks; use an empty value for empty text. Replacements are not
 * parsed as templates. Enclosing actions can modify their formatting; following
 * static text keeps the template's style. String-valued actions recursively use
 * text-node content from replacements.
 *
 * <p>Rendering runs on the calling thread. Shared handlers must support their callers'
 * execution contexts and safely access their data. Direct render calls do not retain
 * the context; returned components and optional cached values may refer to its data.
 * A {@link #bind(Object)} binding retains its context for repeated renders.
 *
 * @param <C> the context passed to the bound handlers
 * @since 1.0
 */
public sealed interface Text<C> permits TextImpl {

    /**
     * The shared empty text, with no handlers.
     *
     * @since 1.0
     */
    Text<Void> EMPTY = new TextImpl<>(new Token.Plain("", Style.empty()), new String[0], false, null);

    /**
     * Compiles without handlers; placeholders keep their literal fallbacks.
     * Unclosed groups are closed at end of input, unclosed placeholders stay literal,
     * and unknown actions are ignored.
     *
     * @param raw the format string, or null for empty text
     * @return the compiled text
     * @throws TextFormatException      if a selected sprite key is invalid
     * @throws IllegalArgumentException if Adventure rejects another action value
     * @since 1.0
     */
    static @NotNull Text<Void> of(@Nullable String raw) {
        return Compiler.compile(raw, false, null);
    }

    /**
     * Compiles the template, then binds each unique key once in compiler registration
     * order. This includes keys in content replaced by an action. Binding obtains
     * handlers, not values; later registry changes do not replace stored handlers.
     *
     * @param raw     the format string, or null for empty text
     * @param factory the factory used only during this call; null handlers mean literal fallback
     * @param <C>     the render context type
     * @return the compiled text with its bound handlers
     * @throws NullPointerException     if factory is null
     * @throws TextFormatException      if a selected sprite key is invalid
     * @throws IllegalArgumentException if Adventure rejects another action value
     * @throws RuntimeException         if binding fails; remaining keys are not bound
     * @since 1.0
     */
    static <C> @NotNull Text<C> of(@Nullable String raw, @NotNull ResolverFactory<C> factory) {
        return Compiler.compile(raw, false, Objects.requireNonNull(factory, "factory"));
    }

    /**
     * Compiles without handlers, with the last-result cache described by
     * {@link #cacheableOf(String, ResolverFactory)}.
     *
     * @param raw the format string, or null for empty text
     * @return the compiled text with literal fallbacks
     * @throws TextFormatException      if a selected sprite key is invalid
     * @throws IllegalArgumentException if Adventure rejects another action value
     * @since 1.0
     */
    static @NotNull Text<Void> cacheableOf(@Nullable String raw) {
        return Compiler.compile(raw, true, null);
    }

    /**
     * Compiles and binds as {@link #of(String, ResolverFactory)}, retaining the last
     * complete value tuple and result per text and physical thread. Every contextual
     * render still calls its bound handlers and compares component values, including
     * styles and events. Equal tuples reuse the result; a different context can replace
     * it. This is not a cache indexed by context. Texts without keys need no result cache.
     *
     * @param raw     the format string, or null for empty text
     * @param factory the factory used only during this call
     * @param <C>     the render context type
     * @return the compiled text with bound handlers and an optional result cache
     * @throws NullPointerException     if factory is null
     * @throws TextFormatException      if a selected sprite key is invalid
     * @throws IllegalArgumentException if Adventure rejects another action value
     * @throws RuntimeException         if binding fails; remaining keys are not bound
     * @since 1.0
     */
    static <C> @NotNull Text<C> cacheableOf(@Nullable String raw, @NotNull ResolverFactory<C> factory) {
        return Compiler.compile(raw, true, Objects.requireNonNull(factory, "factory"));
    }

    /**
     * Renders literal fallbacks for every placeholder without calling handlers.
     * Formatting, actions and the optional result cache still apply.
     *
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @since 1.0
     */
    @NotNull Component render();

    /**
     * Calls each bound handler once in compiler registration order before checking
     * the result cache. Repeated keys share one value. No key lookup or binding is
     * performed here. Keys without a handler keep their literal fallback.
     *
     * @param context the current context, passed unchanged to handlers, including null
     * @return the rendered component
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @throws RuntimeException         if a handler fails; remaining handlers are not called
     * @since 1.0
     */
    @NotNull Component render(@Nullable C context);

    /**
     * Creates an independent cache for repeated renders under one context.
     * Creation does not call handlers. Keep the binding between renders; it retains
     * the context and uses its own cache even when this text is cacheable.
     *
     * @param context the context retained and passed unchanged to handlers, including null
     * @return a new binding for sequential use
     * @since 1.0
     */
    @NotNull BoundText<C> bind(@Nullable C context);
}
