package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * An independent render cache created by {@link Text#bind(Object)}.
 * Every render calls the shared handlers with the retained context. Equal complete
 * value tuples reuse the result; changed tuples rebuild the template while retaining
 * materialized values of unchanged keys. Null and missing reports mean literal fallback.
 * A string reported with a parser is parsed again only when the string or the parser
 * is a different one.
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

    // What a handler reported for a key.
    private static final int ABSENT = 0;
    private static final int NUMBER = 1;
    private static final int TEXT = 2;
    private static final int COMPONENT = 3;
    private static final int PARSED = 4;

    private final TextImpl<C> text;
    private final C context;
    // The last report of each key: null, the number marker, a text, the source string of a
    // parsed value, or a component.
    private final Object[] previous;
    // What the template inserts for each key. For a parsed value this is always its component.
    private final Object[] values;
    private final PendingValue pending;
    private long[] numbers;
    // The parser of each key whose last report was a string to parse; created with the first one.
    private Function<? super String, ? extends Component>[] parsers;
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
     * value types use full component equality. A string reported with a parser is compared
     * as a string, and its parser runs only for a different string or parser.
     * Every key is checked even on a cache hit.
     *
     * @return the current result, reusing the previous instance for equal value tuples
     * @throws IllegalStateException if this binding is already rendering
     * @throws RuntimeException      if a handler or parser fails; remaining handlers are not called
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
                final int kind = this.pending.kind();
                if (!initialized || !this.sameValue(index, kind)) {
                    changed = true;
                    this.store(index, kind, true);
                } else if (this.kind(index) != kind) {
                    // Remember the new report type without discarding an equivalent materialization.
                    this.store(index, kind, false);
                }
            }
            if (changed) this.result = this.text.renderBound(this.values, this.numbers);
            return this.result;
        } catch (RuntimeException | Error failure) {
            if (this.previous != null) {
                Arrays.fill(this.previous, null);
                Arrays.fill(this.values, null);
                if (this.parsers != null) Arrays.fill(this.parsers, null);
            }
            this.result = null;
            throw failure;
        } finally {
            if (this.pending != null) this.pending.clear();
            this.rendering = false;
        }
    }

    private boolean sameValue(int index, int kind) {
        final Object previous = this.previous[index];
        if (this.kind(index) == kind) {
            return switch (kind) {
                case NUMBER -> this.numbers[index] == this.pending.number;
                // The same string through another parser can be another component.
                case PARSED -> this.parsers[index] == this.pending.parser && previous.equals(this.pending.value);
                default -> Objects.equals(previous, this.pending.value);
            };
        }
        final Component old = this.values[index] instanceof Component component
                ? component : this.previousComponent(index);
        return old.equals(this.pendingComponent(index, kind));
    }

    // What the last report stands for when its slot holds no component: an equal report of
    // another type can leave a text or a number there. Never reached for a parsed value.
    private Component previousComponent(int index) {
        final Object value = this.previous[index];
        if (value == null) return this.text.boundFallback(index);
        if (value == Token.NUMBER_VALUE) return Component.text(this.numbers[index]);
        if (value instanceof String string) return Component.text(string);
        return (Component) value;
    }

    // What the pending report inserts. A parsed component is kept for store().
    private Component pendingComponent(int index, int kind) {
        return switch (kind) {
            case ABSENT -> this.text.boundFallback(index);
            case NUMBER -> Component.text(this.pending.number);
            case TEXT -> Component.text((String) this.pending.value);
            case PARSED -> this.pending.parsed = this.parse(index);
            default -> (Component) this.pending.value;
        };
    }

    private Component parse(int index) {
        final Component parsed = this.pending.parser.apply((String) this.pending.value);
        return parsed == null ? this.text.boundFallback(index) : parsed;
    }

    // Records the pending report. An equal value of another type keeps what is already inserted.
    private void store(int index, int kind, boolean insert) {
        final Object current = this.pending.value;
        this.previous[index] = current;
        if (kind == PARSED) {
            if (this.parsers == null) this.parsers = newParsers(this.previous.length);
            this.parsers[index] = this.pending.parser;
            this.values[index] = this.pending.parsed != null ? this.pending.parsed : this.parse(index);
            return;
        }
        if (this.parsers != null) this.parsers[index] = null;
        if (kind == NUMBER) this.storeNumber(index);
        if (insert) this.values[index] = current == null ? this.text.boundFallback(index) : current;
    }

    private void storeNumber(int index) {
        if (this.numbers == null) this.numbers = new long[this.previous.length];
        this.numbers[index] = this.pending.number;
    }

    // The type of the last report stored for a key.
    private int kind(int index) {
        final Object value = this.previous[index];
        if (value == null) return ABSENT;
        if (value == Token.NUMBER_VALUE) return NUMBER;
        if (!(value instanceof String)) return COMPONENT;
        return this.parsers != null && this.parsers[index] != null ? PARSED : TEXT;
    }

    @SuppressWarnings("unchecked")
    private static Function<? super String, ? extends Component>[] newParsers(int size) {
        return (Function<? super String, ? extends Component>[]) new Function<?, ?>[size];
    }

    private static final class PendingValue implements ValueResolver.Value {

        private Object value;
        private long number;
        private Function<? super String, ? extends Component> parser;
        // The parser's result, once a comparison needed it.
        private Component parsed;

        @Override
        public void text(String text) {
            this.value = text;
            this.parser = null;
        }

        @Override
        public void number(long number) {
            this.value = Token.NUMBER_VALUE;
            this.number = number;
            this.parser = null;
        }

        @Override
        public void component(Component component) {
            this.value = component;
            this.parser = null;
        }

        @Override
        public void component(String source, Function<? super String, ? extends Component> parser) {
            Objects.requireNonNull(parser, "parser");
            this.value = source;
            this.parser = source == null ? null : parser;
        }

        private int kind() {
            if (this.value == null) return ABSENT;
            if (this.value == Token.NUMBER_VALUE) return NUMBER;
            if (!(this.value instanceof String)) return COMPONENT;
            return this.parser != null ? PARSED : TEXT;
        }

        private void clear() {
            this.value = null;
            this.parser = null;
            this.parsed = null;
        }
    }
}
