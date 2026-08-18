package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class TextImpl implements Text {

    private final Token token;
    private final String[] phKeys;

    /**
     * Per-render component buffer — fed into the token tree on every dynamic render.
     * {@code null} when there are no placeholders.
     */
    private final ThreadLocal<Component[]> compPhBuffer;

    /**
     * Persistent cache of the last-seen component value for each placeholder index.
     * Non-null only when the {@code Text} was created via {@link Text#cacheableOf}.
     */
    private final Component[] componentCache;

    /**
     * Cached result for fully-static render (no placeholders or empty resolver).
     */
    private Component staticCache;

    TextImpl(Token token, String[] phKeys, boolean cacheable) {
        this.token = token;
        this.phKeys = phKeys;
        final boolean hasCache = cacheable && phKeys.length > 0;
        this.componentCache = hasCache ? new Component[phKeys.length] : null;
        this.compPhBuffer = phKeys.length > 0
                ? ThreadLocal.withInitial(() -> new Component[phKeys.length])
                : null;
    }

    // ── render() overloads ───────────────────────────────────────────────────

    @Override
    public @NotNull Component render() {
        if (this.staticCache != null) return this.staticCache;
        if (this.phKeys.length == 0) {
            return this.staticCache = this.token.render(null);
        }
        // Has placeholders — resolve with EMPTY (all → sentinel fallback)
        final Component[] buf = this.compPhBuffer.get();
        resolveAll(Resolver.EMPTY, buf);
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull Resolver resolver) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        resolveAll(resolver, buf);
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull Map<String, Component> replacements) {
        return render(new Resolver.MappedResolver(replacements));
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k, k.equals(k0) ? v0 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 :
                                                    k.equals(k4) ? v4 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 :
                                                    k.equals(k4) ? v4 :
                                                            k.equals(k5) ? v5 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5,
                                     @NotNull String k6, @NotNull Component v6) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 :
                                                    k.equals(k4) ? v4 :
                                                            k.equals(k5) ? v5 :
                                                                    k.equals(k6) ? v6 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5,
                                     @NotNull String k6, @NotNull Component v6,
                                     @NotNull String k7, @NotNull Component v7) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 :
                                                    k.equals(k4) ? v4 :
                                                            k.equals(k5) ? v5 :
                                                                    k.equals(k6) ? v6 :
                                                                            k.equals(k7) ? v7 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5,
                                     @NotNull String k6, @NotNull Component v6,
                                     @NotNull String k7, @NotNull Component v7,
                                     @NotNull String k8, @NotNull Component v8) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 :
                                                    k.equals(k4) ? v4 :
                                                            k.equals(k5) ? v5 :
                                                                    k.equals(k6) ? v6 :
                                                                            k.equals(k7) ? v7 :
                                                                                    k.equals(k8) ? v8 : null);
        }
        return this.token.render(buf);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5,
                                     @NotNull String k6, @NotNull Component v6,
                                     @NotNull String k7, @NotNull Component v7,
                                     @NotNull String k8, @NotNull Component v8,
                                     @NotNull String k9, @NotNull Component v9) {
        if (this.phKeys.length == 0) return this.render();
        final Component[] buf = this.compPhBuffer.get();
        for (int i = 0; i < this.phKeys.length; i++) {
            final String k = this.phKeys[i];
            fillSlot(buf, i, k,
                    k.equals(k0) ? v0 :
                            k.equals(k1) ? v1 :
                                    k.equals(k2) ? v2 :
                                            k.equals(k3) ? v3 :
                                                    k.equals(k4) ? v4 :
                                                            k.equals(k5) ? v5 :
                                                                    k.equals(k6) ? v6 :
                                                                            k.equals(k7) ? v7 :
                                                                                    k.equals(k8) ? v8 :
                                                                                            k.equals(k9) ? v9 : null);
        }
        return this.token.render(buf);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
     * Fills the component buffer for all placeholder keys using the given resolver.
     */
    private void resolveAll(Resolver resolver, Component[] buf) {
        for (int i = 0; i < this.phKeys.length; i++) {
            fillSlot(buf, i, this.phKeys[i], resolver.resolve(this.phKeys[i]));
        }
    }

    /**
     * Writes one resolved component into the buffer at index {@code i}.
     *
     * <p>When {@code val} is {@code null} the cache is checked first; if the cache also
     * has nothing, a sentinel fallback text component is written.
     */
    private void fillSlot(Component[] buf, int i, String key, Component val) {
        if (val != null) {
            buf[i] = val;
            if (this.componentCache != null) this.componentCache[i] = val;
        } else if (this.componentCache != null && this.componentCache[i] != null) {
            buf[i] = this.componentCache[i];
        } else {
            buf[i] = Component.text("{+" + key + "+}");
        }
    }
}
