package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * An independent render cache created by {@link Text#bind(Object)}.
 * Every render calls the shared handlers with the retained context. Equal complete
 * value tuples reuse the result; changed tuples rebuild the template while retaining
 * materialized values of unchanged keys. Null and missing reports mean literal fallback.
 *
 * <p>Use sequentially; this object is not thread-safe. Transfer between threads needs
 * a happens-before edge and handlers must support the execution context. Recursive
 * rendering of this binding is rejected; other bindings and ordinary renders may nest.
 * Handler or renderer failures clear the cache; the next render obtains all values again.
 * External handler effects are not rolled back.
 *
 * <p>The binding strongly retains its context, values and result. Release the binding
 * when finished. It does not register timers or use the text's ordinary result cache.
 *
 * @param <C> the retained context type
 * @since 1.0
 */
public final class BoundText<C> {

    private final TextImpl<C> text;
    private final C context;
    private final Object[] previous;
    private final Object[] values;
    private final PendingValue pending;
    private long[] numbers;
    private Component result;
    private boolean rendering;

    BoundText(TextImpl<C> text, C context) {
        this.text = text;
        this.context = context;
        final int size = text.bindingSize();
        this.previous = size == 0 ? null : new Object[size];
        this.values = size == 0 ? null : new Object[size];
        this.pending = size == 0 ? null : new PendingValue();
    }

    /**
     * Obtains current values and returns the rendered component on the calling thread.
     * Native text and numbers are compared before materialization; transitions between
     * value types use full component equality. Every key is checked even on a cache hit.
     *
     * @return the current result, reusing the previous instance for equal value tuples
     * @throws IllegalStateException    if this binding is already rendering
     * @throws IllegalArgumentException if Adventure rejects a rendered action value
     * @throws RuntimeException         if a handler fails; remaining handlers are not called
     * @since 1.0
     */
    public @NotNull Component render() {
        if (this.rendering) throw new IllegalStateException("Binding is already rendering");
        this.rendering = true;
        try {
            if (this.previous == null) {
                if (this.result == null) this.result = this.text.renderBound(null, null);
                return this.result;
            }

            final boolean initialized = this.result != null;
            boolean changed = !initialized;
            for (int index = 0; index < this.previous.length; index++) {
                this.pending.clear();
                this.text.resolveBound(index, this.context, this.pending);
                final Object current = this.pending.value;
                final Object previous = this.previous[index];
                if (!initialized || !this.sameValue(index, previous, current)) {
                    changed = true;
                    this.previous[index] = current;
                    this.values[index] = current == null ? this.text.boundFallback(index) : current;
                    if (current == Token.NUMBER_VALUE) this.storeNumber(index);
                } else if (kind(previous) != kind(current)) {
                    // Remember the new report type without discarding an equivalent materialization.
                    this.previous[index] = current;
                    if (current == Token.NUMBER_VALUE) this.storeNumber(index);
                }
            }
            if (changed) this.result = this.text.renderBound(this.values, this.numbers);
            return this.result;
        } catch (RuntimeException | Error failure) {
            if (this.previous != null) {
                Arrays.fill(this.previous, null);
                Arrays.fill(this.values, null);
            }
            this.result = null;
            throw failure;
        } finally {
            if (this.pending != null) this.pending.clear();
            this.rendering = false;
        }
    }

    private boolean sameValue(int index, Object previous, Object current) {
        if (previous == Token.NUMBER_VALUE && current == Token.NUMBER_VALUE) {
            return this.numbers[index] == this.pending.number;
        }
        if (Objects.equals(previous, current)) return true;
        if (kind(previous) == kind(current)) return false;
        final Component old = this.values[index] instanceof Component component
                ? component : this.component(previous, this.numbers == null ? 0 : this.numbers[index], index);
        return old.equals(this.component(current, this.pending.number, index));
    }

    private Component component(Object value, long number, int index) {
        if (value == null) return this.text.boundFallback(index);
        if (value == Token.NUMBER_VALUE) return Component.text(number);
        if (value instanceof String string) return Component.text(string);
        return (Component) value;
    }

    private void storeNumber(int index) {
        if (this.numbers == null) this.numbers = new long[this.previous.length];
        this.numbers[index] = this.pending.number;
    }

    private static int kind(Object value) {
        if (value == null) return 0;
        if (value == Token.NUMBER_VALUE) return 1;
        return value instanceof String ? 2 : 3;
    }

    private static final class PendingValue implements ValueResolver.Value {

        private Object value;
        private long number;

        @Override
        public void text(String text) {
            this.value = text;
        }

        @Override
        public void number(long number) {
            this.value = Token.NUMBER_VALUE;
            this.number = number;
        }

        @Override
        public void component(Component component) {
            this.value = component;
        }

        private void clear() {
            this.value = null;
        }
    }
}
