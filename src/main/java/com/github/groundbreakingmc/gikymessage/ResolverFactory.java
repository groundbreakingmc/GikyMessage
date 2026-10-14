package com.github.groundbreakingmc.gikymessage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Selects a value handler once for each unique key after a template has been parsed.
 * The text stores the returned handlers, not the factory. A handler may retain its
 * own captured services; use its context argument for data that varies per render.
 *
 * @param <C> the context accepted by the returned handlers
 * @since 1.0
 */
@FunctionalInterface
public interface ResolverFactory<C> {

    /**
     * Binds one key. Unknown keys may return null for literal fallback.
     *
     * @param key the case-sensitive key without braces; may be empty
     * @return the handler, or null for the literal {@code {key}} fallback
     * @throws RuntimeException if binding fails; compilation stops without returning a text
     * @since 1.0
     */
    @Nullable ValueResolver<C> resolve(@NotNull String key);
}
