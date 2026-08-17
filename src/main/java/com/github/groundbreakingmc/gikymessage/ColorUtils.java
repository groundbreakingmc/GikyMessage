package com.github.groundbreakingmc.gikymessage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Internal utility class for color parsing, caching, and interpolation.
 */
final class ColorUtils {

    private static final Int2ObjectMap<TextColor> COLOR_CACHE = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<ShadowColor> SHADOW_COLOR_CACHE = new Int2ObjectOpenHashMap<>();

    private ColorUtils() {
    }

    /**
     * Returns a cached {@link TextColor} for the given 24-bit RGB value.
     */
    static TextColor textColorOf(int color) {
        TextColor textColor = COLOR_CACHE.get(color);
        if (textColor != null) return textColor;
        textColor = TextColor.color(color);
        COLOR_CACHE.put(color, textColor);
        return textColor;
    }

    /**
     * Returns a cached {@link ShadowColor} for the given 32-bit ARGB value.
     */
    static ShadowColor shadowColorOf(int color) {
        ShadowColor shadowColor = SHADOW_COLOR_CACHE.get(color);
        if (shadowColor != null) return shadowColor;
        shadowColor = ShadowColor.shadowColor(color);
        SHADOW_COLOR_CACHE.put(color, shadowColor);
        return shadowColor;
    }

    /**
     * Linearly interpolates across a gradient defined by {@code colors} at position {@code t ∈ [0, 1]}.
     */
    static int interpolate(int[] colors, float t) {
        if (colors.length == 1) return colors[0];
        final float scaled = t * (colors.length - 1);
        final int idx = (int) scaled;
        if (idx >= colors.length - 1) return colors[colors.length - 1];
        return lerpColor(colors[idx], colors[idx + 1], scaled - idx);
    }

    static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * Maps a legacy Minecraft color code character ({@code 0–9}, {@code a–f}) to a {@link TextColor}.
     *
     * @return the corresponding {@link TextColor}, or {@code null} if the code is not a color code
     */
    static TextColor fromLegacyCode(char code) {
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    static int lerpColor(int a, int b, float t) {
        return (lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, t) << 16)
                | (lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, t) << 8)
                | lerp(a & 0xFF, b & 0xFF, t);
    }

    static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    /**
     * Parses a {@code #RGB} or {@code #RRGGBB} hex color from {@code src} starting at {@code start}.
     *
     * @return a packed {@code long} where the low 32 bits hold the RGB value and the high 32 bits
     * hold the total character length consumed (4 for {@code #RGB}, 7 for {@code #RRGGBB}),
     * or {@code -1L} on failure
     */
    static long parseHexPacked(char[] src, int start) {
        if (start >= src.length || src[start] != '#') return -1L;
        int count = 0;
        while (start + 1 + count < src.length && count < 6
                && isHexChar(src[start + 1 + count])) count++;
        if (count == 3) {
            final int r = hexVal(src[start + 1]);
            final int g = hexVal(src[start + 2]);
            final int b = hexVal(src[start + 3]);
            if (r == -1 || g == -1 || b == -1) return -1L;
            final int rgb = ((r * 17) << 16) | ((g * 17) << 8) | (b * 17);
            return ((long) 4 << 32) | (rgb & 0xFFFFFFFFL);
        }
        if (count == 6) {
            final int r1 = hexVal(src[start + 1]);
            final int r2 = hexVal(src[start + 2]);
            final int g1 = hexVal(src[start + 3]);
            final int g2 = hexVal(src[start + 4]);
            final int b1 = hexVal(src[start + 5]);
            final int b2 = hexVal(src[start + 6]);
            if (r1 == -1 || r2 == -1 || g1 == -1 || g2 == -1 || b1 == -1 || b2 == -1) return -1L;
            final int rgb = ((r1 << 4 | r2) << 16) | ((g1 << 4 | g2) << 8) | (b1 << 4 | b2);
            return ((long) 7 << 32) | (rgb & 0xFFFFFFFFL);
        }
        return -1L;
    }
}
