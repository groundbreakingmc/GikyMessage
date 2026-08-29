package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

final class TextImpl implements Text {

    private static final Component[] EMPTY_COMPONENTS = new Component[0];
    private static final ThreadLocal<RenderScratch> RENDER_SCRATCH =
            ThreadLocal.withInitial(RenderScratch::new);

    private final Token token;
    private final String[] placeholderKeys;
    private volatile Component[] fallbacks;
    private final ThreadLocal<RenderCache> renderCache;
    private final boolean cacheable;

    TextImpl(Token token, String[] placeholderKeys, boolean cacheable) {
        this.token = Objects.requireNonNull(token, "token");
        this.placeholderKeys = Objects.requireNonNull(placeholderKeys, "placeholderKeys");
        this.cacheable = cacheable;

        final int placeholderCount = this.placeholderKeys.length;
        this.renderCache = placeholderCount == 0 || !cacheable
                ? null
                : ThreadLocal.withInitial(() -> new RenderCache(placeholderCount));
    }

    @Override
    public @NotNull Component render() {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                this.fillSlot(buffer, index, null);
            }
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull Resolver resolver) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                this.fillSlot(buffer, index, resolver.resolve(this.placeholderKeys[index]));
            }
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull Map<String, Component> replacements) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                this.fillSlot(buffer, index, replacements.get(this.placeholderKeys[index]));
            }
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                final String key = this.placeholderKeys[index];
                final Component value =
                        key.equals(k0) ? v0 :
                                null;
                this.fillSlot(buffer, index, value);
            }
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                final String key = this.placeholderKeys[index];
                final Component value =
                        key.equals(k0) ? v0 :
                                key.equals(k1) ? v1 :
                                        null;
                this.fillSlot(buffer, index, value);
            }
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                final String key = this.placeholderKeys[index];
                final Component value =
                        key.equals(k0) ? v0 :
                                key.equals(k1) ? v1 :
                                        key.equals(k2) ? v2 :
                                                null;
                this.fillSlot(buffer, index, value);
            }
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    @Override
    public @NotNull Component render(@NotNull String k0, @NotNull Component v0,
                                     @NotNull String k1, @NotNull Component v1,
                                     @NotNull String k2, @NotNull Component v2,
                                     @NotNull String k3, @NotNull Component v3,
                                     @NotNull String k4, @NotNull Component v4,
                                     @NotNull String k5, @NotNull Component v5) {
        if (this.placeholderKeys.length == 0) return this.token.render(null);

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
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

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
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

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
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

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
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

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Component[] buffer = renderBuffer.components;
        try {
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
            return this.renderResolved(buffer);
        } finally {
            renderBuffer.release();
        }
    }

    private void fillSlot(Component[] buffer, int index, Component value) {
        buffer[index] = value == null ? this.fallback(index) : value;
    }

    private Component fallback(int index) {
        Component[] fallbacks = this.fallbacks;
        if (fallbacks == null) {
            fallbacks = new Component[this.placeholderKeys.length];
            for (int current = 0; current < fallbacks.length; current++) {
                fallbacks[current] = Component.text("{" + this.placeholderKeys[current] + "}");
            }
            this.fallbacks = fallbacks;
        }
        return fallbacks[index];
    }

    private Component renderResolved(Component[] buffer) {
        if (!this.cacheable) return this.token.render(buffer);

        final RenderCache cache = this.renderCache.get();
        if (cache.hasCachedResult && matches(buffer, cache.cachedValues)) {
            return cache.cachedResult;
        }

        final Component rendered = this.token.render(buffer);
        System.arraycopy(buffer, 0, cache.cachedValues, 0, cache.cachedValues.length);
        cache.cachedResult = rendered;
        cache.hasCachedResult = true;
        return rendered;
    }

    private static boolean matches(Component[] buffer, Component[] cachedValues) {
        for (int index = 0; index < cachedValues.length; index++) {
            if (!Objects.equals(buffer[index], cachedValues[index])) return false;
        }
        return true;
    }

    private static final class RenderCache {

        private final Component[] cachedValues;
        private Component cachedResult;
        private boolean hasCachedResult;

        private RenderCache(int placeholderCount) {
            this.cachedValues = new Component[placeholderCount];
        }
    }

    private static final class RenderScratch {

        private RenderBuffer[] buffers = new RenderBuffer[4];
        private int depth;

        private RenderBuffer acquire(int size) {
            if (this.depth == this.buffers.length) {
                this.buffers = Arrays.copyOf(this.buffers, this.buffers.length << 1);
            }

            RenderBuffer buffer = this.buffers[this.depth];
            if (buffer == null) {
                buffer = new RenderBuffer(this);
                this.buffers[this.depth] = buffer;
            }
            this.depth++;
            buffer.prepare(size);
            return buffer;
        }

        private void release(RenderBuffer buffer) {
            buffer.clear();
            this.depth--;
        }
    }

    private static final class RenderBuffer {

        private static final int MAX_RETAINED_CAPACITY = 256;

        private final RenderScratch owner;
        private Component[] components = EMPTY_COMPONENTS;
        private int size;

        private RenderBuffer(RenderScratch owner) {
            this.owner = owner;
        }

        private void prepare(int size) {
            if (this.components.length < size) {
                int capacity = 8;
                while (capacity < size) capacity <<= 1;
                this.components = new Component[capacity];
            }
            this.size = size;
        }

        private void release() {
            this.owner.release(this);
        }

        private void clear() {
            Arrays.fill(this.components, 0, this.size, null);
            if (this.components.length > MAX_RETAINED_CAPACITY) {
                this.components = EMPTY_COMPONENTS;
            }
            this.size = 0;
        }
    }

}
