package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

final class TextImpl implements Text {

    private final Token token;
    private final String[] placeholderKeys;
    private final Component[] fallbacks;
    private final ThreadLocal<RenderContext> renderContext;
    private final boolean cacheable;

    TextImpl(Token token, String[] placeholderKeys, boolean cacheable) {
        this.token = Objects.requireNonNull(token, "token");
        this.placeholderKeys = Objects.requireNonNull(placeholderKeys, "placeholderKeys");
        this.cacheable = cacheable;

        final int placeholderCount = this.placeholderKeys.length;
        this.fallbacks = new Component[placeholderCount];
        for (int index = 0; index < placeholderCount; index++) {
            this.fallbacks[index] = Component.text("{" + this.placeholderKeys[index] + "}");
        }

        this.renderContext = placeholderCount == 0
                ? null
                : ThreadLocal.withInitial(() -> new RenderContext(placeholderCount, cacheable));
    }

    @Override
    public @NotNull Component render() {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            this.fillSlot(buffer, index, null);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull Resolver resolver) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            this.fillSlot(buffer, index, resolver.resolve(this.placeholderKeys[index]));
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull Map<String, Component> replacements) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            this.fillSlot(buffer, index, replacements.get(this.placeholderKeys[index]));
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    key.equals(k4) ? v4 :
                                                            null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    key.equals(k4) ? v4 :
                                                            key.equals(k5) ? v5 :
                                                                    null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5,
                                     @NotNull String k6, @NotNull Component v6) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    key.equals(k4) ? v4 :
                                                            key.equals(k5) ? v5 :
                                                                    key.equals(k6) ? v6 :
                                                                            null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
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
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    key.equals(k4) ? v4 :
                                                            key.equals(k5) ? v5 :
                                                                    key.equals(k6) ? v6 :
                                                                            key.equals(k7) ? v7 :
                                                                                    null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
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
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    key.equals(k4) ? v4 :
                                                            key.equals(k5) ? v5 :
                                                                    key.equals(k6) ? v6 :
                                                                            key.equals(k7) ? v7 :
                                                                                    key.equals(k8) ? v8 :
                                                                                            null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
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
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < this.placeholderKeys.length; index++) {
            final String key = this.placeholderKeys[index];
            final Component value =
                    key.equals(k0) ? v0 :
                            key.equals(k1) ? v1 :
                                    key.equals(k2) ? v2 :
                                            key.equals(k3) ? v3 :
                                                    key.equals(k4) ? v4 :
                                                            key.equals(k5) ? v5 :
                                                                    key.equals(k6) ? v6 :
                                                                            key.equals(k7) ? v7 :
                                                                                    key.equals(k8) ? v8 :
                                                                                            key.equals(k9) ? v9 :
                                                                                                    null;
            this.fillSlot(buffer, index, value);
        }
        return this.renderBuffer(context);
    }

    String[] placeholderKeys() {
        return this.placeholderKeys;
    }

    Component renderMapped(Component[] source, int[] sourceIndices) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderContext context = this.renderContext.get();
        final Component[] buffer = context.buffer;
        for (int index = 0; index < sourceIndices.length; index++) {
            this.fillSlot(buffer, index, source[sourceIndices[index]]);
        }
        return this.renderBuffer(context);
    }

    private void fillSlot(Component[] buffer, int index, Component value) {
        buffer[index] = value == null ? this.fallbacks[index] : value;
    }

    private Component renderBuffer(RenderContext context) {
        try {
            if (!this.cacheable) return this.token.render(context.buffer);

            if (context.hasCachedResult && Arrays.equals(context.buffer, context.cachedValues)) {
                return context.cachedResult;
            }

            final Component rendered = this.token.render(context.buffer);
            System.arraycopy(
                    context.buffer,
                    0,
                    context.cachedValues,
                    0,
                    context.buffer.length
            );
            context.cachedResult = rendered;
            context.hasCachedResult = true;
            return rendered;
        } finally {
            Arrays.fill(context.buffer, null);
        }
    }

    private static final class RenderContext {

        private final Component[] buffer;
        private final Component[] cachedValues;
        private Component cachedResult;
        private boolean hasCachedResult;

        private RenderContext(int placeholderCount, boolean cacheable) {
            this.buffer = new Component[placeholderCount];
            this.cachedValues = cacheable ? new Component[placeholderCount] : null;
        }
    }

}
