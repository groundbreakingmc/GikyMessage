package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Compact internal style holder that packs all five {@link TextDecoration} states
 * into a single {@code short} using 2 bits per decoration.
 *
 * <p>Decoration bit encoding:
 * <ul>
 *   <li>{@link #BIT_NOT_SET} ({@code 0b00}) — not explicitly set</li>
 *   <li>{@link #BIT_TRUE}    ({@code 0b01}) — explicitly enabled</li>
 *   <li>{@link #BIT_FALSE}   ({@code 0b10}) — explicitly disabled</li>
 * </ul>
 */
final class StyleImpl {

    static final StyleImpl EMPTY = new StyleImpl(null, null, (short) 0, null, null, null, null);

    static final int BITS = 2;
    static final int MASK = 0b11;

    static final int BIT_NOT_SET = 0b00;
    static final int BIT_TRUE = 0b01;
    static final int BIT_FALSE = 0b10;

    private final Style style;

    StyleImpl(
            @Nullable TextColor color, @Nullable ShadowColor shadowColor, short decorations,
            @Nullable ClickEvent clickEvent, @Nullable HoverEvent<?> hoverEvent,
            @Nullable String insertion, @Nullable Key font) {
        if (color == null && shadowColor == null && decorations == 0
                && clickEvent == null && hoverEvent == null && insertion == null && font == null) {
            this.style = Style.empty();
            return;
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
        this.style = builder.build();
    }

    Style style() {
        return this.style;
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
