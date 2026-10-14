package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

final class TextImpl<C> implements Text<C> {

    private static final Object[] EMPTY_VALUES = new Object[0];
    private static final ThreadLocal<RenderScratch> RENDER_SCRATCH =
            ThreadLocal.withInitial(RenderScratch::new);

    private final Token token;
    private final String[] placeholderKeys;
    private final ValueResolver<C>[] resolvers;
    private volatile Component[] fallbacks;
    private final ThreadLocal<RenderCache> renderCache;
    private final boolean cacheable;

    TextImpl(Token token, String[] placeholderKeys, boolean cacheable, ResolverFactory<C> factory) {
        this.token = Objects.requireNonNull(token, "token");
        this.placeholderKeys = Objects.requireNonNull(placeholderKeys, "placeholderKeys");
        this.cacheable = cacheable;
        this.resolvers = bind(this.placeholderKeys, factory);

        final int placeholderCount = this.placeholderKeys.length;
        this.renderCache = placeholderCount == 0 || !cacheable
                ? null
                : ThreadLocal.withInitial(() -> new RenderCache(placeholderCount));
    }

    private static <C> ValueResolver<C>[] bind(String[] keys, ResolverFactory<C> factory) {
        if (keys.length == 0 || factory == null) return null;
        @SuppressWarnings("unchecked") final ValueResolver<C>[] resolvers =
                (ValueResolver<C>[]) new ValueResolver<?>[keys.length];
        for (int index = 0; index < keys.length; index++) {
            resolvers[index] = factory.resolve(keys[index]);
        }
        return resolvers;
    }

    @Override
    public @NotNull Component render() {
        if (this.placeholderKeys.length == 0) return this.token.render(null, null);

        return this.renderResolved(this.fallbacks(), null);
    }

    @Override
    public @NotNull Component render(@Nullable C context) {
        if (this.resolvers == null) return this.render();

        final RenderBuffer renderBuffer = RENDER_SCRATCH.get().acquire(this.placeholderKeys.length);
        final Object[] buffer = renderBuffer.values;
        try {
            final ValueResolver.Value output = this.cacheable ? renderBuffer.componentValue() : renderBuffer;
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                renderBuffer.resolved = null;
                final ValueResolver<C> resolver = this.resolvers[index];
                if (resolver != null) resolver.resolve(context, output);
                final Object value = renderBuffer.resolved;
                buffer[index] = value == null ? this.fallbacks()[index] : value;
                if (value == Token.NUMBER_VALUE) renderBuffer.storeNumber(index);
            }
            return this.renderResolved(buffer, renderBuffer.numbers);
        } finally {
            renderBuffer.release();
        }
    }

    private Component[] fallbacks() {
        Component[] fallbacks = this.fallbacks;
        if (fallbacks == null) {
            fallbacks = new Component[this.placeholderKeys.length];
            for (int current = 0; current < fallbacks.length; current++) {
                fallbacks[current] = Component.text("{" + this.placeholderKeys[current] + "}");
            }
            this.fallbacks = fallbacks;
        }
        return fallbacks;
    }

    @Override
    public @NotNull BoundText<C> bind(@Nullable C context) {
        return new BoundText<>(this, context);
    }

    int bindingSize() {
        return this.resolvers == null ? 0 : this.resolvers.length;
    }

    void resolveBound(int index, C context, ValueResolver.Value output) {
        final ValueResolver<C> resolver = this.resolvers[index];
        if (resolver != null) resolver.resolve(context, output);
    }

    Component boundFallback(int index) {
        return this.fallbacks()[index];
    }

    Component renderBound(Object[] values, long[] numbers) {
        if (values == null && this.placeholderKeys.length != 0) values = this.fallbacks();
        return this.token.render(values, numbers);
    }

    private Component renderResolved(Object[] buffer, long[] numbers) {
        if (!this.cacheable) return this.token.render(buffer, numbers);

        final RenderCache cache = this.renderCache.get();
        if (cache.hasCachedResult && matches(buffer, cache.cachedValues)) {
            return cache.cachedResult;
        }

        final Component rendered = this.token.render(buffer, numbers);
        System.arraycopy(buffer, 0, cache.cachedValues, 0, cache.cachedValues.length);
        cache.cachedResult = rendered;
        cache.hasCachedResult = true;
        return rendered;
    }

    private static boolean matches(Object[] buffer, Object[] cachedValues) {
        for (int index = 0; index < cachedValues.length; index++) {
            if (!Objects.equals(buffer[index], cachedValues[index])) return false;
        }
        return true;
    }

    private static final class RenderCache {

        private final Object[] cachedValues;
        private Component cachedResult;
        private boolean hasCachedResult;

        private RenderCache(int placeholderCount) {
            this.cachedValues = new Object[placeholderCount];
        }
    }

    // Nested resolver renders need distinct buffers, released in LIFO order.
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

    private static final class RenderBuffer implements ValueResolver.Value {

        private static final int MAX_RETAINED_CAPACITY = 256;

        private final RenderScratch owner;
        private Object[] values = EMPTY_VALUES;
        private long[] numbers;
        private Object resolved;
        private long resolvedNumber;
        private int size;
        private ComponentValue componentValue;

        private RenderBuffer(RenderScratch owner) {
            this.owner = owner;
        }

        @Override
        public void text(String text) {
            this.resolved = text;
        }

        @Override
        public void number(long number) {
            this.resolved = Token.NUMBER_VALUE;
            this.resolvedNumber = number;
        }

        @Override
        public void component(Component component) {
            this.resolved = component;
        }

        private ComponentValue componentValue() {
            if (this.componentValue == null) this.componentValue = new ComponentValue(this);
            return this.componentValue;
        }

        private void prepare(int size) {
            if (this.values.length < size) {
                int capacity = 8;
                while (capacity < size) capacity <<= 1;
                this.values = new Object[capacity];
            }
            this.size = size;
        }

        private void release() {
            this.owner.release(this);
        }

        private void storeNumber(int index) {
            if (this.numbers == null || this.numbers.length < this.values.length) {
                this.numbers = new long[this.values.length];
            }
            this.numbers[index] = this.resolvedNumber;
        }

        private void clear() {
            this.resolved = null;
            if (this.values.length > MAX_RETAINED_CAPACITY) {
                this.values = EMPTY_VALUES;
                this.numbers = null;
            } else {
                Arrays.fill(this.values, 0, this.size, null);
            }
            this.size = 0;
        }
    }

    private static final class ComponentValue implements ValueResolver.Value {

        private final RenderBuffer buffer;

        private ComponentValue(RenderBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void text(String text) {
            this.buffer.resolved = text == null ? null : Component.text(text);
        }

        @Override
        public void number(long number) {
            this.buffer.resolved = Component.text(number);
        }

        @Override
        public void component(Component component) {
            this.buffer.resolved = component;
        }
    }

}
