package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.List;
import java.util.Objects;

/**
 * An immutable, compiled unit of formatted text that can render itself into a {@link Component}.
 *
 * <p>Tokens are produced exclusively by {@link Compiler} and fall into two broad categories:
 * <ul>
 *   <li><b>Static</b> — the Adventure component is materialized during compilation.</li>
 *   <li><b>Dynamic</b> — the result depends on placeholder values and is rebuilt on render.</li>
 * </ul>
 *
 * <p>The render contract receives one resolved component per compiled placeholder. Placeholder
 * components are inserted unchanged. Following static segments keep the template's compiled style
 * instead of inheriting style or events from the replacement component.
 * Dynamic command and URL values extract visible text recursively from their components.
 */
interface Token {

    Component render(Component[] placeholders);

    // ── Static tokens — Adventure components built during compilation ─────────

    /**
     * Plain text with a style. Example: {@code &cHello world}
     */
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
        public Component render(Component[] placeholders) {
            return this.component;
        }
    }

    /**
     * Text node with child tokens.
     * Cached only when all children are themselves static.
     */
    final class Children implements Token {

        private final Component staticComponent;

        Children(
                String text,
                Style style,
                Token[] children,
                int childCount
        ) {
            this.staticComponent = this.renderChildren(text, style, children, childCount);
        }

        @Override
        public Component render(Component[] placeholders) {
            return this.staticComponent;
        }

        private Component renderChildren(String text, Style style, Token[] children, int childCount) {
            final Component[] rendered = new Component[childCount];
            for (int index = 0; index < childCount; index++) {
                rendered[index] = children[index].render(null);
            }
            return textComponent(text, style, rendered);
        }
    }

    final class DynChildren implements Token {

        private final String text;
        private final Style style;
        private final Token[] children;
        private final int childCount;

        DynChildren(
                String text,
                Style style,
                Token[] children,
                int childCount
        ) {
            this.text = text;
            this.style = style;
            this.children = children;
            this.childCount = childCount;
        }

        @Override
        public Component render(Component[] placeholders) {
            return this.renderChildren(placeholders);
        }

        private Component renderChildren(Component[] placeholders) {
            final Component[] rendered = new Component[this.childCount];
            for (int index = 0; index < this.childCount; index++) {
                rendered[index] = this.children[index].render(placeholders);
            }
            return textComponent(this.text, this.style, rendered);
        }
    }

    // ── Dynamic tokens — rebuilt on every render() ──────────────────────────
    // Invariant: staticParts.length == phIndices.length + 1  (at most)

    /**
     * Text with placeholders and no events. Example: {@code &cHello, {player}!}
     *
     * <p>The first static part is stored in the root component. Each placeholder and its
     * immediately following static segment are emitted as siblings. Static segments remain
     * unstyled children and therefore inherit the compiled template style from their parent,
     * without inheriting replacement-component events or decorations.
     */
    final class PlainDyn implements Token {

        final String[] staticParts;
        final int[] phIndices;
        private final Style style;

        PlainDyn(String[] staticParts, int[] phIndices, Style style) {
            this.staticParts = staticParts;
            this.phIndices = phIndices;
            this.style = style;
        }

        @Override
        public Component render(Component[] compPh) {
            final String rootText = this.staticParts.length > 0 && this.staticParts[0] != null
                    ? this.staticParts[0] : "";
            final Component[] children = buildDynContent(this.staticParts, this.phIndices, compPh);
            return textComponent(rootText, this.style, children);
        }
    }

    final class DynamicHover {

        private final TextImpl text;
        private final int[] sourceIndices;

        DynamicHover(TextImpl text, int[] sourceIndices) {
            this.text = text;
            this.sourceIndices = sourceIndices;
        }

        HoverEvent<?> render(Component[] source) {
            return HoverEvent.showText(this.text.renderMapped(source, this.sourceIndices));
        }
    }

    /**
     * One dynamic action applied directly to the root component produced by
     * {@link #content}. The content already owns its visual style and any
     * static metadata; rendering replaces only the dynamic event.
     */
    final class MetaDyn implements Token {

        static final byte RUN = 0;
        static final byte SUGGEST = 1;
        static final byte URL = 2;
        static final byte COPY = 3;
        static final byte SHOW = 4;

        private final Token content;
        private final byte actionType;
        private final String[] metaStatic;
        private final int[] metaPh;
        private final DynamicHover dynamicHover;

        MetaDyn(
                Token content,
                byte actionType,
                String[] metaStatic,
                int[] metaPh,
                DynamicHover dynamicHover
        ) {
            this.content = content;
            this.actionType = actionType;
            this.metaStatic = metaStatic;
            this.metaPh = metaPh;
            this.dynamicHover = dynamicHover;
        }

        @Override
        public Component render(Component[] compPh) {
            final Component component = this.content.render(compPh);
            final Style baseStyle = component.style();

            ClickEvent click = baseStyle.clickEvent();
            HoverEvent<?> hover = baseStyle.hoverEvent();

            if (this.actionType == MetaDyn.SHOW) {
                hover = this.dynamicHover.render(compPh);
            } else {
                final String value = buildText(this.metaStatic, this.metaPh, compPh);
                click = MetaFullDyn.buildClick(this.actionType, value);
            }

            final Style style = withEvents(baseStyle, click, hover);
            return style == baseStyle ? component : component.style(style);
        }
    }

    /**
     * Dynamic text content combined with dynamic action(s).
     */
    final class MetaFullDyn implements Token {

        private final Token content;
        private final byte actionType;
        private final String[] metaStatic;
        private final int[] metaPh;
        private final DynamicHover dynamicHover;
        private final byte action2Type;
        private final String[] meta2Static;
        private final int[] meta2Ph;
        private final DynamicHover dynamicHover2;

        MetaFullDyn(
                Token content,
                byte actionType,
                String[] metaStatic,
                int[] metaPh,
                DynamicHover dynamicHover,
                byte action2Type,
                String[] meta2Static,
                int[] meta2Ph,
                DynamicHover dynamicHover2
        ) {
            this.content = content;
            this.actionType = actionType;
            this.metaStatic = metaStatic;
            this.metaPh = metaPh;
            this.dynamicHover = dynamicHover;
            this.action2Type = action2Type;
            this.meta2Static = meta2Static;
            this.meta2Ph = meta2Ph;
            this.dynamicHover2 = dynamicHover2;
        }

        @Override
        public Component render(Component[] compPh) {
            final Component component = this.content.render(compPh);
            final Style baseStyle = component.style();

            ClickEvent click = baseStyle.clickEvent();
            HoverEvent<?> hover = baseStyle.hoverEvent();

            if (this.actionType == MetaDyn.SHOW) {
                hover = this.dynamicHover.render(compPh);
            } else {
                final String value = buildText(this.metaStatic, this.metaPh, compPh);
                click = MetaFullDyn.buildClick(this.actionType, value);
            }

            if (this.action2Type != -1) {
                if (this.action2Type == MetaDyn.SHOW) {
                    hover = this.dynamicHover2.render(compPh);
                } else {
                    final String value2 = buildText(this.meta2Static, this.meta2Ph, compPh);
                    click = MetaFullDyn.buildClick(this.action2Type, value2);
                }
            }

            final Style style = withEvents(baseStyle, click, hover);
            return style == baseStyle ? component : component.style(style);
        }

        static ClickEvent buildClick(byte type, String value) {
            return switch (type) {
                case MetaDyn.RUN -> ClickEvent.runCommand(value);
                case MetaDyn.SUGGEST -> ClickEvent.suggestCommand(value);
                case MetaDyn.URL -> ClickEvent.openUrl(value);
                case MetaDyn.COPY -> ClickEvent.copyToClipboard(value);
                default -> throw new IllegalStateException("Not a click action: " + type);
            };
        }
    }

    // ── Gradient tokens ─────────────────────────────────────────────────────

    final class GradientContent implements Token {

        private final Token content;
        private final int[] colors;
        private final Style baseStyle;
        final boolean dynamic;
        private final Component staticComponent;
        private volatile Style[] styleCache0;
        private volatile Style[] styleCache1;
        private volatile Style[] styleCache2;
        private volatile Style[] styleCache3;

        GradientContent(Token content, int[] colors, Style baseStyle, boolean dynamic) {
            this.content = content;
            this.colors = colors;
            this.baseStyle = baseStyle;
            this.dynamic = dynamic;
            this.staticComponent = dynamic ? null : this.renderGradient(null);
        }

        @Override
        public Component render(Component[] placeholders) {
            return this.dynamic ? this.renderGradient(placeholders) : this.staticComponent;
        }

        private Component renderGradient(Component[] placeholders) {
            final Component rendered = this.content.render(placeholders);
            final int codePointCount = countCodePoints(rendered);

            final Style[] styles = this.styles(codePointCount);

            final Component colored;
            if (codePointCount == 0) {
                colored = rendered;
            } else {
                final int[] styleIndex = {0};
                colored = applyGradient(rendered, styles, styleIndex);
            }
            return textComponent("", this.baseStyle, new Component[]{colored});
        }

        private Style[] styles(int length) {
            final int slot = (length ^ (length >>> 2)) & 3;
            Style[] styles = switch (slot) {
                case 0 -> this.styleCache0;
                case 1 -> this.styleCache1;
                case 2 -> this.styleCache2;
                default -> this.styleCache3;
            };

            if (styles != null && styles.length == length) return styles;

            styles = gradientStyles(length, this.colors);
            switch (slot) {
                case 0 -> this.styleCache0 = styles;
                case 1 -> this.styleCache1 = styles;
                case 2 -> this.styleCache2 = styles;
                default -> this.styleCache3 = styles;
            }
            return styles;
        }
    }

    // ── Object tokens (head / sprite, Minecraft 1.21.9+) ───────────────────

    final class Obj implements Token {

        private final Component component;

        Obj(ObjectContents contents, Style style) {
            final Component component = Component.object(contents);
            this.component = style.isEmpty() ? component : component.style(style);
        }

        @Override
        public Component render(Component[] placeholders) {
            return this.component;
        }
    }

    final class ObjDyn implements Token {

        private final String[] staticParts;
        private final int[] placeholderIndices;
        private final Style style;

        ObjDyn(String[] staticParts, int[] placeholderIndices, Style style) {
            this.staticParts = staticParts;
            this.placeholderIndices = placeholderIndices;
            this.style = style;
        }

        @Override
        public Component render(Component[] placeholders) {
            final String profileName = buildText(
                    this.staticParts,
                    this.placeholderIndices,
                    placeholders
            );
            final Component component = Component.object(ObjectContents.playerHead(profileName));
            return this.style.isEmpty() ? component : component.style(this.style);
        }
    }

    // ── Shared utilities ────────────────────────────────────────────────────

    /**
     * Per-thread {@link StringBuilder} reused across all {@link #buildText} calls.
     */
    int MAX_RETAINED_BUILDER_CAPACITY = 8 * 1024;
    ThreadLocal<StringBuilder> TL_SB = ThreadLocal.withInitial(() -> new StringBuilder(128));
    Component[] EMPTY_COMPONENTS = new Component[0];
    Style[] EMPTY_STYLES = new Style[0];

    /**
     * Cached single-character strings for codepoints 0–255.
     */
    String[] CHAR_CACHE = buildCharCache();

    private static String[] buildCharCache() {
        final String[] cache = new String[256];
        for (int i = 0; i < 256; i++) cache[i] = String.valueOf((char) i);
        return cache;
    }

    String[] NO_PARTS = {};
    int[] NO_PH = {};

    /**
     * Interleaves static string parts with the plain-text content extracted from placeholder
     * components, and returns the assembled string.
     *
     * <p>Used exclusively for action-value building (commands, URLs, hover text templates)
     * where a raw string is required. Style information in the component is intentionally
     * discarded — only the visible text content matters for action strings.
     *
     * @param staticParts        static parts ({@code s[i]} is the literal before placeholder {@code i})
     * @param placeholderIndices placeholder indices into {@code compPh}
     * @param placeholders       resolved placeholder components
     */
    private static String buildText(
            String[] staticParts,
            int[] placeholderIndices,
            Component[] placeholders
    ) {
        if (staticParts.length == 0 && placeholderIndices.length == 1) {
            final Component component = placeholders[placeholderIndices[0]];
            if (component instanceof TextComponent textComponent && component.children().isEmpty()) {
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
                appendText(builder, placeholders[placeholderIndices[index]]);

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

    private static String extractText(Component component) {
        if (component == null) return "";

        final StringBuilder builder = TL_SB.get();
        builder.setLength(0);
        try {
            appendText(builder, component);
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

    /**
     * Emits placeholders and following static segments as siblings. Static segments are left
     * unstyled so they inherit only the enclosing template style, not the replacement component's
     * style, events, or child list.
     */
    private static Component[] buildDynContent(
            String[] staticParts,
            int[] placeholderIndices,
            Component[] placeholders
    ) {
        final int placeholderCount = placeholderIndices.length;
        if (placeholderCount == 0) return EMPTY_COMPONENTS;

        int childCount = placeholderCount;
        for (int index = 0; index < placeholderCount; index++) {
            final int staticIndex = index + 1;
            if (staticIndex < staticParts.length
                    && staticParts[staticIndex] != null
                    && !staticParts[staticIndex].isEmpty()) {
                childCount++;
            }
        }

        final Component[] children = new Component[childCount];
        int outputIndex = 0;
        for (int index = 0; index < placeholderCount; index++) {
            final Component placeholder = placeholders[placeholderIndices[index]];
            children[outputIndex++] = placeholder;

            final int staticIndex = index + 1;
            if (staticIndex < staticParts.length) {
                final String staticText = staticParts[staticIndex];
                if (staticText != null && !staticText.isEmpty()) {
                    children[outputIndex++] = Component.text(staticText);
                }
            }
        }

        return children;
    }

    private static Style withEvents(
            Style baseStyle,
            ClickEvent clickEvent,
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
            Component[] children
    ) {
        final TextComponent component = Component.text(content, style);
        return children.length == 0
                ? component
                : component.children(java.util.Arrays.asList(children));
    }

    private static Style[] gradientStyles(int length, int[] colors) {
        if (length == 0) return EMPTY_STYLES;

        final Style[] styles = new Style[length];
        for (int index = 0; index < length; index++) {
            final float position = length == 1 ? 0f : (float) index / (length - 1);
            final int rgb = StyleUtils.interpolate(colors, position);
            styles[index] = Style.style(StyleUtils.textColorOf(rgb));
        }
        return styles;
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

    private static Component applyGradient(
            Component component,
            Style[] styles,
            int[] styleIndex
    ) {
        final List<Component> originalChildren = component.children();
        final int textLength;
        final String text;
        if (component instanceof TextComponent textComponent) {
            text = textComponent.content();
            textLength = text.codePointCount(0, text.length());
        } else {
            text = null;
            textLength = 0;
        }

        if (textLength == 0 && originalChildren.isEmpty()) return component;

        final Component[] children = new Component[textLength + originalChildren.size()];
        int outputIndex = 0;
        if (text != null) {
            int offset = 0;
            for (int index = 0; index < textLength; index++) {
                final int codePoint = text.codePointAt(offset);
                final String value = codePoint < CHAR_CACHE.length
                        ? CHAR_CACHE[codePoint]
                        : Character.toString(codePoint);
                children[outputIndex++] = Component.text(value, styles[styleIndex[0]++]);
                offset += Character.charCount(codePoint);
            }
        }

        for (int index = 0, size = originalChildren.size(); index < size; index++) {
            children[outputIndex++] = applyGradient(
                    originalChildren.get(index),
                    styles,
                    styleIndex
            );
        }

        if (text != null) {
            return textComponent("", component.style(), children);
        }
        return component.children(java.util.Arrays.asList(children));
    }
}
