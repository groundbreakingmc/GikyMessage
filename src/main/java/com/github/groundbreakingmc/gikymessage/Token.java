package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.object.ObjectContents;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

// Compiled tokens render from indexed values; static results are built during compilation.
interface Token {

    // A number occupies the same index in the primitive array; other values are String or Component.
    Object NUMBER_VALUE = new Object();
    Object USED_NUMBER_VALUE = new Object();

    Component render(Object[] placeholders, long[] numbers);

    // Materialize once per slot when a component is needed by a non-text branch or the result cache.
    static Component component(Object[] values, long[] numbers, int index) {
        final Object value = values[index];
        if (value instanceof Component component) return component;
        final Component component = isNumber(value)
                ? Component.text(numbers[index]) : Component.text((String) value);
        values[index] = component;
        return component;
    }

    private static String plainText(Object[] values, long[] numbers, int index) {
        final Object value = values[index];
        if (value instanceof String text) return text;
        if (isNumber(value)) return numberText(values, numbers, index);
        return ((TextComponent) value).content();
    }

    private static boolean isNumber(Object value) {
        return value == NUMBER_VALUE || value == USED_NUMBER_VALUE;
    }

    private static String numberText(Object[] values, long[] numbers, int index) {
        final String text = Long.toString(numbers[index]);
        values[index] = text;
        return text;
    }

    private static void appendValue(StringBuilder builder, Object[] values, long[] numbers, int index) {
        final Object value = values[index];
        if (value instanceof String text) builder.append(text);
        else if (value == NUMBER_VALUE) {
            builder.append(numbers[index]);
            values[index] = USED_NUMBER_VALUE;
        }
        // Repeated numeric uses share decimal text; one use needs no temporary String.
        else if (value == USED_NUMBER_VALUE) builder.append(numberText(values, numbers, index));
        else appendText(builder, (Component) value);
    }

    final class Plain implements Token {

        final String text;
        final Style style;
        private final Component component;

        Plain(String text, Style style) {
            this.text = text;
            this.style = style;
            this.component = textComponent(text, style);
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            return this.component;
        }
    }

    // Retains only the rendered result of static content.
    final class Constant implements Token {

        private final Component staticComponent;

        Constant(Component component) {
            this.staticComponent = component;
        }

        Constant(
                String text,
                Style style,
                Token[] children,
                int childCount
        ) {
            this.staticComponent = this.renderChildren(text, style, children, childCount);
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            return this.staticComponent;
        }

        private Component renderChildren(String text, Style style, Token[] children, int childCount) {
            final ChildBuffer rendered = acquireChildren(childCount);
            try {
                for (int index = 0; index < childCount; index++) {
                    rendered.setComponent(index, children[index].render(null, null));
                }
                return textComponent(text, style, rendered);
            } finally {
                releaseChildren(rendered);
            }
        }
    }

    final class DynChildren implements Token {

        // The root without children; each render only attaches them.
        private final TextComponent base;
        private final Token[] children;
        private final int childCount;

        DynChildren(
                String text,
                Style style,
                Token[] children,
                int childCount
        ) {
            this.base = Component.text(text, style);
            this.children = children;
            this.childCount = childCount;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            final ChildBuffer rendered = acquireChildren(this.childCount);
            try {
                for (int index = 0; index < this.childCount; index++) {
                    rendered.setComponent(index, this.children[index].render(placeholders, numbers));
                }
                return this.base.children(rendered);
            } finally {
                releaseChildren(rendered);
            }
        }

        private DynChildren withStyle(Style style) {
            return new DynChildren(this.base.content(), style, this.children, this.childCount);
        }
    }

    // staticParts may omit trailing empty entries: length <= phIndices.length + 1.

    // Keeps replacements and static tails as siblings, preventing replacement styles from leaking into tails.
    final class PlainDyn implements Token {

        final String[] staticParts;
        final int[] phIndices;
        private final TextComponent base;
        private final Component[] staticTails;
        private final int childCount;

        private PlainDyn withStyle(Style style) {
            return new PlainDyn(this.staticParts, this.phIndices, style);
        }

        PlainDyn(String[] staticParts, int[] phIndices, Style style) {
            this.staticParts = staticParts;
            this.phIndices = phIndices;

            final String rootText = staticParts.length > 0 && staticParts[0] != null
                    ? staticParts[0] : "";
            this.base = Component.text(rootText, style);

            Component[] tails = null;
            int count = phIndices.length;
            for (int index = 0; index < phIndices.length; index++) {
                final int staticIndex = index + 1;
                if (staticIndex >= staticParts.length) continue;

                final String staticText = staticParts[staticIndex];
                if (staticText == null || staticText.isEmpty()) continue;

                if (tails == null) tails = new Component[phIndices.length];
                tails[index] = Component.text(staticText);
                count++;
            }
            this.staticTails = tails;
            this.childCount = count;
        }

        @Override
        public Component render(Object[] compPh, long[] numbers) {
            final Component flattened = this.flatten(compPh, numbers);
            if (flattened != null) return flattened;

            final ChildBuffer children = acquireChildren(this.childCount);
            try {
                int outputIndex = 0;
                for (int index = 0; index < this.phIndices.length; index++) {
                    children.setComponent(outputIndex++, component(compPh, numbers, this.phIndices[index]));

                    if (this.staticTails != null) {
                        final Component tail = this.staticTails[index];
                        if (tail != null) children.setComponent(outputIndex++, tail);
                    }
                }
                return this.base.children(children);
            } finally {
                releaseChildren(children);
            }
        }

        private Component flatten(Object[] placeholders, long[] numbers) {
            for (final int placeholderIndex : this.phIndices) {
                final Object value = placeholders[placeholderIndex];
                if (value instanceof String || isNumber(value)) continue;
                final Component component = (Component) value;
                if (!(component instanceof TextComponent textComponent)
                        || !textComponent.style().isEmpty()
                        || !textComponent.children().isEmpty()) {
                    return null;
                }
            }

            if (this.phIndices.length == 1 && this.staticParts.length == 0 && this.base.style().isEmpty()) {
                return component(placeholders, numbers, this.phIndices[0]);
            }

            if (this.phIndices.length == 1) {
                final String replacement = plainText(placeholders, numbers, this.phIndices[0]);
                final String tail = this.staticParts.length > 1 && this.staticParts[1] != null
                        ? this.staticParts[1] : "";
                return Component.text(this.base.content() + replacement + tail, this.base.style());
            }

            final StringBuilder builder = TL_SB.get();
            builder.setLength(0);
            try {
                if (this.staticParts.length > 0 && this.staticParts[0] != null) {
                    builder.append(this.staticParts[0]);
                }

                for (int index = 0; index < this.phIndices.length; index++) {
                    appendValue(builder, placeholders, numbers, this.phIndices[index]);

                    final int staticIndex = index + 1;
                    if (staticIndex < this.staticParts.length && this.staticParts[staticIndex] != null) {
                        builder.append(this.staticParts[staticIndex]);
                    }
                }

                return Component.text(builder.toString(), this.base.style());
            } finally {
                releaseBuilder(builder);
            }
        }
    }

    // Replaces the click, the hover or both on the content root with ones built from
    // placeholders, preserving its other styles and metadata.
    final class EventContent implements Token {

        static final byte NONE = -1;
        static final byte RUN = 0;
        static final byte SUGGEST = 1;
        static final byte URL = 2;
        static final byte COPY = 3;

        private final Token content;
        private final byte clickType;
        private final String[] clickParts;
        private final int[] clickPlaceholders;
        // The tooltip, or null to keep the content's own.
        private final Token hover;

        EventContent(Token content, byte clickType, String[] clickParts, int[] clickPlaceholders, Token hover) {
            this.content = content;
            this.clickType = clickType;
            this.clickParts = clickParts;
            this.clickPlaceholders = clickPlaceholders;
            this.hover = hover;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            final Component component = this.content.render(placeholders, numbers);
            final Style baseStyle = component.style();

            ClickEvent<?> click = baseStyle.clickEvent();
            HoverEvent<?> hover = baseStyle.hoverEvent();
            if (this.clickType != NONE) {
                final String value = buildText(this.clickParts, this.clickPlaceholders, placeholders, numbers);
                click = dynamicClick(this.clickType, value, click);
            }
            if (this.hover != null) hover = HoverEvent.showText(this.hover.render(placeholders, numbers));

            final Style style = withEvents(baseStyle, click, hover);
            return style == baseStyle ? component : component.style(style);
        }

        static ClickEvent<?> buildClick(byte type, String value) {
            return switch (type) {
                case RUN -> ClickEvent.runCommand(value);
                case SUGGEST -> ClickEvent.suggestCommand(value);
                case URL -> ClickEvent.openUrl(value);
                case COPY -> ClickEvent.copyToClipboard(value);
                default -> throw new IllegalStateException("Not a click action: " + type);
            };
        }

        // Newer Adventure rejects some values, such as a URL with spaces. A placeholder value
        // must not fail the render, so a rejected one leaves the content's click as it was.
        private static ClickEvent<?> dynamicClick(byte type, String value, ClickEvent<?> current) {
            try {
                return buildClick(type, value);
            } catch (IllegalArgumentException rejected) {
                return current;
            }
        }
    }

    // The look of a color, shadow or style action applied to its content: with force throughout
    // the content, otherwise only where the content's text leaves it unset. A value with
    // placeholders is read on each render; the looks of its last values are kept.
    final class LookContent implements Token {

        private static final int CACHE_SIZE = 8;

        private final Token content;
        // The fixed value, or null for one with placeholders.
        private final Look look;
        private final byte kind;
        private final String[] staticParts;
        private final int[] placeholderIndices;
        private final float phase;
        private final boolean force;
        // The looks of the last values, one per slot of their hash; null for a fixed value.
        private final AtomicReferenceArray<Look> looks;

        static Token create(Token content, Look look, boolean force, boolean dynamic) {
            if (!dynamic) return new Constant(applyLook(content.render(null, null), look, force));
            // A plain style over content whose root style is fixed is applied to that root once.
            if (!force && look.colors == null && look.shadows == null) {
                if (content instanceof PlainDyn plain) return plain.withStyle(fill(plain.base.style(), look.style));
                if (content instanceof DynChildren children) {
                    return children.withStyle(fill(children.base.style(), look.style));
                }
            }
            return new LookContent(content, look, (byte) -1, NO_PARTS, NO_PH, 0f, force);
        }

        static Token create(Token content, byte kind, String[] staticParts, int[] placeholderIndices,
                            float phase, boolean force) {
            return new LookContent(content, null, kind, staticParts, placeholderIndices, phase, force);
        }

        private LookContent(Token content, Look look, byte kind, String[] staticParts, int[] placeholderIndices,
                            float phase, boolean force) {
            this.content = content;
            this.look = look;
            this.kind = kind;
            this.staticParts = staticParts;
            this.placeholderIndices = placeholderIndices;
            this.phase = phase;
            this.force = force;
            this.looks = look == null ? new AtomicReferenceArray<>(CACHE_SIZE) : null;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            final Component rendered = this.content.render(placeholders, numbers);
            final Look look = this.look != null ? this.look
                    : this.resolve(buildText(this.staticParts, this.placeholderIndices, placeholders, numbers));
            return applyLook(rendered, look, this.force);
        }

        // An invalid value is kept as an empty look, so it is not read again either.
        private Look resolve(String value) {
            final int slot = value.hashCode() & (CACHE_SIZE - 1);
            final Look cached = this.looks.get(slot);
            if (cached != null && cached.source.equals(value)) return cached;
            final Look parsed = Look.parse(this.kind, value, this.phase);
            final Look look = parsed != null ? parsed : new Look(value, Style.empty(), null, null);
            this.looks.set(slot, look);
            return look;
        }
    }

    // The style first, so that a reset in it removes shadows before a shadow gradient is spread.
    private static Component applyLook(Component component, Look look, boolean force) {
        Component result = component;
        if (!look.style.isEmpty()) result = applyStyle(result, look.style, force);
        if (look.colors != null) result = applyGradient(result, look.colors, force);
        if (look.shadows != null) result = applyGradient(result, look.shadows, force);
        return result;
    }

    // What a plain style gives a root with this style: it fills only what the root leaves unset.
    private static Style fill(Style root, Style style) {
        final Style applied = StyleUtils.resetsShadow(style) ? StyleUtils.withoutShadow(style) : style;
        return root.merge(applied, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
    }

    // Without force the style fills what the component's root leaves unset; its children
    // inherit it. With force it is set on every component.
    private static Component applyStyle(Component component, Style style, boolean force) {
        if (!force) return component.style(fill(component.style(), style));
        final boolean resetsShadow = StyleUtils.resetsShadow(style);
        final Style applied = resetsShadow ? StyleUtils.withoutShadow(style) : style;
        // reset in a forced style removes every shadow of the segment.
        return forceStyle(component, applied, resetsShadow);
    }

    private static Component forceStyle(Component component, Style style, boolean removeShadows) {
        final List<Component> children = component.children();
        final Style own = removeShadows ? StyleUtils.withoutShadow(component.style()) : component.style();
        final Component styled = component.style(own.merge(style));
        if (children.isEmpty()) return styled;
        final ChildBuffer buffer = acquireChildren(children.size());
        try {
            for (int index = 0; index < children.size(); index++) {
                buffer.setComponent(index, forceStyle(children.get(index), style, removeShadows));
            }
            return styled.children(buffer);
        } finally {
            releaseChildren(buffer);
        }
    }

    // Replaces content with a translation that each client looks up in its own language. The
    // content's text, placeholders resolved, is the fallback for clients without the key, and
    // the translation takes the content root's style. Nested styles of the content are not kept.
    // Only segments with a lang action create this token; other templates pay nothing for it.
    final class Translated implements Token {

        private final Token content;
        // The key when it has no placeholders; otherwise it is built from the parts per render.
        private final String key;
        private final String[] keyParts;
        private final int[] keyPlaceholders;

        static Token create(Token content, String key, boolean dynamicContent) {
            final Translated token = new Translated(content, key, NO_PARTS, NO_PH);
            return dynamicContent ? token : new Constant(token.render(null, null));
        }

        static Token create(Token content, String[] keyParts, int[] keyPlaceholders) {
            return new Translated(content, null, keyParts, keyPlaceholders);
        }

        private Translated(Token content, String key, String[] keyParts, int[] keyPlaceholders) {
            this.content = content;
            this.key = key;
            this.keyParts = keyParts;
            this.keyPlaceholders = keyPlaceholders;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            final Component rendered = this.content.render(placeholders, numbers);
            final String key = this.key != null ? this.key
                    : buildText(this.keyParts, this.keyPlaceholders, placeholders, numbers);
            // Like an invalid color, an empty key leaves the content as it is.
            if (key.isEmpty()) return rendered;
            final String fallback = contentText(rendered);
            final Component translated = fallback.isEmpty()
                    ? Component.translatable(key)
                    : Component.translatable(key, fallback);
            return rendered.style().isEmpty() ? translated : translated.style(rendered.style());
        }
    }

    private static String contentText(Component component) {
        if (component instanceof TextComponent text && text.children().isEmpty()) return text.content();
        final StringBuilder builder = TL_SB.get();
        builder.setLength(0);
        try {
            appendText(builder, component);
            return builder.toString();
        } finally {
            releaseBuilder(builder);
        }
    }

    // Shows its segment only while its condition holds. A hidden segment is not rendered.
    final class Conditional implements Token {

        private final Token segment;
        private final Condition condition;

        Conditional(Token segment, Condition condition) {
            this.segment = segment;
            this.condition = condition;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            return this.condition.holds(placeholders, numbers)
                    ? this.segment.render(placeholders, numbers) : Component.empty();
        }
    }

    // Shows the first branch whose condition holds, otherwise the fallback, or nothing without
    // one. Each condition is checked at most once and only the chosen branch is rendered.
    final class Choice implements Token {

        private final Condition[] conditions;
        private final Token[] branches;
        private final Token fallback;

        Choice(Condition[] conditions, Token[] branches, Token fallback) {
            this.conditions = conditions;
            this.branches = branches;
            this.fallback = fallback;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            for (int index = 0; index < this.conditions.length; index++) {
                if (this.conditions[index].holds(placeholders, numbers)) {
                    return this.branches[index].render(placeholders, numbers);
                }
            }
            return this.fallback != null ? this.fallback.render(placeholders, numbers) : Component.empty();
        }
    }

    // A condition of if or unless. Values are inspected where they are, so a presence check
    // builds nothing; a comparison builds the text of both sides.
    sealed interface Condition {

        boolean holds(Object[] placeholders, long[] numbers);

        // Whether the outcome is known while compiling, because no placeholder takes part.
        boolean isStatic();

        // Both conditions must hold; either may be null for none.
        static Condition both(Condition first, Condition second) {
            if (first == null) return second;
            if (second == null) return first;
            return new Both(first, second);
        }

        // At least one of the conditions must hold.
        static Condition either(Condition first, Condition second) {
            return new Either(first, second);
        }
    }

    // Holds while the value has text, or while it has none when negated. Blank text counts as
    // none, and so does a component that holds only formatting.
    record Presence(String[] staticParts, int[] placeholderIndices, boolean negated) implements Condition {

        @Override
        public boolean holds(Object[] placeholders, long[] numbers) {
            boolean present = hasText(this.staticParts);
            for (int index = 0; !present && index < this.placeholderIndices.length; index++) {
                present = valueHasText(placeholders[this.placeholderIndices[index]]);
            }
            return present != this.negated;
        }

        @Override
        public boolean isStatic() {
            return this.placeholderIndices.length == 0;
        }

        static boolean hasText(String[] parts) {
            for (final String part : parts) {
                if (part != null && !part.isBlank()) return true;
            }
            return false;
        }

        // A number always has digits.
        private static boolean valueHasText(Object value) {
            if (value instanceof String text) return !text.isBlank();
            if (isNumber(value)) return true;
            return componentHasText((Component) value);
        }

        // Components other than text, such as translations, show something of their own.
        private static boolean componentHasText(Component component) {
            if (!(component instanceof TextComponent text) || !text.content().isBlank()) return true;
            final List<Component> children = component.children();
            for (int index = 0, size = children.size(); index < size; index++) {
                if (componentHasText(children.get(index))) return true;
            }
            return false;
        }
    }

    // Compares the text of both sides, negated for unless. Spaces around a side do not count and
    // components compare by their plain text. When both sides are numbers, such as 7, -12 or
    // 3.50, they compare as numbers; <, >, <= and >= hold only then.
    final class Comparison implements Condition {

        // Operator codes are indices here; at one position the two-character ones are tried first.
        static final String[] OPERATORS = {"==", "!=", "<=", ">=", "<", ">"};
        private static final int NOT_NUMBERS = Integer.MIN_VALUE;

        // A side that is a whole number without its text: fixed digits, or the slot of a sole
        // placeholder, which holds a number only on some renders. NOT_WHOLE for any other side.
        private static final int FIXED_WHOLE = -2;
        private static final int NOT_WHOLE = -1;

        private final String[] leftParts;
        private final int[] leftPlaceholders;
        private final String[] rightParts;
        private final int[] rightPlaceholders;
        private final byte operator;
        private final boolean negated;
        private final int leftWhole;
        private final int rightWhole;
        private final long leftValue;
        private final long rightValue;

        Comparison(String[] leftParts, int[] leftPlaceholders, String[] rightParts, int[] rightPlaceholders,
                   byte operator, boolean negated) {
            this.leftParts = leftParts;
            this.leftPlaceholders = leftPlaceholders;
            this.rightParts = rightParts;
            this.rightPlaceholders = rightPlaceholders;
            this.operator = operator;
            this.negated = negated;
            this.leftWhole = wholeSlot(leftParts, leftPlaceholders);
            this.rightWhole = wholeSlot(rightParts, rightPlaceholders);
            this.leftValue = this.leftWhole == FIXED_WHOLE ? Long.parseLong(fixedText(leftParts)) : 0;
            this.rightValue = this.rightWhole == FIXED_WHOLE ? Long.parseLong(fixedText(rightParts)) : 0;
        }

        @Override
        public boolean holds(Object[] placeholders, long[] numbers) {
            final int order;
            String left = null;
            String right = null;
            // Two whole numbers, such as a number placeholder and fixed digits, need no text.
            if (isWhole(this.leftWhole, placeholders) && isWhole(this.rightWhole, placeholders)) {
                order = Long.compare(whole(this.leftWhole, this.leftValue, numbers),
                        whole(this.rightWhole, this.rightValue, numbers));
            } else {
                // The left text is copied out before the right one reuses the shared builder.
                left = buildText(this.leftParts, this.leftPlaceholders, placeholders, numbers).strip();
                right = buildText(this.rightParts, this.rightPlaceholders, placeholders, numbers).strip();
                order = compareNumbers(left, right);
            }
            final boolean result = switch (this.operator) {
                case 0 -> order == NOT_NUMBERS ? left.equals(right) : order == 0;
                case 1 -> order == NOT_NUMBERS ? !left.equals(right) : order != 0;
                case 2 -> order != NOT_NUMBERS && order <= 0;
                case 3 -> order != NOT_NUMBERS && order >= 0;
                case 4 -> order != NOT_NUMBERS && order < 0;
                default -> order != NOT_NUMBERS && order > 0;
            };
            return result != this.negated;
        }

        private static int wholeSlot(String[] parts, int[] placeholders) {
            if (placeholders.length == 0) {
                final String text = fixedText(parts);
                return isDecimal(text) && isWholeText(text) ? FIXED_WHOLE : NOT_WHOLE;
            }
            return parts.length == 0 && placeholders.length == 1 ? placeholders[0] : NOT_WHOLE;
        }

        // The compiler strips the text of a side, so fixed text is used as it is.
        private static String fixedText(String[] parts) {
            return parts.length == 0 || parts[0] == null ? "" : parts[0];
        }

        private static boolean isWhole(int slot, Object[] placeholders) {
            return slot == FIXED_WHOLE || slot >= 0 && isNumber(placeholders[slot]);
        }

        private static long whole(int slot, long fixed, long[] numbers) {
            return slot == FIXED_WHOLE ? fixed : numbers[slot];
        }

        // The sign of left minus right when both are decimal numbers, else NOT_NUMBERS.
        static int compareNumbers(String left, String right) {
            if (!isDecimal(left) || !isDecimal(right)) return NOT_NUMBERS;
            if (isWholeText(left) && isWholeText(right)) {
                return Long.compare(Long.parseLong(left), Long.parseLong(right));
            }
            return Integer.signum(new BigDecimal(left).compareTo(new BigDecimal(right)));
        }

        // A decimal number that a long holds as it is.
        private static boolean isWholeText(String decimal) {
            return decimal.length() <= 18 && decimal.indexOf('.') < 0;
        }

        // An optional sign, digits, and optionally a point with more digits.
        private static boolean isDecimal(String text) {
            int index = !text.isEmpty() && (text.charAt(0) == '-' || text.charAt(0) == '+') ? 1 : 0;
            final int digits = index;
            while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
            if (index == digits) return false;
            if (index < text.length() && text.charAt(index) == '.') {
                final int fraction = ++index;
                while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
                if (index == fraction) return false;
            }
            return index == text.length();
        }

        @Override
        public boolean isStatic() {
            return this.leftPlaceholders.length == 0 && this.rightPlaceholders.length == 0;
        }
    }

    record Both(Condition first, Condition second) implements Condition {

        @Override
        public boolean holds(Object[] placeholders, long[] numbers) {
            return this.first.holds(placeholders, numbers) && this.second.holds(placeholders, numbers);
        }

        @Override
        public boolean isStatic() {
            return this.first.isStatic() && this.second.isStatic();
        }
    }

    record Either(Condition first, Condition second) implements Condition {

        @Override
        public boolean holds(Object[] placeholders, long[] numbers) {
            return this.first.holds(placeholders, numbers) || this.second.holds(placeholders, numbers);
        }

        @Override
        public boolean isStatic() {
            return this.first.isStatic() && this.second.isStatic();
        }
    }

    // Rewrites the text of every text node of its content, step by step: case, small capitals,
    // compact numbers. Then it keeps at most a limit of characters over the whole content.
    // Nodes whose text does not change are kept as they are.
    final class TransformContent implements Token {

        static final byte UPPER = 0;
        static final byte LOWER = 1;
        static final byte SMALL_CAPS = 2;
        static final byte COMPACT = 3;

        // Small capitals for a to z, used for both cases; x has none and stays x.
        private static final String SMALL_CAPITALS = "ᴀʙᴄᴅᴇꜰɢʜɪ"
                + "ᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        private static final String[] UNITS = {"K", "M", "B", "T"};
        private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

        private final Token content;
        private final byte[] steps;
        // Characters to keep, or -1 for all.
        private final int limit;

        static Token create(Token content, byte[] steps, int limit, boolean dynamic) {
            final TransformContent token = new TransformContent(content, steps, limit);
            return dynamic ? token : new Constant(token.render(null, null));
        }

        private TransformContent(Token content, byte[] steps, int limit) {
            this.content = content;
            this.steps = steps;
            this.limit = limit;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            final Component rendered = this.content.render(placeholders, numbers);
            final ChildScratch scratch = childScratch();
            // Nested transforms have finished rendering by now, so the budget is this token's alone.
            scratch.transformBudget = this.limit;
            return this.apply(rendered, scratch);
        }

        private Component apply(Component component, ChildScratch scratch) {
            Component result = component;
            if (component instanceof TextComponent text) {
                final String original = text.content();
                String changed = original;
                for (final byte step : this.steps) changed = transform(step, changed);
                if (this.limit >= 0) changed = keep(changed, scratch);
                if (!changed.equals(original)) result = text.content(changed);
            }
            final List<Component> children = component.children();
            if (children.isEmpty()) return result;
            final ChildBuffer buffer = scratch.acquire(children.size());
            try {
                boolean childChanged = false;
                for (int index = 0; index < children.size(); index++) {
                    final Component child = children.get(index);
                    final Component next = this.apply(child, scratch);
                    if (next != child) childChanged = true;
                    buffer.setComponent(index, next);
                }
                return childChanged ? result.children(buffer) : result;
            } finally {
                releaseChildren(buffer);
            }
        }

        private static String transform(byte step, String text) {
            return switch (step) {
                case UPPER -> text.toUpperCase(Locale.ROOT);
                case LOWER -> text.toLowerCase(Locale.ROOT);
                case SMALL_CAPS -> smallCaps(text);
                default -> compact(text);
            };
        }

        private static String smallCaps(String text) {
            char[] result = null;
            for (int index = 0; index < text.length(); index++) {
                final char character = text.charAt(index);
                final int letter = character >= 'a' && character <= 'z' ? character - 'a'
                        : character >= 'A' && character <= 'Z' ? character - 'A' : -1;
                if (letter < 0) continue;
                final char small = SMALL_CAPITALS.charAt(letter);
                if (small == character) continue;
                if (result == null) result = text.toCharArray();
                result[index] = small;
            }
            return result == null ? text : new String(result);
        }

        // Shortens every number of at least 1000 that stands on its own: 1234567 becomes 1.2M.
        // Digits inside a word, such as a player name, are left alone.
        private static String compact(String text) {
            StringBuilder result = null;
            int copied = 0;
            int index = 0;
            final int length = text.length();
            while (index < length) {
                if (!isDigit(text.charAt(index)) || index > 0 && isWordPart(text.charAt(index - 1))) {
                    index++;
                    continue;
                }
                final boolean signed = index > 0 && text.charAt(index - 1) == '-'
                        && (index == 1 || !isWordPart(text.charAt(index - 2)));
                final int start = signed ? index - 1 : index;
                int end = index;
                while (end < length && isDigit(text.charAt(end))) end++;
                if (end + 1 < length && text.charAt(end) == '.' && isDigit(text.charAt(end + 1))) {
                    end += 2;
                    while (end < length && isDigit(text.charAt(end))) end++;
                }
                final String formatted = end < length && isWordPart(text.charAt(end))
                        ? null : compactNumber(text.substring(start, end));
                if (formatted != null) {
                    if (result == null) result = new StringBuilder(length);
                    result.append(text, copied, start).append(formatted);
                    copied = end;
                }
                index = end;
            }
            return result == null ? text : result.append(text, copied, length).toString();
        }

        // One decimal, rounded half up, without a trailing .0; null below 1000.
        private static String compactNumber(String number) {
            final int digits = number.length() - (number.charAt(0) == '-' ? 1 : 0);
            if (digits <= 18 && number.indexOf('.') < 0) return compactWhole(Long.parseLong(number));
            BigDecimal value = new BigDecimal(number);
            final boolean negative = value.signum() < 0;
            value = value.abs();
            if (value.compareTo(THOUSAND) < 0) return null;
            int unit = 0;
            while (value.compareTo(THOUSAND) >= 0 && unit < UNITS.length) {
                value = value.movePointLeft(3);
                unit++;
            }
            value = value.setScale(1, RoundingMode.HALF_UP);
            if (value.compareTo(THOUSAND) >= 0 && unit < UNITS.length) {
                value = value.movePointLeft(3).setScale(1, RoundingMode.HALF_UP);
                unit++;
            }
            return (negative ? "-" : "") + value.stripTrailingZeros().toPlainString() + UNITS[unit - 1];
        }

        // The same rounding in long arithmetic, for whole numbers of up to 18 digits.
        private static String compactWhole(long number) {
            final long magnitude = Math.abs(number);
            if (magnitude < 1000) return null;
            int unit = 1;
            long scale = 1000;
            while (unit < UNITS.length && magnitude >= scale * 1000) {
                scale *= 1000;
                unit++;
            }
            // Tenths of the value in this unit, rounded half up.
            final long tenth = scale / 10;
            long tenths = magnitude / tenth + (magnitude % tenth * 2 >= tenth ? 1 : 0);
            if (tenths >= 10_000 && unit < UNITS.length) {
                tenths = tenths / 1000 + (tenths % 1000 >= 500 ? 1 : 0);
                unit++;
            }
            final String value = tenths % 10 == 0 ? Long.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
            return (number < 0 ? "-" : "") + value + UNITS[unit - 1];
        }

        private static boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }

        private static boolean isWordPart(char character) {
            return Character.isLetterOrDigit(character) || character == '_';
        }

        // Counts code points, so a surrogate pair is never cut in half.
        private static String keep(String text, ChildScratch scratch) {
            final int budget = scratch.transformBudget;
            if (budget <= 0) return text.isEmpty() ? text : "";
            final int points = text.codePointCount(0, text.length());
            if (points <= budget) {
                scratch.transformBudget = budget - points;
                return text;
            }
            scratch.transformBudget = 0;
            return text.substring(0, text.offsetByCodePoints(0, budget));
        }
    }

    final class Obj implements Token {

        private final Component component;

        // These factories keep Adventure's object types, absent before 4.25, out of the
        // compiler: it must load on an older Adventure for templates that use no objects.
        static Obj head(String profileName, Style style) {
            return new Obj(ObjectContents.playerHead(profileName), style);
        }

        static Obj sprite(Key sprite, Style style) {
            return new Obj(ObjectContents.sprite(sprite), style);
        }

        private Obj(ObjectContents contents, Style style) {
            final Component component = Component.object(contents);
            this.component = style.isEmpty() ? component : component.style(style);
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            return this.component;
        }
    }

    final class ObjDyn implements Token {

        private final String[] staticParts;
        private final int[] placeholderIndices;
        private final Style style;
        // The segment's own text, rendered when Adventure rejects the name.
        private final Token fallback;

        ObjDyn(String[] staticParts, int[] placeholderIndices, Style style, Token fallback) {
            this.staticParts = staticParts;
            this.placeholderIndices = placeholderIndices;
            this.style = style;
            this.fallback = fallback;
        }

        @Override
        public Component render(Object[] placeholders, long[] numbers) {
            final String profileName = buildText(
                    this.staticParts,
                    this.placeholderIndices,
                    placeholders, numbers
            );
            final ObjectContents head;
            try {
                head = ObjectContents.playerHead(profileName);
            } catch (IllegalArgumentException rejected) {
                // Newer Adventure rejects names that no player can have, such as ones with
                // spaces. A placeholder value must not fail the render: the segment stays text.
                return this.fallback.render(placeholders, numbers);
            }
            final Component component = Component.object(head);
            return this.style.isEmpty() ? component : component.style(this.style);
        }
    }

    int MAX_RETAINED_BUILDER_CAPACITY = 8 * 1024;
    // Finish using the builder before another operation on this thread reuses it.
    ThreadLocal<StringBuilder> TL_SB = ThreadLocal.withInitial(() -> new StringBuilder(128));
    // Held weakly: a thread's strong reference to an object of this library would keep the
    // library's class loader alive for as long as that thread runs, for example the main
    // thread of a server that unloads the plugin embedding this library. A buffer in use is
    // reachable from the render that acquired it; idle ones may be collected and created again.
    ThreadLocal<WeakReference<ChildScratch>> CHILD_SCRATCH = new ThreadLocal<>();
    Component[] EMPTY_COMPONENTS = new Component[0];

    // Single-code-point strings reused by gradients.
    String[] CHAR_CACHE = buildCharCache();

    private static String[] buildCharCache() {
        final String[] cache = new String[256];
        for (int i = 0; i < 256; i++) cache[i] = String.valueOf((char) i);
        return cache;
    }

    String[] NO_PARTS = {};
    int[] NO_PH = {};

    // Builds action strings from literal parts and replacement text; non-text nodes contribute only their children.
    private static String buildText(
            String[] staticParts,
            int[] placeholderIndices,
            Object[] placeholders, long[] numbers
    ) {
        // A side of a comparison may be fixed text; it needs no copy.
        if (placeholderIndices.length == 0) {
            return staticParts.length == 0 || staticParts[0] == null ? "" : staticParts[0];
        }
        if (staticParts.length == 0 && placeholderIndices.length == 1) {
            final int index = placeholderIndices[0];
            final Object value = placeholders[index];
            if (value instanceof String text) return text;
            if (isNumber(value)) return numberText(placeholders, numbers, index);
            if (value instanceof TextComponent textComponent && textComponent.children().isEmpty()) {
                return textComponent.content();
            }
        }

        final StringBuilder builder = TL_SB.get();
        builder.setLength(0);
        try {
            if (staticParts.length > 0 && staticParts[0] != null) {
                builder.append(staticParts[0]);
            }

            for (int index = 0; index < placeholderIndices.length; index++) {
                appendValue(builder, placeholders, numbers, placeholderIndices[index]);

                final int staticIndex = index + 1;
                if (staticIndex < staticParts.length && staticParts[staticIndex] != null) {
                    builder.append(staticParts[staticIndex]);
                }
            }

            return builder.toString();
        } finally {
            releaseBuilder(builder);
        }
    }

    private static void releaseBuilder(StringBuilder builder) {
        if (builder.capacity() > MAX_RETAINED_BUILDER_CAPACITY) {
            TL_SB.set(new StringBuilder(128));
        } else {
            builder.setLength(0);
        }
    }

    private static void appendText(StringBuilder builder, Component component) {
        if (component == null) return;
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }

        final List<Component> children = component.children();
        for (int index = 0, size = children.size(); index < size; index++) {
            appendText(builder, children.get(index));
        }
    }

    private static Style withEvents(
            Style baseStyle,
            ClickEvent<?> clickEvent,
            HoverEvent<?> hoverEvent
    ) {
        Style style = baseStyle;
        if (!Objects.equals(style.clickEvent(), clickEvent)) {
            style = style.clickEvent(clickEvent);
        }
        if (!Objects.equals(style.hoverEvent(), hoverEvent)) {
            style = style.hoverEvent(hoverEvent);
        }
        return style;
    }

    private static Component textComponent(String content, Style style) {
        return Component.text(content, style);
    }

    private static Component textComponent(
            String content,
            Style style,
            List<? extends Component> children
    ) {
        final TextComponent component = Component.text(content, style);
        return children.isEmpty() ? component : component.children(children);
    }

    private static int countCodePoints(Component component) {
        int count = component instanceof TextComponent textComponent
                ? textComponent.content().codePointCount(0, textComponent.content().length())
                : 0;

        final List<Component> children = component.children();
        for (int index = 0, size = children.size(); index < size; index++) {
            count += countCodePoints(children.get(index));
        }
        return count;
    }

    // Spreads a gradient over the characters of a component, each character a child of its own.
    // With force it colors every character. Without force it skips the characters that have a
    // color of their own, or a shadow for a shadow gradient, and counts them, so the gradient
    // goes on after them. A component whose text is not its own, such as a translation, takes
    // the color at its place.
    private static Component applyGradient(Component component, Look.Gradient gradient, boolean force) {
        final Style[] styles = gradient.styles(countCodePoints(component));
        final ChildScratch scratch = childScratch();
        scratch.gradientIndex = 0;
        return applyGradient(component, styles, gradient.shadow(), force, false, scratch);
    }

    private static Component applyGradient(
            Component component,
            Style[] styles,
            boolean shadow,
            boolean force,
            boolean inheritsOwn,
            ChildScratch scratch
    ) {
        final boolean own = inheritsOwn
                || (shadow ? StyleUtils.hasShadow(component.style()) : component.color() != null);
        if (own && !force) {
            scratch.gradientIndex += countCodePoints(component);
            return component;
        }

        final List<Component> originalChildren = component.children();
        final String text = component instanceof TextComponent textComponent ? textComponent.content() : null;
        final int textLength = text == null ? 0 : text.codePointCount(0, text.length());
        // One character, or text that is not the component's own, takes one style itself.
        final boolean whole = text == null || textLength == 1;
        final int split = whole ? 0 : textLength;

        final Component self;
        if (whole) {
            final Style style = styles[Math.min(scratch.gradientIndex, styles.length - 1)];
            self = component.style(component.style().merge(style));
            scratch.gradientIndex += textLength;
        } else if (split > 0) {
            self = Component.text("", component.style());
        } else {
            self = component;
        }
        if (originalChildren.isEmpty() && split == 0) return self;

        final ChildBuffer children = scratch.acquire(split + originalChildren.size());
        try {
            int outputIndex = 0;
            int offset = 0;
            for (int index = 0; index < split; index++) {
                final int codePoint = text.codePointAt(offset);
                final String value = codePoint < CHAR_CACHE.length
                        ? CHAR_CACHE[codePoint]
                        : Character.toString(codePoint);
                children.setComponent(outputIndex++, Component.text(value, styles[scratch.gradientIndex++]));
                offset += Character.charCount(codePoint);
            }

            for (int index = 0, size = originalChildren.size(); index < size; index++) {
                children.setComponent(outputIndex++,
                        applyGradient(originalChildren.get(index), styles, shadow, force, own, scratch));
            }
            return self.children(children);
        } finally {
            releaseChildren(children);
        }
    }

    private static ChildScratch childScratch() {
        final WeakReference<ChildScratch> reference = CHILD_SCRATCH.get();
        ChildScratch scratch = reference == null ? null : reference.get();
        if (scratch == null) {
            scratch = new ChildScratch();
            CHILD_SCRATCH.set(new WeakReference<>(scratch));
        }
        return scratch;
    }

    private static ChildBuffer acquireChildren(int size) {
        return childScratch().acquire(size);
    }

    private static void releaseChildren(ChildBuffer buffer) {
        buffer.owner.release(buffer);
    }

    // LIFO buffers isolate recursive renders; every acquire must be paired with release.
    final class ChildScratch {

        private static final int MAX_RETAINED_CAPACITY = 1024;

        private ChildBuffer[] buffers = new ChildBuffer[4];
        private int depth;
        private int gradientIndex;
        // Characters a transform with a limit may still keep while it walks one result.
        private int transformBudget;

        private ChildBuffer acquire(int size) {
            if (this.depth == this.buffers.length) {
                this.buffers = Arrays.copyOf(this.buffers, this.buffers.length << 1);
            }

            ChildBuffer buffer = this.buffers[this.depth];
            if (buffer == null) {
                buffer = new ChildBuffer(this);
                this.buffers[this.depth] = buffer;
            }
            this.depth++;
            buffer.prepare(size);
            return buffer;
        }

        private void release(ChildBuffer buffer) {
            buffer.release();
            this.depth--;
        }
    }

    final class ChildBuffer extends AbstractList<Component> implements RandomAccess {

        private final ChildScratch owner;
        private Component[] components = EMPTY_COMPONENTS;
        private int size;

        private ChildBuffer(ChildScratch owner) {
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

        private void setComponent(int index, Component component) {
            this.components[index] = component;
        }

        private void release() {
            if (this.components.length > ChildScratch.MAX_RETAINED_CAPACITY) {
                this.components = EMPTY_COMPONENTS;
            } else {
                Arrays.fill(this.components, 0, this.size, null);
            }
            this.size = 0;
        }

        @Override
        public Component get(int index) {
            if (index < 0 || index >= this.size) throw new IndexOutOfBoundsException(index);
            return this.components[index];
        }

        @Override
        public int size() {
            return this.size;
        }
    }

}
