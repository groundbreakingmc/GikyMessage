package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies placeholder components during synchronous {@link Text#render(Resolver)} calls.
 *
 * @since 1.0.0
 */
public interface Resolver {

    /**
     * Resolves a key without parsing the returned component as a template.
     *
     * @param key a non-null, case-sensitive key without braces; may be empty
     * @return the replacement, or {@code null} for literal {@code {key}} fallback;
     * use {@link Component#empty()} for empty text
     * @throws RuntimeException if resolution fails; propagated by {@code render}
     * @since 1.0.0
     */
    @Nullable Component resolve(String key);
}
