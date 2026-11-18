package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.*;

import java.util.concurrent.atomic.AtomicReferenceArray;

final class StyleUtils {

    static final int BITS = 2;
    static final int MASK = 0b11;

    static final int BIT_NOT_SET = 0b00;
    static final int BIT_TRUE = 0b01;
    static final int BIT_FALSE = 0b10;

    private static final TextDecoration[] DECORATIONS = TextDecoration.values();

    private static final int COLOR_CACHE_SIZE = 1 << 13;
    private static final int COLOR_CACHE_MASK = COLOR_CACHE_SIZE - 1;

    private static final AtomicReferenceArray<TextColor> COLOR_CACHE =
            new AtomicReferenceArray<>(COLOR_CACHE_SIZE);
    private static final AtomicReferenceArray<ShadowColor> SHADOW_COLOR_CACHE =
            new AtomicReferenceArray<>(COLOR_CACHE_SIZE);

    private StyleUtils() {
    }

    static Style create(TextColor color, ShadowColor shadowColor, short decorations) {
        return create(color, shadowColor, decorations, null, null, null, null);
    }

    static Style create(
            TextColor color,
            ShadowColor shadowColor,
            short decorations,
            ClickEvent clickEvent,
            HoverEvent<?> hoverEvent,
            String insertion,
            Key font
    ) {
        if (color == null && shadowColor == null && decorations == 0
                && clickEvent == null && hoverEvent == null && insertion == null && font == null) {
            return Style.empty();
        }

        final Style.Builder builder = Style.style();
        if (color != null) builder.color(color);
        if (shadowColor != null) Shadows.apply(builder, shadowColor);

        applyDecoration(builder, decorations, TextDecoration.BOLD);
        applyDecoration(builder, decorations, TextDecoration.ITALIC);
        applyDecoration(builder, decorations, TextDecoration.UNDERLINED);
        applyDecoration(builder, decorations, TextDecoration.STRIKETHROUGH);
        applyDecoration(builder, decorations, TextDecoration.OBFUSCATED);

        if (clickEvent != null) builder.clickEvent(clickEvent);
        if (hoverEvent != null) builder.hoverEvent(hoverEvent);
        if (insertion != null) builder.insertion(insertion);
        if (font != null) builder.font(font);
        return builder.build();
    }

    static short withDecoration(short decorations, TextDecoration decoration, int state) {
        final int shift = decoration.ordinal() * BITS;
        final int mask = MASK << shift;
        return (short) ((decorations & ~mask) | (state << shift));
    }

    static short resetDecorations(short inheritedDecorations) {
        short result = 0;
        for (final TextDecoration decoration : DECORATIONS) {
            final int shift = decoration.ordinal() * BITS;
            final int inheritedState = (inheritedDecorations >>> shift) & MASK;
            if (inheritedState == BIT_TRUE) {
                result |= (short) (BIT_FALSE << shift);
            }
        }
        return result;
    }

    static short decorationDelta(short current, short inherited) {
        short result = 0;
        for (final TextDecoration decoration : DECORATIONS) {
            final int shift = decoration.ordinal() * BITS;
            final int currentState = (current >>> shift) & MASK;
            final int inheritedState = (inherited >>> shift) & MASK;
            if (currentState != BIT_NOT_SET && currentState != inheritedState) {
                result |= (short) (currentState << shift);
            }
        }
        return result;
    }

    static TextColor textColorOf(int color) {
        final int rgb = color & 0x00FFFFFF;
        final int slot = colorCacheSlot(rgb);
        final TextColor cached = COLOR_CACHE.get(slot);
        if (cached != null && cached.value() == rgb) return cached;

        final TextColor textColor = TextColor.color(rgb);
        COLOR_CACHE.lazySet(slot, textColor);
        return textColor;
    }

    static ShadowColor shadowColorOf(int argb) {
        final int slot = colorCacheSlot(argb);
        final ShadowColor cached = SHADOW_COLOR_CACHE.get(slot);
        if (cached != null && cached.value() == argb) return cached;

        final ShadowColor shadowColor = ShadowColor.shadowColor(argb);
        SHADOW_COLOR_CACHE.lazySet(slot, shadowColor);
        return shadowColor;
    }

    private static int colorCacheSlot(int color) {
        final int mixed = color * 0x9E3779B9;
        return (mixed ^ (mixed >>> 16)) & COLOR_CACHE_MASK;
    }

    static int interpolate(int[] colors, float t) {
        if (colors.length == 1) return colors[0];
        final float scaled = t * (colors.length - 1);
        final int idx = (int) scaled;
        if (idx >= colors.length - 1) return colors[colors.length - 1];
        return lerpColor(colors[idx], colors[idx + 1], scaled - idx);
    }

    // The colors form a loop c0 -> c1 -> ... -> c(n-1) -> c0. Without a shift the text spans
    // c0 to c(n-1), as an ordinary gradient does. The phase moves that span along the loop by a
    // fraction of its whole length, so phases 0 and 1 give the same colors and a sequence of
    // evenly spaced phases moves the colors smoothly along the text.
    static int interpolateCycle(int[] colors, float position, float phase) {
        final int count = colors.length;
        double segment = (double) position * (count - 1) + (double) phase * count;
        segment -= Math.floor(segment / count) * count;
        int low = (int) segment;
        if (low >= count) {
            low = 0;
            segment = 0;
        }
        final int high = low + 1 == count ? 0 : low + 1;
        return lerpColor(colors[low], colors[high], (float) (segment - low));
    }

    static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // Returns null unless the entire value denotes one color.
    static TextColor parseColor(String value) {
        return parseColor(value, 0, value.length());
    }

    private static TextColor parseColor(String value, int start, int end) {
        final int length = end - start;
        if (length == 0) return null;
        if (length == 1) return fromLegacyCode(value.charAt(start));
        final char first = value.charAt(start);
        final boolean legacy = first == '&' || first == '§';
        if (length == 2 && legacy) return fromLegacyCode(value.charAt(start + 1));

        if (length == 14 && legacy && Character.toLowerCase(value.charAt(start + 1)) == 'x') {
            int rgb = 0;
            for (int index = start + 2; index < end; index += 2) {
                if (value.charAt(index) != first) return null;
                final int digit = hexVal(value.charAt(index + 1));
                if (digit == -1) return null;
                rgb = (rgb << 4) | digit;
            }
            return textColorOf(rgb);
        }

        final int hexStart = legacy ? start + 1 : start;
        if (value.charAt(hexStart) == '#') {
            if (end - hexStart != 7) return null;
            int rgb = 0;
            for (int index = hexStart + 1; index < end; index++) {
                final int digit = hexVal(value.charAt(index));
                if (digit == -1) return null;
                rgb = (rgb << 4) | digit;
            }
            return textColorOf(rgb);
        }
        return NamedTextColor.NAMES.value(value.substring(start, end).toLowerCase(java.util.Locale.ROOT));
    }

    // Returns null for invalid formatting; empty input produces an empty style. A shadow, written
    // as in text ($#ff5555 or $#ff5555:80), is left out where the platform has no shadow colors:
    // a style from a placeholder cannot fail, and the compiler rejects a fixed one there. reset
    // also resets the shadow, marked with Shadows.RESET for applyStyle.
    static Style parseStyle(String value) {
        TextColor color = null;
        ShadowColor shadowColor = null;
        short decorations = 0;
        int index = 0;
        while (index < value.length()) {
            if (Character.isWhitespace(value.charAt(index))) {
                index++;
                continue;
            }
            final int start = index;
            final char first = value.charAt(index);
            TextDecoration decoration = null;
            boolean reset = false;
            if (first == '$') {
                final char[] chars = value.toCharArray();
                final int digits = start + 1 < chars.length && chars[start + 1] == '#' ? start + 2 : start + 1;
                final int rgb = parseHex(chars, digits);
                if (rgb == -1) return null;
                final int alpha = parseAlpha(chars, digits + 6);
                index = digits + (alpha == -1 ? 6 : 9);
                if (index < chars.length && !Character.isWhitespace(chars[index])
                        && chars[index] != '&' && chars[index] != '§') return null;
                if (Platform.SHADOW_COLORS) shadowColor = shadowColorOf((alpha == -1 ? 0xFF : alpha) << 24 | rgb);
                continue;
            }
            if (first == '&' || first == '§') {
                if (++index == value.length()) return null;
                final char code = Character.toLowerCase(value.charAt(index++));
                if (code == 'x') {
                    index = start + 14;
                    if (index > value.length()) return null;
                } else if (code == '#') {
                    while (index < value.length() && isHexChar(value.charAt(index))) index++;
                } else {
                    decoration = switch (code) {
                        case 'k' -> TextDecoration.OBFUSCATED;
                        case 'l' -> TextDecoration.BOLD;
                        case 'm' -> TextDecoration.STRIKETHROUGH;
                        case 'n' -> TextDecoration.UNDERLINED;
                        case 'o' -> TextDecoration.ITALIC;
                        default -> null;
                    };
                    reset = code == 'r';
                }
            } else {
                while (index < value.length() && !Character.isWhitespace(value.charAt(index))
                        && value.charAt(index) != '&' && value.charAt(index) != '§') index++;
                final String name = value.substring(start, index).toLowerCase(java.util.Locale.ROOT);
                decoration = switch (name) {
                    case "bold" -> TextDecoration.BOLD;
                    case "italic" -> TextDecoration.ITALIC;
                    case "underlined", "underline" -> TextDecoration.UNDERLINED;
                    case "strikethrough" -> TextDecoration.STRIKETHROUGH;
                    case "obfuscated" -> TextDecoration.OBFUSCATED;
                    default -> null;
                };
                reset = name.equals("reset");
            }

            if (reset) {
                color = NamedTextColor.WHITE;
                // Like &r in text; applyStyle removes the segment's own shadows on seeing it.
                if (Platform.SHADOW_COLORS) shadowColor = Shadows.RESET;
                for (final TextDecoration entry : DECORATIONS) {
                    decorations = withDecoration(decorations, entry, BIT_FALSE);
                }
            } else if (decoration != null) {
                decorations = withDecoration(decorations, decoration, BIT_TRUE);
            } else {
                final TextColor parsed = parseColor(value, start, index);
                if (parsed == null) return null;
                color = parsed;
            }
        }
        return create(color, shadowColor, decorations);
    }

    // A shadow as shadow: takes it: none, or as in text with or without $ and #, such as
    // $#ff5555, #ff5555:80 or ff5555.
    static boolean isShadow(String value) {
        if (value.equals("none")) return true;
        final char[] chars = value.toCharArray();
        int digits = 0;
        if (digits < chars.length && chars[digits] == '$') digits++;
        if (digits < chars.length && chars[digits] == '#') digits++;
        if (parseHex(chars, digits) == -1) return false;
        final int end = digits + 6;
        return end == chars.length || end + 3 == chars.length && parseAlpha(chars, end) != -1;
    }

    // The shadow of a value isShadow accepts, or null for any other; needs shadow colors.
    static ShadowColor parseShadow(String value) {
        if (!isShadow(value)) return null;
        if (value.equals("none")) return ShadowColor.none();
        final char[] chars = value.toCharArray();
        int digits = 0;
        if (chars[digits] == '$') digits++;
        if (chars[digits] == '#') digits++;
        final int alpha = parseAlpha(chars, digits + 6);
        return shadowColorOf((alpha == -1 ? 0xFF : alpha) << 24 | parseHex(chars, digits));
    }

    // Whether a parsed style resets the shadow; then withoutShadow gives the rest of it.
    static boolean resetsShadow(Style style) {
        return Platform.SHADOW_COLORS && Shadows.resets(style);
    }

    static Style withoutShadow(Style style) {
        return Shadows.with(style, null);
    }

    static Style withShadow(Style style, ShadowColor shadowColor) {
        return Shadows.with(style, shadowColor);
    }

    static TextColor fromLegacyCode(char code) {
        return switch (Character.toLowerCase(code)) {
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

    // The six hex digits at start as RGB, or -1 when fewer follow. A color has no shorter form,
    // so its code is always as long and never takes part of the text after it.
    static int parseHex(char[] src, int start) {
        if (start + 6 > src.length) return -1;
        int rgb = 0;
        for (int index = start; index < start + 6; index++) {
            final int digit = hexVal(src[index]);
            if (digit == -1) return -1;
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    // The alpha of a shadow: a colon and two hex digits after its color, as in $#ff5555:80.
    // Returns -1 for anything else, so the text after a shadow is never read as alpha.
    static int parseAlpha(char[] src, int start) {
        if (start + 3 > src.length || src[start] != ':') return -1;
        final int high = hexVal(src[start + 1]);
        final int low = hexVal(src[start + 2]);
        return high == -1 || low == -1 ? -1 : high << 4 | low;
    }

    private static void applyDecoration(Style.Builder builder, short decorations, TextDecoration decoration) {
        final int state = (decorations >>> (decoration.ordinal() * BITS)) & MASK;
        if (state == BIT_TRUE) {
            builder.decoration(decoration, TextDecoration.State.TRUE);
        } else if (state == BIT_FALSE) {
            builder.decoration(decoration, TextDecoration.State.FALSE);
        }
    }

    // The only call into Adventure's shadow API, which is absent before 4.18. In a class of
    // its own, so that the rest of this one links on an older Adventure; it loads with the
    // first shadow, and the compiler rejects shadows the platform lacks before that.
    private static final class Shadows {

        // Never rendered: applyStyle replaces it by removing shadows. Compared by identity, so
        // a transparent shadow someone writes is never taken for it.
        private static final ShadowColor RESET = ShadowColor.shadowColor(0x00FFFFFF);

        private static void apply(Style.Builder builder, ShadowColor shadowColor) {
            builder.shadowColor(shadowColor);
        }

        private static boolean resets(Style style) {
            return style.shadowColor() == RESET;
        }

        private static Style with(Style style, ShadowColor shadowColor) {
            return style.shadowColor(shadowColor);
        }
    }
}
