package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class TextImpl<C> implements Text<C> {

    private static final Object[] EMPTY_VALUES = new Object[0];
    // A thread must not strongly hold an object of this library: that would keep the library's
    // class loader alive for as long as the thread runs. See Token.CHILD_SCRATCH.
    private static final ThreadLocal<WeakReference<RenderScratch>> RENDER_SCRATCH = new ThreadLocal<>();

    private final Token token;
    private final String[] placeholderKeys;
    private final ValueResolver<C>[] resolvers;
    private volatile Component[] fallbacks;
    // Per thread: the last value tuple as reported, the result it produced, null until one is
    // cached, and the numbers of the tuple. A plain array for the same reason as above.
    private final ThreadLocal<Object[]> renderCache;
    private final boolean cacheable;

    TextImpl(Token token, String[] placeholderKeys, boolean cacheable, ResolverFactory<C> factory) {
        this.token = Objects.requireNonNull(token, "token");
        this.placeholderKeys = Objects.requireNonNull(placeholderKeys, "placeholderKeys");
        this.cacheable = cacheable;
        this.resolvers = bind(this.placeholderKeys, factory);

        final int placeholderCount = this.placeholderKeys.length;
        this.renderCache = placeholderCount == 0 || !cacheable
                ? null
                : ThreadLocal.withInitial(() -> new Object[placeholderCount + 2]);
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

        final RenderBuffer renderBuffer = renderScratch().acquire(this.placeholderKeys.length);
        final Object[] buffer = renderBuffer.values;
        try {
            for (int index = 0; index < this.placeholderKeys.length; index++) {
                renderBuffer.resolved = null;
                final ValueResolver<C> resolver = this.resolvers[index];
                if (resolver != null) resolver.resolve(context, renderBuffer);
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

    @Override
    public @NotNull List<String> keys() {
        return List.of(this.placeholderKeys);
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

        final Object[] cache = this.renderCache.get();
        final int valueCount = this.placeholderKeys.length;
        final Object cachedResult = cache[valueCount];
        if (cachedResult != null && matches(buffer, numbers, cache, (long[]) cache[valueCount + 1], valueCount)) {
            return (Component) cachedResult;
        }

        // The values as reported: rendering may turn them into text or components in place.
        cache[valueCount] = null;
        System.arraycopy(buffer, 0, cache, 0, valueCount);
        if (numbers != null) {
            long[] cachedNumbers = (long[]) cache[valueCount + 1];
            if (cachedNumbers == null) cache[valueCount + 1] = cachedNumbers = new long[valueCount];
            System.arraycopy(numbers, 0, cachedNumbers, 0, valueCount);
        }
        final Component rendered = this.token.render(buffer, numbers);
        cache[valueCount] = rendered;
        return rendered;
    }

    // Values of one type compare as they are; values of different types compare as the
    // components they render as, so a text equals the same text reported as a component.
    private static boolean matches(Object[] buffer, long[] numbers, Object[] cache, long[] cachedNumbers,
                                   int valueCount) {
        for (int index = 0; index < valueCount; index++) {
            final Object value = buffer[index];
            final Object cached = cache[index];
            if (value == Token.NUMBER_VALUE) {
                if (cached != Token.NUMBER_VALUE) {
                    if (!Component.text(numbers[index]).equals(asComponent(cached, cachedNumbers, index))) return false;
                } else if (numbers[index] != cachedNumbers[index]) {
                    return false;
                }
            } else if (value.getClass() == cached.getClass() || value instanceof Component && cached instanceof Component) {
                if (!value.equals(cached)) return false;
            } else if (!asComponent(value, numbers, index).equals(asComponent(cached, cachedNumbers, index))) {
                return false;
            }
        }
        return true;
    }

    private static Component asComponent(Object value, long[] numbers, int index) {
        if (value == Token.NUMBER_VALUE) return Component.text(numbers[index]);
        if (value instanceof String text) return Component.text(text);
        return (Component) value;
    }

    // Buffers left unused between renders may be collected and are then created again.
    private static RenderScratch renderScratch() {
        final WeakReference<RenderScratch> reference = RENDER_SCRATCH.get();
        RenderScratch scratch = reference == null ? null : reference.get();
        if (scratch == null) {
            scratch = new RenderScratch();
            RENDER_SCRATCH.set(new WeakReference<>(scratch));
        }
        return scratch;
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

        @Override
        public void component(String source, Function<? super String, ? extends Component> parser) {
            this.resolved = parse(source, parser);
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

    // A render without a binding keeps nothing between calls, so it parses every time.
    private static Component parse(String source, Function<? super String, ? extends Component> parser) {
        Objects.requireNonNull(parser, "parser");
        return source == null ? null : parser.apply(source);
    }
}
