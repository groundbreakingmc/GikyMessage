package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.format.Style;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The value of a color, shadow or style action: a style applied to the segment as a whole, and
 * gradients of text colors and of shadows, each spread over the characters of the segment. A
 * color or a shadow is either in the style or a gradient, never both.
 */
final class Look {

    /** A {@code color} value: one color or a gradient of them. */
    static final byte COLOR = 0;
    /** A {@code shadow} value: {@code none}, one shadow or a gradient of them. */
    static final byte SHADOW = 1;
    /** A {@code style} value: space-separated colors, decorations, shadows and {@code reset}. */
    static final byte STYLE = 2;

    /** The value this look was read from, or null for one built while compiling. */
    final String source;
    final Style style;
    final Gradient colors;
    final Gradient shadows;

    /**
     * Creates a look.
     *
     * @param source  the value it was read from, or null
     * @param style   the style applied as a whole, empty for none
     * @param colors  the gradient of text colors, or null
     * @param shadows the gradient of shadows, or null
     */
    Look(String source, Style style, Gradient colors, Gradient shadows) {
        this.source = source;
        this.style = style;
        this.colors = colors;
        this.shadows = shadows;
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
            case SHADOW -> StyleUtils.parseShadowLook(value, phase);
            default -> StyleUtils.parseStyleLook(value, phase);
        };
    }

    boolean isEmpty() {
        return this.style.isEmpty() && this.colors == null && this.shadows == null;
    }

    /**
     * The colors of a gradient, as RGB for text and ARGB for shadows, and the styles that give
     * each character of a text its color.
     */
    static final class Gradient {

        private final int[] stops;
        private final boolean shadow;
        // Fraction of a full color cycle in [0, 1); zero is the plain gradient.
        private final float phase;
        // The first stop alone, for content without text of its own, such as a translation.
        private final Style[] first;
        // The styles for the last texts, one per length modulo 16, so names of up to 16
        // characters, the usual content from a placeholder, never evict each other.
        private final AtomicReferenceArray<Style[]> cache = new AtomicReferenceArray<>(16);

        /**
         * Creates a gradient. A shadow gradient needs shadow colors on the platform.
         *
         * @param stops  two or more colors, RGB for text or ARGB for shadows
         * @param shadow whether the colors are shadows
         * @param phase  the shift along the cycle of the colors, in [0, 1)
         */
        Gradient(int[] stops, boolean shadow, float phase) {
            this.stops = stops;
            this.shadow = shadow;
            this.phase = phase;
            this.first = new Style[]{this.style(stops[0])};
        }

        boolean shadow() {
            return this.shadow;
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
                    styles[index] = this.style(this.phase == 0f
                            ? StyleUtils.interpolate(this.stops, position)
                            : StyleUtils.interpolateCycle(this.stops, position, this.phase));
                }
                this.cache.set(slot, styles);
            }
            return styles;
        }

        private Style style(int color) {
            return this.shadow ? StyleUtils.shadowStyle(color) : Style.style(StyleUtils.textColorOf(color));
        }
    }
}
