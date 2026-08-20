package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.Arrays;

/**
 * An immutable, compiled unit of formatted text that can render itself into a {@link Component}.
 *
 * <p>Tokens are produced exclusively by {@link Compiler} and fall into two broad categories:
 * <ul>
 *   <li><b>Static</b> — result is computed once and cached on the first {@link #render} call.</li>
 *   <li><b>Dynamic</b> — result depends on placeholder values and is rebuilt on every call.</li>
 * </ul>
 *
 * <p>The render contract uses a single {@code Component[] compPh} array:
 * <ul>
 *   <li>Each placeholder becomes the <em>parent</em> of the immediately following static text
 *       segment, so the placeholder's color and decorations are inherited by that text unless
 *       overridden.</li>
 *   <li>For action strings (commands, URLs, hover text) the raw text content is extracted from
 *       the component recursively via {@link #extractText}.</li>
 * </ul>
 * The array is {@code null} when the token is known to be fully static.
 */
interface Token {

    Component render(Component[] compPh);

    // ── Static tokens — Component cached on first render() ──────────────────

    /**
     * Plain text with a style. Example: {@code &cHello world}
     */
    final class Plain implements Token {

        final String text;
        final Style style;
        private Component cached;

        Plain(String text, Style style) {
            this.text = text;
            this.style = style;
        }

        @Override
        public Component render(Component[] compPh) {
            if (this.cached != null) return this.cached;
            return this.cached = Component.text(this.text, this.style);
        }
    }

    /**
     * Plain text with static click/hover events pre-baked into the style.
     * Example: {@code [Click](run:"/spawn", show:"Go!")}
     */
    final class Meta implements Token {

        private final String text;
        private final Style style;
        private Component cached;

        Meta(String text, Style style) {
            this.text = text;
            this.style = style;
        }

        @Override
        public Component render(Component[] compPh) {
            if (this.cached != null) return this.cached;
            return this.cached = Component.text(this.text, this.style);
        }
    }

    /**
     * Text node with child tokens.
     * Cached only when all children are themselves static.
     */
    final class Children implements Token {

        private final String text;
        private final Style style;
        private final Token[] children;
        private final int childCnt;
        final boolean hasDynChild;
        private Component cached;

        Children(String text, Style style,
                 Token[] children, int childCnt, boolean hasDynChild) {
            this.text = text;
            this.style = style;
            this.children = children;
            this.childCnt = childCnt;
            this.hasDynChild = hasDynChild;
        }

        @Override
        public Component render(Component[] compPh) {
            if (!this.hasDynChild && this.cached != null) return this.cached;

            final Component[] rendered = new Component[this.childCnt];
            for (int i = 0; i < this.childCnt; i++) {
                rendered[i] = this.children[i].render(compPh);
            }

            final Component result = text(this.text, this.style, rendered);
            if (!this.hasDynChild) {
                this.cached = result;
            }
            return result;
        }
    }

    // ── Dynamic tokens — rebuilt on every render() ──────────────────────────
    // Invariant: staticParts.length == phIndices.length + 1  (at most)

    /**
     * Text with placeholders and no events. Example: {@code &cHello, {player}!}
     *
     * <p>Each placeholder component becomes the <em>parent</em> of the static text segment
     * that follows it, so the placeholder's style (color, decorations) is inherited by that
     * segment unless it overrides them.
     *
     * <p>Layout:
     * <pre>
     *   staticParts[0]            ← root text content of the styled wrapper
     *   phIndices[0] → compPh[i]  ← placeholder component (parent)
     *     └─ staticParts[1]       ← child of the placeholder (inherits its style)
     *   phIndices[1] → compPh[j]
     *     └─ staticParts[2]
     *   …
     * </pre>
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

            return text(rootText, this.style, buildDynContent(this.staticParts, this.phIndices, compPh));
        }
    }

    /**
     * Text with placeholders and static (pre-built) click/hover events.
     * Example: {@code [Hello {player}](run:"/spawn")}
     */
    final class MetaDynContent implements Token {

        private final String[] staticParts;
        private final int[] phIndices;
        private final Style style;

        MetaDynContent(String[] staticParts, int[] phIndices, Style style) {
            this.staticParts = staticParts;
            this.phIndices = phIndices;
            this.style = style;
        }

        @Override
        public Component render(Component[] compPh) {
            final String rootText = this.staticParts.length > 0 && this.staticParts[0] != null
                    ? this.staticParts[0] : "";

            return text(rootText, this.style, buildDynContent(this.staticParts, this.phIndices, compPh));
        }
    }

    /**
     * Static text with one dynamic action (a placeholder inside a command, URL, etc.).
     * Optionally has child tokens.
     */
    final class MetaDyn implements Token {

        static final byte RUN = 0;
        static final byte SUGGEST = 1;
        static final byte URL = 2;
        static final byte COPY = 3;
        static final byte SHOW = 4;

        private final String text;
        private final short deco;
        private final TextColor color;
        private final ShadowColor shadow;
        private final byte actionType;
        private final String[] metaStatic;
        private final int[] metaPh;
        private final HoverEvent<?> staticHover;
        private final ClickEvent<?> staticClick;
        private final Token[] children;
        final int childCnt;
        final boolean hasDynChild;

        MetaDyn(String text, short deco, TextColor color, ShadowColor shadow,
                byte actionType, String[] metaStatic, int[] metaPh,
                HoverEvent<?> staticHover, ClickEvent<?> staticClick) {
            this(text, deco, color, shadow, actionType, metaStatic, metaPh,
                    staticHover, staticClick, null, 0, false);
        }

        MetaDyn(String text, short deco, TextColor color, ShadowColor shadow,
                byte actionType, String[] metaStatic, int[] metaPh,
                HoverEvent<?> staticHover, ClickEvent<?> staticClick,
                Token[] children, int childCnt, boolean hasDynChild) {
            this.text = text;
            this.deco = deco;
            this.color = color;
            this.shadow = shadow;
            this.actionType = actionType;
            this.metaStatic = metaStatic;
            this.metaPh = metaPh;
            this.staticHover = staticHover;
            this.staticClick = staticClick;
            this.children = children;
            this.childCnt = childCnt;
            this.hasDynChild = hasDynChild;
        }

        @Override
        public Component render(Component[] compPh) {
            // Action values always require a plain string — extract text from the component
            final String value = buildText(this.metaStatic, this.metaPh, compPh);

            ClickEvent<?> click = this.staticClick;
            HoverEvent<?> hover = this.staticHover;
            switch (this.actionType) {
                case RUN -> click = ClickEvent.runCommand(value);
                case SUGGEST -> click = ClickEvent.suggestCommand(value);
                case URL -> click = ClickEvent.openUrl(value);
                case COPY -> click = ClickEvent.copyToClipboard(value);
                case SHOW -> hover = HoverEvent.showText(Text.of(value).render());
            }

            final Style style = Compiler.style(
                    this.color, this.shadow, this.deco, click, hover, null);

            if (this.childCnt > 0) {
                final Component[] rendered = new Component[this.childCnt];
                for (int i = 0; i < this.childCnt; i++) {
                    rendered[i] = this.children[i].render(compPh);
                }

                return text(this.text, style, rendered);
            }

            return Component.text(this.text, style);
        }
    }

    /**
     * Dynamic text content combined with dynamic action(s).
     */
    final class MetaFullDyn implements Token {

        private final String[] staticParts;
        private final int[] phIndices;
        private final short deco;
        private final TextColor color;
        private final ShadowColor shadow;
        private final byte actionType;
        private final String[] metaStatic;
        private final int[] metaPh;
        private final byte action2Type;
        private final String[] meta2Static;
        private final int[] meta2Ph;
        private final Token[] children;
        private final int childCnt;
        private final boolean hasDynChild;

        MetaFullDyn(String[] staticParts, int[] phIndices,
                    short deco, TextColor color, ShadowColor shadow,
                    byte actionType, String[] metaStatic, int[] metaPh,
                    byte action2Type, String[] meta2Static, int[] meta2Ph,
                    Token[] children, int childCnt, boolean hasDynChild) {
            this.staticParts = staticParts;
            this.phIndices = phIndices;
            this.deco = deco;
            this.color = color;
            this.shadow = shadow;
            this.actionType = actionType;
            this.metaStatic = metaStatic;
            this.metaPh = metaPh;
            this.action2Type = action2Type;
            this.meta2Static = meta2Static;
            this.meta2Ph = meta2Ph;
            this.children = children;
            this.childCnt = childCnt;
            this.hasDynChild = hasDynChild;
        }

        @Override
        public Component render(Component[] compPh) {
            // buildText reuses TL_SB; each call returns a String copy, so sequential reuse is safe
            final String val1 = buildText(this.metaStatic, this.metaPh, compPh);

            ClickEvent<?> click = buildClick(this.actionType, val1);
            HoverEvent<?> hover = this.actionType == MetaDyn.SHOW
                    ? HoverEvent.showText(Text.of(val1).render()) : null;

            if (this.meta2Static != null) {
                final String val2 = buildText(this.meta2Static, this.meta2Ph, compPh);

                if (this.action2Type == MetaDyn.SHOW) {
                    hover = HoverEvent.showText(Text.of(val2).render());
                } else {
                    click = buildClick(this.action2Type, val2);
                }
            }

            final Style style = Compiler.style(
                    this.color, this.shadow, this.deco, click, hover, null);

            final String rootText = this.staticParts.length > 0 && this.staticParts[0] != null
                    ? this.staticParts[0] : "";

            final Component[] dynChildren =
                    buildDynContent(this.staticParts, this.phIndices, compPh);

            if (dynChildren.length == 0 && this.childCnt == 0) {
                return Component.text(rootText, style);
            }

            final Component[] allChildren = new Component[dynChildren.length + this.childCnt];
            System.arraycopy(dynChildren, 0, allChildren, 0, dynChildren.length);

            for (int i = 0; i < this.childCnt; i++) {
                allChildren[dynChildren.length + i] = this.children[i].render(compPh);
            }

            return text(rootText, style, allChildren);
        }

        static ClickEvent<?> buildClick(byte type, String value) {
            return switch (type) {
                case MetaDyn.RUN -> ClickEvent.runCommand(value);
                case MetaDyn.SUGGEST -> ClickEvent.suggestCommand(value);
                case MetaDyn.URL -> ClickEvent.openUrl(value);
                case MetaDyn.COPY -> ClickEvent.copyToClipboard(value);
                default -> null;
            };
        }
    }

    // ── Gradient tokens ─────────────────────────────────────────────────────

    final class Gradient implements Token {

        private final String text;
        private final int[] colors;
        private final short deco;
        private final ShadowColor shadow;
        private final ClickEvent<?> clickEvent;
        private final HoverEvent<?> hoverEvent;
        private Component cached;

        Gradient(String text, int[] colors, short deco, ShadowColor shadow,
                 ClickEvent<?> clickEvent, HoverEvent<?> hoverEvent) {
            this.text = text;
            this.colors = colors;
            this.deco = deco;
            this.shadow = shadow;
            this.clickEvent = clickEvent;
            this.hoverEvent = hoverEvent;
        }

        @Override
        public Component render(Component[] compPh) {
            if (this.cached != null) return this.cached;
            return this.cached = buildGradient(
                    this.text, this.colors, this.deco, this.shadow,
                    this.clickEvent, this.hoverEvent);
        }
    }

    final class GradientDyn implements Token {

        private final String[] staticParts;
        private final int[] phIndices;
        private final int[] colors;
        private final short deco;
        private final ShadowColor shadow;
        private final ClickEvent<?> clickEvent;
        private final HoverEvent<?> hoverEvent;

        GradientDyn(String[] staticParts, int[] phIndices, int[] colors,
                    short deco, ShadowColor shadow,
                    ClickEvent<?> clickEvent, HoverEvent<?> hoverEvent) {
            this.staticParts = staticParts;
            this.phIndices = phIndices;
            this.colors = colors;
            this.deco = deco;
            this.shadow = shadow;
            this.clickEvent = clickEvent;
            this.hoverEvent = hoverEvent;
        }

        @Override
        public Component render(Component[] compPh) {
            // Gradient is applied per-character — needs an assembled plain string
            return buildGradient(
                    buildText(this.staticParts, this.phIndices, compPh),
                    this.colors, this.deco, this.shadow,
                    this.clickEvent, this.hoverEvent);
        }
    }

    // ── Object tokens (head / sprite, 1.21.9+) ──────────────────────────────

    final class Obj implements Token {

        private final ObjectContents contents;
        private final Style style;
        private Component cached;

        Obj(ObjectContents contents, Style style) {
            this.contents = contents;
            this.style = style;
        }

        @Override
        public Component render(Component[] compPh) {
            if (this.cached != null) return this.cached;
            return this.cached = Component.object(this.contents).style(this.style);
        }
    }

    final class ObjDyn implements Token {

        private final int phIndex;
        private final Style style;

        ObjDyn(int phIndex, Style style) {
            this.phIndex = phIndex;
            this.style = style;
        }

        @Override
        public Component render(Component[] compPh) {
            // Player head name must be a resource-location string — extract plain text
            return Component.object(
                    ObjectContents.playerHead(extractText(compPh[this.phIndex]))
            ).style(this.style);
        }
    }

    // ── Shared utilities ────────────────────────────────────────────────────

    Component[] EMPTY_COMPONENTS = new Component[0];

    /**
     * Creates a text component and attaches children only when necessary.
     */
    private static Component text(String content, Style style, Component[] children) {
        if (children.length == 0) {
            return Component.text(content, style);
        }

        return Component.text(content, style).children(Arrays.asList(children));
    }

    /**
     * Per-thread {@link StringBuilder} reused across all {@link #buildText} calls.
     */
    ThreadLocal<StringBuilder> TL_SB = ThreadLocal.withInitial(() -> new StringBuilder(128));

    /**
     * Cached single-character strings for ASCII codepoints 0–127.
     */
    String[] CHAR_CACHE = buildCharCache();

    private static String[] buildCharCache() {
        final String[] cache = new String[128];

        for (int i = 0; i < 128; i++) {
            cache[i] = String.valueOf((char) i);
        }

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
     * @param s      static parts ({@code s[i]} is the literal before placeholder {@code i})
     * @param idx    placeholder indices into {@code compPh}
     * @param compPh resolved placeholder components
     */
    private static String buildText(String[] s, int[] idx, Component[] compPh) {
        final StringBuilder sb = TL_SB.get();
        sb.setLength(0);

        if (s.length > 0 && s[0] != null) {
            sb.append(s[0]);
        }

        for (int i = 0; i < idx.length; i++) {
            sb.append(extractText(compPh[idx[i]]));
            final int j = i + 1;
            if (j < s.length && s[j] != null) {
                sb.append(s[j]);
            }
        }

        return sb.toString();
    }

    /**
     * Recursively extracts the plain text content of a {@link Component}, depth-first,
     * discarding all style information. Used to obtain a raw string from a placeholder
     * component for use in action values (commands, URLs).
     */
    private static String extractText(Component component) {
        if (component == null) {
            return "";
        }

        final StringBuilder sb = TL_SB.get();
        final int mark = sb.length();
        appendText(sb, component);
        final String result = sb.substring(mark);
        sb.setLength(mark);
        return result;
    }

    private static void appendText(StringBuilder sb, Component component) {
        if (component instanceof TextComponent tc) {
            sb.append(tc.content());
        }

        for (final Component child : component.children()) {
            appendText(sb, child);
        }
    }

    /**
     * Builds the child {@link Component} array for a dynamic text node using the
     * <em>placeholder-as-parent</em> model:
     *
     * <ul>
     *   <li>Each placeholder component from {@code compPh[phIndices[i]]} is emitted as-is
     *       when no static text follows it, or wrapped around the following static text
     *       segment as a child — making that segment inherit the placeholder's color and
     *       decorations.</li>
     * </ul>
     *
     * <p>The first static segment ({@code staticParts[0]}) is the root text of the
     * surrounding styled wrapper and is <strong>not</strong> included in the returned array.
     *
     * @param staticParts literal segments: {@code [0]} is the root text, {@code [i+1]}
     *                    follows placeholder {@code i}
     * @param phIndices   indices into {@code compPh}
     * @param compPh      resolved placeholder components
     * @return children array (never null; empty when {@code phIndices} is empty)
     */
    private static Component[] buildDynContent(
            String[] staticParts, int[] phIndices, Component[] compPh) {
        final int n = phIndices.length;

        if (n == 0) {
            return EMPTY_COMPONENTS;
        }

        final Component[] result = new Component[n];
        for (int i = 0; i < n; i++) {
            final Component ph = compPh[phIndices[i]];
            final int j = i + 1;
            if (j < staticParts.length && staticParts[j] != null && !staticParts[j].isEmpty()) {
                // Attach the following static text as a child of the placeholder component,
                // so it inherits the placeholder's color and decorations.
                result[i] = appendStaticChild(ph, staticParts[j]);
            } else {
                result[i] = ph;
            }
        }
        return result;
    }

    /**
     * Returns a copy of {@code parent} with {@code text} appended as a plain child.
     *
     * <p>For {@link TextComponent}s whose children array is currently empty this avoids an
     * intermediate list allocation by constructing the result directly.
     */
    private static Component appendStaticChild(Component parent, String text) {
        return parent.append(Component.text(text));
    }

    /**
     * Builds a gradient {@link Component} by assigning an interpolated color to each character.
     */
    private static Component buildGradient(
            String text, int[] colors, short deco, ShadowColor shadow,
            ClickEvent<?> clickEvent, HoverEvent<?> hoverEvent
    ) {
        final int len = text.length();

        if (len == 0) {
            return Component.empty();
        }

        final Component[] chars = new Component[len];
        for (int i = 0; i < len; i++) {
            final float t = len == 1 ? 0f : (float) i / (len - 1);
            final int rgb = ColorUtils.interpolate(colors, t);

            final Style style = Compiler.style(
                    ColorUtils.textColorOf(rgb), shadow, deco,
                    clickEvent, hoverEvent, null
            );
            final char ch = text.charAt(i);
            final String charStr = ch < 128 ? CHAR_CACHE[ch] : String.valueOf(ch);

            chars[i] = Component.text(charStr, style);
        }

        return Component.text("", Style.empty()).children(Arrays.asList(chars));
    }
}
