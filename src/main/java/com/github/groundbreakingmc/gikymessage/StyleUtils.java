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

    private static final int OPAQUE_ALPHA = 0xFF000000;
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
            final int digits = end - hexStart - 1;
            if (digits != 3 && digits != 6) return null;
            int rgb = 0;
            for (int index = hexStart + 1; index < end; index++) {
                final int digit = hexVal(value.charAt(index));
                if (digit == -1) return null;
                rgb = (rgb << 4) | digit;
            }
            if (digits == 3) {
                rgb = (((rgb >>> 8) & 15) * 17 << 16)
                        | (((rgb >>> 4) & 15) * 17 << 8) | ((rgb & 15) * 17);
            }
            return textColorOf(rgb);
        }
        return NamedTextColor.NAMES.value(value.substring(start, end).toLowerCase(java.util.Locale.ROOT));
    }

    // Returns null for invalid formatting; empty input produces an empty style.
    static Style parseStyle(String value) {
        TextColor color = null;
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
        return create(color, null, decorations);
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

    // Packs consumed length in the high 32 bits and RGB in the low 24; -1 means invalid hex.
    static long parseHexPacked(char[] src, int start) {
        if (start >= src.length || src[start] != '#') return -1L;
        int count = 0;
        int rgb = 0;
        while (count < 6 && start + 1 + count < src.length) {
            final int digit = hexVal(src[start + 1 + count]);
            if (digit == -1) break;
            rgb = (rgb << 4) | digit;
            count++;
        }
        if (count == 3) {
            final int r = (rgb >>> 8) & 0xF;
            final int g = (rgb >>> 4) & 0xF;
            final int b = rgb & 0xF;
            rgb = ((r * 17) << 16) | ((g * 17) << 8) | (b * 17);
            return ((long) 4 << 32) | (rgb & 0xFFFFFFFFL);
        }
        if (count == 6) {
            return ((long) 7 << 32) | (rgb & 0xFFFFFFFFL);
        }
        return -1L;
    }

    // Packs consumed length in the high 32 bits and ARGB in the low 32; -1 means invalid hex.
    // Eight digits are RRGGBBAA and four are RGBA: alpha comes last, as in Adventure's hex
    // strings. Six and three digits are opaque. Seven digits keep their earlier meaning, a
    // six-digit color followed by one character of text.
    static long parseShadowPacked(char[] src, int start) {
        if (start >= src.length || src[start] != '#') return -1L;
        int count = 0;
        long digits = 0;
        while (count < 8 && start + 1 + count < src.length) {
            final int digit = hexVal(src[start + 1 + count]);
            if (digit == -1) break;
            digits = (digits << 4) | digit;
            count++;
        }
        final int argb;
        if (count == 8) {
            argb = (int) ((digits & 0xFF) << 24 | digits >>> 8);
        } else if (count == 6 || count == 7) {
            argb = OPAQUE_ALPHA | (int) (count == 7 ? digits >>> 4 : digits);
            count = 6;
        } else if (count == 4) {
            argb = expand((int) (digits & 0xF)) << 24 | expand((int) (digits >>> 12 & 0xF)) << 16
                    | expand((int) (digits >>> 8 & 0xF)) << 8 | expand((int) (digits >>> 4 & 0xF));
        } else if (count == 3) {
            argb = OPAQUE_ALPHA | expand((int) (digits >>> 8 & 0xF)) << 16
                    | expand((int) (digits >>> 4 & 0xF)) << 8 | expand((int) (digits & 0xF));
        } else {
            return -1L;
        }
        return ((long) (count + 1) << 32) | (argb & 0xFFFFFFFFL);
    }

    // One hex digit of a short form stands for two equal digits.
    private static int expand(int digit) {
        return digit * 17;
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

        private static void apply(Style.Builder builder, ShadowColor shadowColor) {
            builder.shadowColor(shadowColor);
        }
    }
}
