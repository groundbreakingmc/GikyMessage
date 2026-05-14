package com.github.groundbreakingmc.gikymessage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.ARGBLike;
import net.kyori.examination.ExaminableProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compact {@link Style} implementation that packs all five {@link TextDecoration} states
 * into a single {@code short} using 2 bits per decoration.
 *
 * <p>Decoration bit encoding:
 * <ul>
 *   <li>{@link #BIT_NOT_SET} ({@code 0b00}) — not explicitly set</li>
 *   <li>{@link #BIT_TRUE}    ({@code 0b01}) — explicitly enabled</li>
 *   <li>{@link #BIT_FALSE}   ({@code 0b10}) — explicitly disabled</li>
 * </ul>
 */
@SuppressWarnings("ALL")
final class StyleImpl implements Style {

    static final StyleImpl EMPTY = new StyleImpl(null, null, (short) 0, null, null, null, null);

    static final int BITS = 2;
    static final int MASK = 0b11;

    static final int BIT_NOT_SET = 0b00;
    static final int BIT_TRUE = 0b01;
    static final int BIT_FALSE = 0b10;

    private final @Nullable TextColor color;
    private final @Nullable ShadowColor shadowColor;
    private final short decorations;
    private final @Nullable ClickEvent clickEvent;
    private final @Nullable HoverEvent<?> hoverEvent;
    private final @Nullable String insertion;
    private final @Nullable Key font;

    private final Int2ObjectMap<Style> cache = new Int2ObjectOpenHashMap<>();

    StyleImpl(
            @Nullable TextColor color, @Nullable ShadowColor shadowColor, short decorations,
            @Nullable ClickEvent clickEvent, @Nullable HoverEvent<?> hoverEvent,
            @Nullable String insertion, @Nullable Key font) {
        this.color = color;
        this.shadowColor = shadowColor;
        this.decorations = decorations;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.insertion = insertion;
        this.font = font;
    }

    private static int encode(TextDecoration.State state) {
        return switch (state) {
            case NOT_SET -> BIT_NOT_SET;
            case TRUE -> BIT_TRUE;
            case FALSE -> BIT_FALSE;
        };
    }

    private static TextDecoration.State decode(int bits) {
        return switch (bits & MASK) {
            case BIT_TRUE -> TextDecoration.State.TRUE;
            case BIT_FALSE -> TextDecoration.State.FALSE;
            default -> TextDecoration.State.NOT_SET;
        };
    }

    static StyleImpl of(@Nullable TextColor color) {
        return color == null ? EMPTY : new StyleImpl(color, null, (short) 0, null, null, null, null);
    }

    static StyleImpl of(@Nullable TextColor color,
                        boolean bold, boolean italic, boolean underlined,
                        boolean strikethrough, boolean obfuscated) {
        short deco = 0;
        if (bold) deco |= (short) (BIT_TRUE << (TextDecoration.BOLD.ordinal() * BITS));
        if (italic) deco |= (short) (BIT_TRUE << (TextDecoration.ITALIC.ordinal() * BITS));
        if (underlined) deco |= (short) (BIT_TRUE << (TextDecoration.UNDERLINED.ordinal() * BITS));
        if (strikethrough) deco |= (short) (BIT_TRUE << (TextDecoration.STRIKETHROUGH.ordinal() * BITS));
        if (obfuscated) deco |= (short) (BIT_TRUE << (TextDecoration.OBFUSCATED.ordinal() * BITS));
        return (color == null && deco == 0) ? EMPTY : new StyleImpl(color, null, deco, null, null, null, null);
    }

    static short packDecorations(Map<TextDecoration, TextDecoration.State> decorations) {
        short result = 0;
        for (final TextDecoration decoration : TextDecoration.values()) {
            final TextDecoration.State state = decorations.getOrDefault(decoration, TextDecoration.State.NOT_SET);
            result |= (short) (encode(state) << (decoration.ordinal() * BITS));
        }
        return result;
    }

    @Override
    public @Nullable TextColor color() {
        return this.color;
    }

    @Override
    public @NotNull Style color(@Nullable TextColor textColor) {
        final Style style = this.cache.get(textColor.value());
        if (style != null) return style;

        final Style newStyle = new StyleImpl(textColor, this.shadowColor, this.decorations,
                this.clickEvent, this.hoverEvent, this.insertion, this.font);

        this.cache.put(textColor.value(), newStyle);

        return newStyle;
    }

    @Override
    public @NotNull Style colorIfAbsent(@Nullable TextColor c) {
        return this.color == null ? color(c) : this;
    }

    @Override
    public @Nullable ShadowColor shadowColor() {
        return this.shadowColor;
    }

    @Override
    public @NotNull Style shadowColor(@Nullable ARGBLike argb) {
        return Objects.equals(this.shadowColor, argb) ? this
                : new StyleImpl(this.color,
                argb == null ? null : ShadowColor.shadowColor(argb),
                this.decorations, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    @Override
    public @NotNull Style shadowColorIfAbsent(@Nullable ARGBLike argb) {
        return this.shadowColor == null ? shadowColor(argb) : this;
    }

    @Override
    public TextDecoration.@NotNull State decoration(@NotNull TextDecoration d) {
        return decode((this.decorations >> (d.ordinal() * BITS)));
    }

    @Override
    public @NotNull Style decoration(@NotNull TextDecoration d, TextDecoration.@NotNull State state) {
        final int shift = d.ordinal() * BITS;
        final short nd = (short) ((this.decorations & ~(MASK << shift)) | (encode(state) << shift));
        return new StyleImpl(this.color, this.shadowColor, nd,
                this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    @Override
    public @NotNull Style decorationIfAbsent(@NotNull TextDecoration decoration, TextDecoration.@NotNull State state) {
        return this.decoration(decoration) == TextDecoration.State.NOT_SET ? this.decoration(decoration, state) : this;
    }

    @Override
    public @NotNull Map<TextDecoration, TextDecoration.State> decorations() {
        final EnumMap<TextDecoration, TextDecoration.State> map = new EnumMap<>(TextDecoration.class);
        for (final TextDecoration d : TextDecoration.values()) map.put(d, decoration(d));
        return map;
    }

    @Override
    public @NotNull Style decorations(@NotNull Map<TextDecoration, TextDecoration.State> decorations) {
        return new StyleImpl(this.color, this.shadowColor, packDecorations(decorations),
                this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    @Override
    public @Nullable ClickEvent clickEvent() {
        return this.clickEvent;
    }

    @Override
    public @NotNull Style clickEvent(@Nullable ClickEvent event) {
        return new StyleImpl(this.color, this.shadowColor, this.decorations,
                event, this.hoverEvent, this.insertion, this.font);
    }

    @Override
    public @Nullable HoverEvent<?> hoverEvent() {
        return this.hoverEvent;
    }

    @Override
    public @NotNull Style hoverEvent(@Nullable HoverEventSource<?> source) {
        return new StyleImpl(this.color, this.shadowColor, this.decorations, this.clickEvent,
                source == null ? null : source.asHoverEvent(), this.insertion, this.font);
    }

    @Override
    public @Nullable String insertion() {
        return this.insertion;
    }

    @Override
    public @NotNull Style insertion(@Nullable String ins) {
        return new StyleImpl(this.color, this.shadowColor, this.decorations,
                this.clickEvent, this.hoverEvent, ins, this.font);
    }

    @Override
    public @Nullable Key font() {
        return this.font;
    }

    @Override
    public @NotNull Style font(@Nullable Key f) {
        return new StyleImpl(this.color, this.shadowColor, this.decorations,
                this.clickEvent, this.hoverEvent, this.insertion, f);
    }

    @Override
    public @NotNull Style merge(@NotNull Style that, Merge.@NotNull Strategy strategy, @NotNull Set<Merge> merges) {
        if (that.isEmpty() || Merge.Strategy.NEVER == strategy) return this;
        final Builder builder = this.toBuilder();
        builder.merge(that, strategy, merges);
        return builder.build();
    }

    @Override
    public @NotNull Style unmerge(@NotNull Style that) {
        final Builder builder = this.toBuilder();
        if (Objects.equals(this.color, that.color())) builder.color(null);
        if (Objects.equals(this.shadowColor, that.shadowColor())) builder.shadowColor(null);
        for (final TextDecoration decoration : TextDecoration.values())
            if (this.decoration(decoration) == that.decoration(decoration))
                builder.decoration(decoration, TextDecoration.State.NOT_SET);
        if (Objects.equals(this.clickEvent, that.clickEvent())) builder.clickEvent(null);
        if (Objects.equals(this.hoverEvent, that.hoverEvent())) builder.hoverEvent((HoverEventSource<?>) null);
        if (Objects.equals(this.insertion, that.insertion())) builder.insertion(null);
        if (Objects.equals(this.font, that.font())) builder.font(null);
        return builder.build();
    }

    @Override
    public boolean isEmpty() {
        return this.color == null && this.decorations == 0 && this.shadowColor == null
                && this.clickEvent == null && this.hoverEvent == null
                && this.insertion == null && this.font == null;
    }

    @Override
    public @NotNull Builder toBuilder() {
        final Builder b = Style.style();
        this.applyTo(b);
        return b;
    }

    /**
     * Copies all non-empty properties of this style into the given {@link Builder}.
     */
    void applyTo(@NotNull Builder builder) {
        if (this.color != null) builder.color(this.color);
        if (this.shadowColor != null) builder.shadowColor(this.shadowColor);
        for (TextDecoration d : TextDecoration.values()) {
            TextDecoration.State s = decoration(d);
            if (s != TextDecoration.State.NOT_SET) builder.decoration(d, s);
        }
        if (this.clickEvent != null) builder.clickEvent(this.clickEvent);
        if (this.hoverEvent != null) builder.hoverEvent(this.hoverEvent);
        if (this.insertion != null) builder.insertion(this.insertion);
        if (this.font != null) builder.font(this.font);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StyleImpl s)) return false;
        return this.decorations == s.decorations
                && Objects.equals(this.color, s.color)
                && Objects.equals(this.shadowColor, s.shadowColor)
                && Objects.equals(this.clickEvent, s.clickEvent)
                && Objects.equals(this.hoverEvent, s.hoverEvent)
                && Objects.equals(this.insertion, s.insertion)
                && Objects.equals(this.font, s.font);
    }

    @Override
    public int hashCode() {
        int r = Objects.hashCode(this.color);
        r = 31 * r + this.decorations;
        r = 31 * r + Objects.hashCode(this.shadowColor);
        r = 31 * r + Objects.hashCode(this.clickEvent);
        r = 31 * r + Objects.hashCode(this.hoverEvent);
        r = 31 * r + Objects.hashCode(this.insertion);
        r = 31 * r + Objects.hashCode(this.font);
        return r;
    }

    @Override
    public @NotNull Stream<? extends ExaminableProperty> examinableProperties() {
        return Stream.of(
                ExaminableProperty.of("color", this.color),
                ExaminableProperty.of("shadowColor", this.shadowColor),
                ExaminableProperty.of("decorations", this.decorations()),
                ExaminableProperty.of("clickEvent", this.clickEvent),
                ExaminableProperty.of("hoverEvent", this.hoverEvent),
                ExaminableProperty.of("insertion", this.insertion),
                ExaminableProperty.of("font", this.font));
    }
}
