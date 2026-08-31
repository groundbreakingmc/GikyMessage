package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

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
        if (shadowColor != null) builder.shadowColor(shadowColor);

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

    static ShadowColor shadowColorOf(int color) {
        final int argb = OPAQUE_ALPHA | (color & 0x00FFFFFF);
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

    static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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

    private static void applyDecoration(Style.Builder builder, short decorations, TextDecoration decoration) {
        final int state = (decorations >>> (decoration.ordinal() * BITS)) & MASK;
        if (state == BIT_TRUE) {
            builder.decoration(decoration, TextDecoration.State.TRUE);
        } else if (state == BIT_FALSE) {
            builder.decoration(decoration, TextDecoration.State.FALSE);
        }
    }
}
