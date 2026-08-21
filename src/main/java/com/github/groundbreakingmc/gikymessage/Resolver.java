package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

public interface Resolver {

    /**
     * Returns the value for {@code key}, or {@code null} when the key is not provided.
     */
    @Nullable Component resolve(String key);
}
