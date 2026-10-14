package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A handler bound to one placeholder key during template compilation.
 * Reads the current render context and reports its value without another key lookup.
 * Values are not parsed as templates.
 *
 * <p>Report a value through the supplied {@link Value}. Making no call or reporting
 * null means absent and renders the literal {@code {key}}; if called more than once,
 * the last value wins. Text and numbers render as unstyled text components.
 *
 * <p>Rendering uses text and numbers directly where possible. The optional text cache
 * compares component equality, even across different reported value types.
 *
 * @param <C> the render context type
 * @since 1.0
 */
@FunctionalInterface
public interface ValueResolver<C> {

    /**
     * Obtains this handler's value from the current context.
     *
     * @param context the current context, passed unchanged by the caller, including null
     * @param value   the receiver for this key's value; valid only during this call
     * @throws RuntimeException if resolution fails; propagated to the caller
     * @since 1.0
     */
    void resolve(@Nullable C context, @NotNull Value value);

    /**
     * Receives the value of the key being resolved. Not thread-safe; do not retain.
     *
     * @since 1.0
     */
    interface Value {

        /**
         * Reports a plain text value.
         *
         * @param text the text, or {@code null} for the literal {@code {key}} fallback
         * @since 1.0
         */
        void text(@Nullable String text);

        /**
         * Reports a number, rendered as its decimal text.
         *
         * @param number the value
         * @since 1.0
         */
        void number(long number);

        /**
         * Reports an arbitrary component; changes are detected with component equality.
         *
         * @param component the component, or {@code null} for the literal {@code {key}} fallback
         * @since 1.0
         */
        void component(@Nullable Component component);
    }
}
