package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resolves placeholder keys to {@link Component}s at render time.
 *
 * <p>{@link #resolve} is the single contract: return a {@link Component} for the
 * given key, or {@code null} if the key is not handled.
 *
 * <p>Use one of the static factory implementations for common arities
 * ({@link Resolver1}–{@link Resolver10}), {@link MappedResolver} for map-based
 * resolution, or {@link ArrayResolver} for parallel key/component arrays.
 * For texts without placeholders use {@link #EMPTY}.
 */
public interface Resolver {

    Resolver EMPTY = k -> null;

    /**
     * Returns a {@link Component} for {@code key}, or {@code null} if not resolved.
     *
     * <p>The returned component becomes the <em>parent</em> of any immediately following
     * static text segment, so its color and decorations are inherited by that text unless
     * the segment overrides them explicitly.
     */
    @Nullable Component resolve(String key);

    // ── Built-in implementations ────────────────────────────────────────────

    class MappedResolver implements Resolver {

        private final Map<String, Component> replacements;

        public MappedResolver(Map<String, Component> replacements) {
            this.replacements = replacements;
        }

        @Override
        public @Nullable Component resolve(String key) {
            return this.replacements.get(key);
        }
    }

    class ArrayResolver implements Resolver {

        private final String[] keys;
        private final Component[] values;

        public ArrayResolver(String[] keys, Component[] values) {
            this.keys = keys;
            this.values = values;
        }

        @Override
        public @Nullable Component resolve(String key) {
            for (int i = 0; i < this.keys.length; i++) {
                if (this.keys[i].equals(key)) return this.values[i];
            }
            return Component.text('{' + key + '}');
        }
    }

    class Resolver1 implements Resolver {

        private final String k0;
        private final Component v0;

        public Resolver1(String k0, Component v0) {
            this.k0 = k0;
            this.v0 = v0;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver2 implements Resolver {

        private final String k0, k1;
        private final Component v0, v1;

        public Resolver2(String k0, Component v0, String k1, Component v1) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver3 implements Resolver {

        private final String k0, k1, k2;
        private final Component v0, v1, v2;

        public Resolver3(String k0, Component v0, String k1, Component v1, String k2, Component v2) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver4 implements Resolver {

        private final String k0, k1, k2, k3;
        private final Component v0, v1, v2, v3;

        public Resolver4(String k0, Component v0, String k1, Component v1,
                         String k2, Component v2, String k3, Component v3) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver5 implements Resolver {

        private final String k0, k1, k2, k3, k4;
        private final Component v0, v1, v2, v3, v4;

        public Resolver5(String k0, Component v0, String k1, Component v1,
                         String k2, Component v2, String k3, Component v3,
                         String k4, Component v4) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
            this.k4 = k4;
            this.v4 = v4;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            if (this.k4.equals(key)) return this.v4;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver6 implements Resolver {

        private final String k0, k1, k2, k3, k4, k5;
        private final Component v0, v1, v2, v3, v4, v5;

        public Resolver6(String k0, Component v0, String k1, Component v1,
                         String k2, Component v2, String k3, Component v3,
                         String k4, Component v4, String k5, Component v5) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
            this.k4 = k4;
            this.v4 = v4;
            this.k5 = k5;
            this.v5 = v5;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            if (this.k4.equals(key)) return this.v4;
            if (this.k5.equals(key)) return this.v5;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver7 implements Resolver {

        private final String k0, k1, k2, k3, k4, k5, k6;
        private final Component v0, v1, v2, v3, v4, v5, v6;

        public Resolver7(String k0, Component v0, String k1, Component v1,
                         String k2, Component v2, String k3, Component v3,
                         String k4, Component v4, String k5, Component v5,
                         String k6, Component v6) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
            this.k4 = k4;
            this.v4 = v4;
            this.k5 = k5;
            this.v5 = v5;
            this.k6 = k6;
            this.v6 = v6;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            if (this.k4.equals(key)) return this.v4;
            if (this.k5.equals(key)) return this.v5;
            if (this.k6.equals(key)) return this.v6;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver8 implements Resolver {

        private final String k0, k1, k2, k3, k4, k5, k6, k7;
        private final Component v0, v1, v2, v3, v4, v5, v6, v7;

        public Resolver8(String k0, Component v0, String k1, Component v1,
                         String k2, Component v2, String k3, Component v3,
                         String k4, Component v4, String k5, Component v5,
                         String k6, Component v6, String k7, Component v7) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
            this.k4 = k4;
            this.v4 = v4;
            this.k5 = k5;
            this.v5 = v5;
            this.k6 = k6;
            this.v6 = v6;
            this.k7 = k7;
            this.v7 = v7;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            if (this.k4.equals(key)) return this.v4;
            if (this.k5.equals(key)) return this.v5;
            if (this.k6.equals(key)) return this.v6;
            if (this.k7.equals(key)) return this.v7;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver9 implements Resolver {

        private final String k0, k1, k2, k3, k4, k5, k6, k7, k8;
        private final Component v0, v1, v2, v3, v4, v5, v6, v7, v8;

        public Resolver9(String k0, Component v0, String k1, Component v1,
                         String k2, Component v2, String k3, Component v3,
                         String k4, Component v4, String k5, Component v5,
                         String k6, Component v6, String k7, Component v7,
                         String k8, Component v8) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
            this.k4 = k4;
            this.v4 = v4;
            this.k5 = k5;
            this.v5 = v5;
            this.k6 = k6;
            this.v6 = v6;
            this.k7 = k7;
            this.v7 = v7;
            this.k8 = k8;
            this.v8 = v8;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            if (this.k4.equals(key)) return this.v4;
            if (this.k5.equals(key)) return this.v5;
            if (this.k6.equals(key)) return this.v6;
            if (this.k7.equals(key)) return this.v7;
            if (this.k8.equals(key)) return this.v8;
            return Component.text('{' + key + '}');
        }
    }

    class Resolver10 implements Resolver {

        private final String k0, k1, k2, k3, k4, k5, k6, k7, k8, k9;
        private final Component v0, v1, v2, v3, v4, v5, v6, v7, v8, v9;

        public Resolver10(String k0, Component v0, String k1, Component v1,
                          String k2, Component v2, String k3, Component v3,
                          String k4, Component v4, String k5, Component v5,
                          String k6, Component v6, String k7, Component v7,
                          String k8, Component v8, String k9, Component v9) {
            this.k0 = k0;
            this.v0 = v0;
            this.k1 = k1;
            this.v1 = v1;
            this.k2 = k2;
            this.v2 = v2;
            this.k3 = k3;
            this.v3 = v3;
            this.k4 = k4;
            this.v4 = v4;
            this.k5 = k5;
            this.v5 = v5;
            this.k6 = k6;
            this.v6 = v6;
            this.k7 = k7;
            this.v7 = v7;
            this.k8 = k8;
            this.v8 = v8;
            this.k9 = k9;
            this.v9 = v9;
        }

        @Override
        public @Nullable Component resolve(String key) {
            if (this.k0.equals(key)) return this.v0;
            if (this.k1.equals(key)) return this.v1;
            if (this.k2.equals(key)) return this.v2;
            if (this.k3.equals(key)) return this.v3;
            if (this.k4.equals(key)) return this.v4;
            if (this.k5.equals(key)) return this.v5;
            if (this.k6.equals(key)) return this.v6;
            if (this.k7.equals(key)) return this.v7;
            if (this.k8.equals(key)) return this.v8;
            if (this.k9.equals(key)) return this.v9;
            return Component.text('{' + key + '}');
        }
    }
}
