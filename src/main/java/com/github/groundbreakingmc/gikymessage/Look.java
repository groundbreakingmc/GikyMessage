package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.format.Style;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The value of a color, shadow or style action: a style applied to the segment as a whole, or a
 * gradient of text colors spread over the characters of the segment.
 */
final class Look {

    /** A {@code color} value: one color or a gradient of them. */
    static final byte COLOR = 0;
    /** A {@code shadow} value: {@code none} or one shadow. */
    static final byte SHADOW = 1;
    /** A {@code style} value: space-separated colors, decorations, shadows and {@code reset}. */
    static final byte STYLE = 2;

    /** The value this look was read from, or null for one built while compiling. */
    final String source;
    final Style style;
    final Gradient colors;

    /**
     * Creates a look.
     *
     * @param source the value it was read from, or null
     * @param style  the style applied as a whole, empty for none
     * @param colors the gradient of text colors, or null
     */
    Look(String source, Style style, Gradient colors) {
        this.source = source;
        this.style = style;
        this.colors = colors;
    }

    /**
     * Reads a value of the given kind.
     *
     * @param kind  {@link #COLOR}, {@link #SHADOW} or {@link #STYLE}
     * @param value the value as written
     * @param phase the shift of the segment's gradients, a fraction of a cycle in [0, 1)
     * @return the look, or null when the value is invalid
     */
    static Look parse(byte kind, String value, float phase) {
        return switch (kind) {
            case COLOR -> StyleUtils.parseColorLook(value, phase);
            case SHADOW -> StyleUtils.parseShadowLook(value);
            default -> StyleUtils.parseStyleLook(value);
        };
    }

    boolean isEmpty() {
        return this.style.isEmpty() && this.colors == null;
    }

    /** The colors of a gradient and the styles that give each character of a text its color. */
    static final class Gradient {

        private final int[] stops;
        // Fraction of a full color cycle in [0, 1); zero is the plain gradient.
        private final float phase;
        // The first stop alone, for content without text of its own, such as a translation.
        private final Style[] first;
        // The styles for the last texts, one per length modulo 16, so names of up to 16
        // characters, the usual content from a placeholder, never evict each other.
        private final AtomicReferenceArray<Style[]> cache = new AtomicReferenceArray<>(16);

        /**
         * Creates a gradient.
         *
         * @param stops two or more RGB colors
         * @param phase the shift along the cycle of the colors, in [0, 1)
         */
        Gradient(int[] stops, float phase) {
            this.stops = stops;
            this.phase = phase;
            this.first = new Style[]{style(stops[0])};
        }

        /** One style per character of a text this long; the first stop alone for no text. */
        Style[] styles(int length) {
            if (length == 0) return this.first;
            final int slot = length & 15;
            Style[] styles = this.cache.get(slot);
            if (styles == null || styles.length != length) {
                styles = new Style[length];
                for (int index = 0; index < length; index++) {
                    final float position = length == 1 ? 0f : (float) index / (length - 1);
                    styles[index] = style(this.phase == 0f
                            ? StyleUtils.interpolate(this.stops, position)
                            : StyleUtils.interpolateCycle(this.stops, position, this.phase));
                }
                this.cache.set(slot, styles);
            }
            return styles;
        }

        private static Style style(int rgb) {
            return Style.style(StyleUtils.textColorOf(rgb));
        }
    }
}
