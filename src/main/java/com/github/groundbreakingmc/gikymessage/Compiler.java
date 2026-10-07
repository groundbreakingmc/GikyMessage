package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Compiles raw format strings into {@link Token} trees consumed by {@link TextImpl}.
 * <p>
 * <h3>Format syntax</h3>
 * <ul>
 *   <li>{@code &c}, {@code &l}, … — legacy Minecraft color/decoration codes</li>
 *   <li>{@code &#RRGGBB} or {@code &#RGB} — hex color</li>
 *   <li>{@code $#RRGGBB} — shadow color</li>
 *   <li>{@code &r} — reset all styles</li>
 *   <li>{@code {key}} — placeholder</li>
 *   <li>{@code [text](action:value, …)} — bracketed segment with optional actions
 *       ({@code run}, {@code suggest}, {@code url}, {@code copy}, {@code show},
 *        {@code page}, {@code insert}, {@code gradient}, {@code color}, {@code style}, {@code head}, {@code sprite})</li>
 *   <li>{@code \n}, {@code \[}, {@code \]}, {@code \{}, {@code \}}, {@code \\} — escapes</li>
 * </ul>
 */
final class Compiler {

    private static final int INITIAL_PLACEHOLDER_CAPACITY = 16;
    private static final int PLACEHOLDER_HASH_THRESHOLD = 32;
    private static final int MAX_RETAINED_SCRATCH_CAPACITY = 8 * 1024;
    private static final int INITIAL_STACK_CAPACITY = 8;

    /**
     * Per-thread scratch buffer for quoted value and action parsing.
     */
    private static final ThreadLocal<StringBuilder> SCRATCH_BUFFER =
            ThreadLocal.withInitial(() -> new StringBuilder(64));

    private static final byte ACTION_UNKNOWN = 0;
    private static final byte ACTION_RUN = 1;
    private static final byte ACTION_SUGGEST = 2;
    private static final byte ACTION_URL = 3;
    private static final byte ACTION_COPY = 4;
    private static final byte ACTION_INSERT = 5;
    private static final byte ACTION_SHOW = 6;
    private static final byte ACTION_HEAD = 7;
    private static final byte ACTION_SPRITE = 8;
    private static final byte ACTION_PAGE = 9;
    private static final byte ACTION_GRADIENT = 10;
    private static final byte ACTION_COLOR = 11;
    private static final byte ACTION_STYLE = 12;

    private Compiler() {
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Entry point
    // ════════════════════════════════════════════════════════════════════════

    static Text compile(String raw, boolean cacheable) {
        if (raw == null || raw.isEmpty()) return Text.EMPTY;
        if (isPlainText(raw)) {
            return new TextImpl(new Token.Plain(raw, Style.empty()), Token.NO_PARTS, cacheable);
        }

        final PlaceholderTable placeholders = new PlaceholderTable();
        final Token root = compileToken(raw, placeholders);
        return new TextImpl(root, placeholders.toArray(), cacheable);
    }

    private static boolean isPlainText(String raw) {
        for (int index = 0; index < raw.length(); index++) {
            switch (raw.charAt(index)) {
                case '&', '$', '[', '{', '\\' -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static Token compileToken(String raw, PlaceholderTable placeholders) {
        if (raw.isEmpty()) return new Token.Plain("", Style.empty());

        final char[] src = raw.toCharArray();
        final int len = src.length;

        short curDeco = 0;
        TextColor curColor = null;
        ShadowColor curShadow = null;

        Frame[] stack = null;
        int depth = 0;

        short inhDeco = 0;
        TextColor inhColor = null;
        ShadowColor inhShadow = null;

        final StringBuilder textBuf = new StringBuilder();
        Token[] rootTokens = new Token[8];
        int rootTokenCnt = 0;

        // The first plain token can normally be stored directly in the root
        // component. A later root-level &r makes that unsafe: Adventure would
        // have to emit explicit negative decorations / white to cancel the
        // root style. Decide lazily when the first styled root token is flushed.
        boolean canHoistRootStyle = true;

        String[] dynStatic = null;
        int[] dynPh = null;
        int dynPhCnt = 0;
        boolean hasDyn = false;

        int pos = 0;
        boolean noMorePlaceholders = false;

        // ════════════════════════════════════════════════════════════════════
        //  Main loop
        // ════════════════════════════════════════════════════════════════════
        while (pos < len) {
            final char c = src[pos];

            // ── Escape sequences ─────────────────────────────────────────────
            if (c == '\\' && pos + 1 < len) {
                switch (src[pos + 1]) {
                    case 'n' -> {
                        textBuf.append('\n');
                        pos += 2;
                        continue;
                    }
                    case '[' -> {
                        textBuf.append('[');
                        pos += 2;
                        continue;
                    }
                    case ']' -> {
                        textBuf.append(']');
                        pos += 2;
                        continue;
                    }
                    case '{' -> {
                        textBuf.append('{');
                        pos += 2;
                        continue;
                    }
                    case '}' -> {
                        textBuf.append('}');
                        pos += 2;
                        continue;
                    }
                    case '\\' -> {
                        textBuf.append('\\');
                        pos += 2;
                        continue;
                    }
                }
                textBuf.append(c);
                pos++;
                continue;
            }

            // ── Color / decoration codes: & ──────────────────────────────────
            if (c == '&' && pos + 1 < len) {
                final char next = src[pos + 1];
                final char code = Character.toLowerCase(next);
                final TextColor legacyColor = StyleUtils.fromLegacyCode(next);
                final long packedColor = next == '#'
                        ? StyleUtils.parseHexPacked(src, pos + 1)
                        : -1L;

                // Determine whether this is a valid style-changing code before
                // touching any state. If it is, flush any accumulated text first
                // so it gets the OLD style, not the incoming one.
                final boolean willChangeColor = legacyColor != null || packedColor != -1L;
                final boolean willChangeDeco = code == 'l' || code == 'o' || code == 'n'
                        || code == 'm' || code == 'k' || code == 'r';

                if (willChangeColor || willChangeDeco) {
                    final Token styleFlush = flushText(textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                            curDeco, curColor, curShadow, inhDeco, inhColor, inhShadow);
                    if (styleFlush != null) {
                        if (depth == 0) {
                            if (rootTokenCnt == 0 && styleFlush instanceof Token.Plain) {
                                if (curDeco != 0 || curColor != null || curShadow != null) {
                                    canHoistRootStyle = !hasRootResetAhead(src, pos);
                                }
                                if (canHoistRootStyle) {
                                    inhDeco = curDeco;
                                    inhColor = curColor;
                                    inhShadow = curShadow;
                                }
                            }
                            rootTokens = pushToken(rootTokens, rootTokenCnt++, styleFlush);
                        } else {
                            stack[depth - 1].add(styleFlush);
                        }
                    }
                    textBuf.setLength(0);
                    dynPhCnt = 0;
                    hasDyn = false;
                }

                if (packedColor != -1L) {
                    curColor = StyleUtils.textColorOf((int) packedColor);
                    pos += 1 + (int) (packedColor >>> 32);
                    continue;
                }

                if (legacyColor != null) {
                    curColor = legacyColor;
                    pos += 2;
                    continue;
                }

                switch (code) {
                    case 'l' -> {
                        curDeco = StyleUtils.withDecoration(curDeco, TextDecoration.BOLD, StyleUtils.BIT_TRUE);
                        pos += 2;
                        continue;
                    }
                    case 'o' -> {
                        curDeco = StyleUtils.withDecoration(curDeco, TextDecoration.ITALIC, StyleUtils.BIT_TRUE);
                        pos += 2;
                        continue;
                    }
                    case 'n' -> {
                        curDeco = StyleUtils.withDecoration(curDeco, TextDecoration.UNDERLINED, StyleUtils.BIT_TRUE);
                        pos += 2;
                        continue;
                    }
                    case 'm' -> {
                        curDeco = StyleUtils.withDecoration(curDeco, TextDecoration.STRIKETHROUGH, StyleUtils.BIT_TRUE);
                        pos += 2;
                        continue;
                    }
                    case 'k' -> {
                        curDeco = StyleUtils.withDecoration(curDeco, TextDecoration.OBFUSCATED, StyleUtils.BIT_TRUE);
                        pos += 2;
                        continue;
                    }
                    case 'r' -> {
                        curDeco = StyleUtils.resetDecorations(inhDeco);
                        curColor = inhColor == null ? null : NamedTextColor.WHITE;
                        curShadow = inhShadow == null ? null : ShadowColor.none();
                        pos += 2;
                        continue;
                    }
                }

                textBuf.append(c);
                pos++;
                continue;
            }

            // ── Shadow color: $ ──────────────────────────────────────────────
            if (c == '$' && pos + 1 < len) {
                final char next = src[pos + 1];

                if (next == '#') {
                    final long packed = StyleUtils.parseHexPacked(src, pos + 1);
                    if (packed != -1L) {
                        // Flush accumulated text before the shadow style changes.
                        final Token shadowFlush = flushText(textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                                curDeco, curColor, curShadow, inhDeco, inhColor, inhShadow);
                        if (shadowFlush != null) {
                            if (depth == 0) {
                                if (rootTokenCnt == 0 && shadowFlush instanceof Token.Plain) {
                                    if (curDeco != 0 || curColor != null || curShadow != null) {
                                        canHoistRootStyle = !hasRootResetAhead(src, pos);
                                    }
                                    if (canHoistRootStyle) {
                                        inhDeco = curDeco;
                                        inhColor = curColor;
                                        inhShadow = curShadow;
                                    }
                                }
                                rootTokens = pushToken(rootTokens, rootTokenCnt++, shadowFlush);
                            } else {
                                stack[depth - 1].add(shadowFlush);
                            }
                        }
                        textBuf.setLength(0);
                        dynPhCnt = 0;
                        hasDyn = false;
                        curShadow = StyleUtils.shadowColorOf((int) packed);
                        pos += 1 + (int) (packed >>> 32);
                        continue;
                    }
                }

                textBuf.append(c);
                pos++;
                continue;
            }

            // ── Placeholder: {key} ───────────────────────────────────────────
            if (c == '{' && !noMorePlaceholders) {
                int braceEnd = pos + 1;
                while (braceEnd < len && src[braceEnd] != '}') braceEnd++;

                if (braceEnd < len) {
                    final int keyOff = pos + 1;
                    final int keyLen = braceEnd - keyOff;
                    final int phIdx = placeholders.intern(src, keyOff, keyLen);
                    if (dynPh == null) {
                        dynPh = new int[INITIAL_PLACEHOLDER_CAPACITY];
                        dynStatic = new String[INITIAL_PLACEHOLDER_CAPACITY + 1];
                    } else if (dynPhCnt == dynPh.length) {
                        dynPh = Arrays.copyOf(dynPh, dynPh.length << 1);
                        dynStatic = Arrays.copyOf(dynStatic, dynStatic.length << 1);
                    }

                    dynStatic[dynPhCnt] = textBuf.isEmpty() ? "" : textBuf.toString();
                    textBuf.setLength(0);
                    dynPh[dynPhCnt] = phIdx;
                    dynPhCnt++;
                    hasDyn = true;

                    pos = braceEnd + 1;
                    continue;
                }

                noMorePlaceholders = true;
                textBuf.append(c);
                pos++;
                continue;
            }

            // ── Open nested block: [ ─────────────────────────────────────────
            if (c == '[') {
                final Token accumulated = flushText(
                        textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                        curDeco, curColor, curShadow,
                        inhDeco, inhColor, inhShadow);
                if (accumulated != null) {
                    if (depth == 0) {
                        if (rootTokenCnt == 0 && accumulated instanceof Token.Plain) {
                            if (curDeco != 0 || curColor != null || curShadow != null) {
                                canHoistRootStyle = !hasRootResetAhead(src, pos);
                            }
                            if (canHoistRootStyle) {
                                inhDeco = curDeco;
                                inhColor = curColor;
                                inhShadow = curShadow;
                            }
                        }
                        rootTokens = pushToken(rootTokens, rootTokenCnt++, accumulated);
                    } else {
                        stack[depth - 1].add(accumulated);
                    }
                }
                textBuf.setLength(0);
                dynPhCnt = 0;
                hasDyn = false;

                if (stack == null) {
                    stack = new Frame[INITIAL_STACK_CAPACITY];
                } else if (depth == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                Frame frame = stack[depth];
                if (frame == null) {
                    frame = new Frame();
                    stack[depth] = frame;
                }
                frame.reset(curDeco, curColor, curShadow, inhDeco, inhColor, inhShadow);
                inhDeco = curDeco;
                inhColor = curColor;
                inhShadow = curShadow;
                depth++;
                pos++;
                continue;
            }

            // ── Close block: ] ───────────────────────────────────────────────
            if (c == ']') {
                if (depth == 0) {
                    textBuf.append(c);
                    pos++;
                    continue;
                }
                pos++;

                int actStart = -1, actEnd = -1;
                if (pos < len && src[pos] == '(') {
                    final int parenEnd = findClosingParen(src, pos);
                    if (parenEnd != -1) {
                        actStart = pos + 1;
                        actEnd = parenEnd;
                        pos = parenEnd + 1;
                    }
                }

                final Token innerText = flushText(
                        textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                        curDeco, curColor, curShadow,
                        inhDeco, inhColor, inhShadow);
                textBuf.setLength(0);
                dynPhCnt = 0;
                hasDyn = false;

                final Frame frame = stack[--depth];
                curDeco = frame.outerDecorations;
                curColor = frame.outerColor;
                curShadow = frame.outerShadow;
                inhDeco = frame.inheritedDecorations;
                inhColor = frame.inheritedColor;
                inhShadow = frame.inheritedShadow;

                final Token bracketToken = buildBracketToken(
                        innerText,
                        frame.outerDecorations,
                        frame.outerColor,
                        frame.outerShadow,
                        frame.children, frame.childCount,
                        src, actStart, actEnd, placeholders,
                        inhDeco, inhColor, inhShadow);

                if (depth == 0) rootTokens = pushToken(rootTokens, rootTokenCnt++, bracketToken);
                else stack[depth - 1].add(bracketToken);
                continue;
            }

            // ── Plain character ──────────────────────────────────────────────
            textBuf.append(c);
            pos++;
        }

        // ── End of input ─────────────────────────────────────────────────────
        Token pending = flushText(textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                curDeco, curColor, curShadow,
                inhDeco, inhColor, inhShadow);

        // Be lenient with missing closing brackets: close every open frame at EOF
        // instead of dropping the already compiled content.
        while (depth > 0) {
            final Frame frame = stack[--depth];
            curDeco = frame.outerDecorations;
            curColor = frame.outerColor;
            curShadow = frame.outerShadow;
            inhDeco = frame.inheritedDecorations;
            inhColor = frame.inheritedColor;
            inhShadow = frame.inheritedShadow;

            pending = buildBracketToken(
                    pending,
                    frame.outerDecorations,
                    frame.outerColor,
                    frame.outerShadow,
                    frame.children, frame.childCount,
                    src, -1, -1, placeholders,
                    inhDeco, inhColor, inhShadow
            );
        }

        if (pending != null) rootTokens = pushToken(rootTokens, rootTokenCnt++, pending);

        final Token root = rootTokenCnt == 1
                ? rootTokens[0]
                : buildRootToken(rootTokens, rootTokenCnt, canHoistRootStyle);

        return root;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Flush accumulated text into a token
    // ════════════════════════════════════════════════════════════════════════

    private static Token flushText(
            StringBuilder textBuf,
            String[] dynStatic, int[] dynPh, int dynPhCnt, boolean hasDyn,
            short deco, TextColor color, ShadowColor shadow,
            short inhDeco, TextColor inhColor, ShadowColor inhShadow
    ) {
        if (textBuf.isEmpty() && dynPhCnt == 0) return null;

        final short deltaDeco = StyleUtils.decorationDelta(deco, inhDeco);
        final TextColor deltaColor = color != inhColor ? color : null;
        final ShadowColor deltaShadow = shadow != inhShadow ? shadow : null;

        final Style style = StyleUtils.create(deltaColor, deltaShadow, deltaDeco);

        if (!hasDyn) {
            final String s = textBuf.isEmpty() ? "" : textBuf.toString();
            return new Token.Plain(s, style);
        } else {
            final String tail = textBuf.isEmpty() ? null : textBuf.toString();

            int trimLen;
            if (tail != null) {
                trimLen = dynPhCnt + 1;
            } else {
                trimLen = dynPhCnt;
                while (trimLen > 0 && dynStatic[trimLen - 1] == null) trimLen--;
            }

            final String[] staticParts;
            if (trimLen == 0) {
                staticParts = Token.NO_PARTS;
            } else {
                staticParts = Arrays.copyOf(dynStatic, trimLen);
                if (tail != null) staticParts[dynPhCnt] = tail;
            }

            final int[] phIndices = Arrays.copyOf(dynPh, dynPhCnt);
            return new Token.PlainDyn(staticParts, phIndices, style);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Build bracket token
    //  Receives src + actStart/actEnd offsets to avoid substring allocation.
    // ════════════════════════════════════════════════════════════════════════

    private static Token buildBracketToken(
            Token innerText,
            short decorations,
            TextColor color,
            ShadowColor shadow,
            Token[] children,
            int childCount,
            char[] source,
            int actionStart,
            int actionEnd,
            PlaceholderTable placeholders,
            short inheritedDecorations,
            TextColor inheritedColor,
            ShadowColor inheritedShadow
    ) {
        final StringBuilder scratch = SCRATCH_BUFFER.get();
        try {
            return buildBracketToken0(
                    innerText, decorations, color, shadow, children, childCount,
                    source, actionStart, actionEnd, placeholders,
                    inheritedDecorations, inheritedColor, inheritedShadow, scratch
            );
        } finally {
            releaseScratch(scratch);
        }
    }

    private static Token buildBracketToken0(
            Token innerText,
            short decorations,
            TextColor color,
            ShadowColor shadow,
            Token[] children,
            int childCount,
            char[] source,
            int actionStart,
            int actionEnd,
            PlaceholderTable placeholders,
            short inheritedDecorations,
            TextColor inheritedColor,
            ShadowColor inheritedShadow,
            StringBuilder scratch
    ) {
        decorations = StyleUtils.decorationDelta(
                decorations,
                inheritedDecorations
        );

        color = color != inheritedColor
                ? color
                : null;

        shadow = shadow != inheritedShadow
                ? shadow
                : null;

        final Style visualStyle = StyleUtils.create(
                color,
                shadow,
                decorations
        );

        if (actionStart == -1) {
            return wrapContent(innerText, children, childCount, visualStyle);
        }

        byte selectedClickType = -1;
        int selectedClickPriority = Integer.MAX_VALUE;
        boolean selectedClickDynamic = false;

        String selectedClickValue = null;
        int selectedClickValueStart = -1;
        int selectedClickValueEnd = -1;

        int pageValue = -1;

        byte selectedContentType = ACTION_UNKNOWN;
        int selectedContentPriority = Integer.MAX_VALUE;
        boolean selectedContentDynamic = false;

        String selectedContentValue = null;
        int selectedContentValueStart = -1;
        int selectedContentValueEnd = -1;

        String styleValue = null;
        boolean styleDynamic = false;

        String colorValue = null;
        boolean colorDynamic = false;

        boolean hasShow = false;
        boolean showDynamic = false;

        String showText = null;
        int showTextStart = -1;
        int showTextEnd = -1;

        boolean hasInsertion = false;

        String insertionValue = null;
        int insertionValueStart = -1;
        int insertionValueEnd = -1;

        int position = actionStart;

        while (position < actionEnd) {
            while (position < actionEnd) {
                final char character = source[position];

                if (character != ' ' && character != ',') {
                    break;
                }

                position++;
            }

            if (position >= actionEnd) {
                break;
            }

            int keyOffset = position;

            while (position < actionEnd && source[position] != ':') {
                position++;
            }

            if (position >= actionEnd) {
                break;
            }

            int keyEnd = position;

            while (keyOffset < keyEnd && source[keyOffset] == ' ') {
                keyOffset++;
            }

            while (keyEnd > keyOffset && source[keyEnd - 1] == ' ') {
                keyEnd--;
            }

            final int keyLength = keyEnd - keyOffset;
            final byte action = actionType(
                    source,
                    keyOffset,
                    keyLength
            );

            position++;

            while (position < actionEnd && source[position] == ' ') {
                position++;
            }

            if (position >= actionEnd) {
                break;
            }

            final boolean captureValue = switch (action) {
                case ACTION_RUN, ACTION_HEAD, ACTION_SHOW, ACTION_INSERT -> true;

                case ACTION_COLOR -> colorValue == null;

                case ACTION_STYLE -> styleValue == null;

                case ACTION_SUGGEST -> selectedClickPriority >= 1;

                case ACTION_URL -> selectedClickPriority >= 2;

                case ACTION_COPY -> selectedClickPriority >= 3;

                case ACTION_PAGE -> selectedClickPriority >= 4;

                case ACTION_SPRITE -> selectedContentPriority >= 1;

                case ACTION_GRADIENT -> selectedContentPriority >= 2;

                default -> false;
            };

            final boolean detectDynamic = switch (action) {
                case ACTION_RUN,
                        ACTION_SUGGEST,
                        ACTION_URL,
                        ACTION_COPY,
                        ACTION_SHOW,
                        ACTION_HEAD, ACTION_COLOR, ACTION_STYLE -> true;

                default -> false;
            };

            final boolean quoted;

            int valueStart = -1;
            int valueEnd = -1;

            boolean dynamic = false;

            final char firstCharacter = source[position];

            if (firstCharacter == '"' || firstCharacter == '\'') {
                quoted = true;

                final char quote = firstCharacter;
                position++;

                if (captureValue) {
                    scratch.setLength(0);
                }

                while (position < actionEnd) {
                    final char character = source[position++];

                    if (character == quote) {
                        break;
                    }

                    if (character == '\\' && position < actionEnd) {
                        final char escapedCharacter = source[position++];

                        if (captureValue) {
                            scratch.append(escapedCharacter);
                        }

                        continue;
                    }

                    if (captureValue) {
                        if (detectDynamic && character == '{') {
                            dynamic = true;
                        }

                        scratch.append(character);
                    }
                }

                while (position < actionEnd && source[position] != ',') {
                    position++;
                }
            } else {
                quoted = false;
                valueStart = position;

                while (position < actionEnd && source[position] != ',') {
                    if (captureValue
                            && detectDynamic
                            && source[position] == '{') {
                        dynamic = true;
                    }

                    position++;
                }

                valueEnd = position;

                if (captureValue) {
                    while (valueStart < valueEnd
                            && source[valueStart] == ' ') {
                        valueStart++;
                    }

                    if (action != ACTION_SUGGEST) {
                        while (valueEnd > valueStart
                                && source[valueEnd - 1] == ' ') {
                            valueEnd--;
                        }
                    }
                }
            }

            if (!captureValue) {
                continue;
            }

            final String quotedValue =
                    quoted && action != ACTION_PAGE
                            ? scratch.toString()
                            : null;

            switch (action) {
                case ACTION_RUN -> {
                    selectedClickPriority = 0;
                    selectedClickType = Token.MetaDyn.RUN;
                    selectedClickDynamic = dynamic;

                    pageValue = -1;

                    if (quoted) {
                        selectedClickValue = quotedValue;
                        selectedClickValueStart = -1;
                        selectedClickValueEnd = -1;
                    } else {
                        selectedClickValue = null;
                        selectedClickValueStart = valueStart;
                        selectedClickValueEnd = valueEnd;
                    }
                }

                case ACTION_SUGGEST -> {
                    selectedClickPriority = 1;
                    selectedClickType = Token.MetaDyn.SUGGEST;
                    selectedClickDynamic = dynamic;

                    pageValue = -1;

                    if (quoted) {
                        selectedClickValue = quotedValue;
                        selectedClickValueStart = -1;
                        selectedClickValueEnd = -1;
                    } else {
                        selectedClickValue = null;
                        selectedClickValueStart = valueStart;
                        selectedClickValueEnd = valueEnd;
                    }
                }

                case ACTION_URL -> {
                    selectedClickPriority = 2;
                    selectedClickType = Token.MetaDyn.URL;
                    selectedClickDynamic = dynamic;

                    pageValue = -1;

                    if (quoted) {
                        selectedClickValue = quotedValue;
                        selectedClickValueStart = -1;
                        selectedClickValueEnd = -1;
                    } else {
                        selectedClickValue = null;
                        selectedClickValueStart = valueStart;
                        selectedClickValueEnd = valueEnd;
                    }
                }

                case ACTION_COPY -> {
                    selectedClickPriority = 3;
                    selectedClickType = Token.MetaDyn.COPY;
                    selectedClickDynamic = dynamic;

                    pageValue = -1;

                    if (quoted) {
                        selectedClickValue = quotedValue;
                        selectedClickValueStart = -1;
                        selectedClickValueEnd = -1;
                    } else {
                        selectedClickValue = null;
                        selectedClickValueStart = valueStart;
                        selectedClickValueEnd = valueEnd;
                    }
                }

                case ACTION_PAGE -> {
                    selectedClickPriority = 4;
                    selectedClickType = -1;
                    selectedClickDynamic = false;

                    selectedClickValue = null;
                    selectedClickValueStart = -1;
                    selectedClickValueEnd = -1;

                    pageValue = quoted
                            ? parseIntOrMinusOne(scratch)
                            : parseIntOrMinusOne(
                            source,
                            valueStart,
                            valueEnd
                    );
                }

                case ACTION_SHOW -> {
                    hasShow = true;
                    showDynamic = dynamic;

                    if (quoted) {
                        showText = quotedValue;
                        showTextStart = -1;
                        showTextEnd = -1;
                    } else {
                        showText = null;
                        showTextStart = valueStart;
                        showTextEnd = valueEnd;
                    }
                }

                case ACTION_INSERT -> {
                    hasInsertion = true;

                    if (quoted) {
                        insertionValue = quotedValue;
                        insertionValueStart = -1;
                        insertionValueEnd = -1;
                    } else {
                        insertionValue = null;
                        insertionValueStart = valueStart;
                        insertionValueEnd = valueEnd;
                    }
                }

                case ACTION_HEAD -> {
                    selectedContentPriority = 0;
                    selectedContentType = ACTION_HEAD;
                    selectedContentDynamic = dynamic;

                    if (quoted) {
                        selectedContentValue = quotedValue;
                        selectedContentValueStart = -1;
                        selectedContentValueEnd = -1;
                    } else {
                        selectedContentValue = null;
                        selectedContentValueStart = valueStart;
                        selectedContentValueEnd = valueEnd;
                    }
                }

                case ACTION_SPRITE -> {
                    selectedContentPriority = 1;
                    selectedContentType = ACTION_SPRITE;
                    selectedContentDynamic = false;

                    if (quoted) {
                        selectedContentValue = quotedValue;
                        selectedContentValueStart = -1;
                        selectedContentValueEnd = -1;
                    } else {
                        selectedContentValue = null;
                        selectedContentValueStart = valueStart;
                        selectedContentValueEnd = valueEnd;
                    }
                }

                case ACTION_STYLE -> {
                    styleValue = quoted ? quotedValue : new String(source, valueStart, valueEnd - valueStart);
                    styleDynamic = dynamic;
                }

                case ACTION_COLOR -> {
                    colorValue = quoted ? quotedValue : new String(source, valueStart, valueEnd - valueStart);
                    colorDynamic = dynamic;
                }

                case ACTION_GRADIENT -> {
                    selectedContentPriority = 2;
                    selectedContentType = ACTION_GRADIENT;
                    selectedContentDynamic = false;

                    if (quoted) {
                        selectedContentValue = quotedValue;
                        selectedContentValueStart = -1;
                        selectedContentValueEnd = -1;
                    } else {
                        selectedContentValue = null;
                        selectedContentValueStart = valueStart;
                        selectedContentValueEnd = valueEnd;
                    }
                }
            }
        }

        if (selectedClickType != -1
                && selectedClickValue == null
                && selectedClickValueStart != -1) {
            selectedClickValue = new String(
                    source,
                    selectedClickValueStart,
                    selectedClickValueEnd - selectedClickValueStart
            );
        }

        if (selectedContentType != ACTION_UNKNOWN
                && selectedContentValue == null
                && selectedContentValueStart != -1) {
            selectedContentValue = new String(
                    source,
                    selectedContentValueStart,
                    selectedContentValueEnd - selectedContentValueStart
            );
        }

        if (hasShow
                && showText == null
                && showTextStart != -1) {
            showText = new String(
                    source,
                    showTextStart,
                    showTextEnd - showTextStart
            );
        }

        if (hasInsertion
                && insertionValue == null
                && insertionValueStart != -1) {
            insertionValue = new String(
                    source,
                    insertionValueStart,
                    insertionValueEnd - insertionValueStart
            );
        }

        final byte clickType = selectedClickType;
        final String clickValue = selectedClickValue;
        final boolean clickDynamic = selectedClickDynamic;

        final ClickEvent staticClick;

        if (clickType != -1) {
            staticClick = clickDynamic
                    ? null
                    : Token.MetaFullDyn.buildClick(
                    clickType,
                    clickValue
            );
        } else if (pageValue != -1) {
            staticClick = ClickEvent.changePage(pageValue);
        } else {
            staticClick = null;
        }

        final HoverEvent<?> staticHover =
                showText != null && !showDynamic
                        ? buildStaticHover(showText)
                        : null;

        // Visual properties and all static metadata belong to the same root
        // component. Dynamic actions later replace only their corresponding
        // event on that component instead of introducing a wrapper component.
        final Style contentStyle = StyleUtils.create(
                color,
                shadow,
                decorations,
                staticClick,
                staticHover,
                insertionValue,
                null
        );

        Token contentToken;

        if (selectedContentType == ACTION_HEAD) {
            if (selectedContentDynamic) {
                final DynamicValue dynamicHead = parseDynamicValue(
                        selectedContentValue,
                        placeholders
                );

                contentToken = new Token.ObjDyn(
                        dynamicHead.staticParts,
                        dynamicHead.placeholderIndices,
                        contentStyle
                );
            } else {
                contentToken = new Token.Obj(
                        ObjectContents.playerHead(selectedContentValue),
                        contentStyle
                );
            }
        } else if (selectedContentType == ACTION_SPRITE) {
            contentToken = new Token.Obj(
                    ObjectContents.sprite(
                            parseSpriteKey(selectedContentValue)
                    ),
                    contentStyle
            );
        } else {
            if (selectedContentType == ACTION_GRADIENT) {
                final int[] gradientColors =
                        parseGradientColors(selectedContentValue);

                if (gradientColors != null) {
                    final Token rawContent = wrapContent(
                            innerText,
                            children,
                            childCount,
                            Style.empty()
                    );

                    contentToken = new Token.GradientContent(
                            rawContent,
                            gradientColors,
                            contentStyle,
                            isDynamic(rawContent)
                    );
                } else {
                    contentToken = wrapContent(
                            innerText,
                            children,
                            childCount,
                            contentStyle
                    );
                }
            } else {
                contentToken = wrapContent(
                        innerText,
                        children,
                        childCount,
                        contentStyle
                );
            }
        }

        if (styleValue != null) {
            if (styleDynamic) {
                final DynamicValue value = parseDynamicValue(styleValue, placeholders);
                contentToken = new Token.StyleContent(contentToken, value.staticParts,
                        value.placeholderIndices, null, true);
            } else {
                final Style value = StyleUtils.parseStyle(styleValue);
                if (value != null && !value.isEmpty()) {
                    contentToken = new Token.StyleContent(contentToken, Token.NO_PARTS,
                            Token.NO_PH, value, isDynamic(contentToken));
                }
            }
        }

        if (colorValue != null) {
            if (colorDynamic) {
                final DynamicValue value = parseDynamicValue(colorValue, placeholders);
                contentToken = new Token.ColorContent(contentToken, value.staticParts,
                        value.placeholderIndices, null, true);
            } else {
                final TextColor value = StyleUtils.parseColor(colorValue);
                if (value != null) {
                    contentToken = new Token.ColorContent(contentToken, Token.NO_PARTS,
                            Token.NO_PH, value, isDynamic(contentToken));
                }
            }
        }

        if (!clickDynamic && !showDynamic) {
            return contentToken;
        }

        if (clickDynamic && showDynamic) {
            final DynamicValue dynamicClick = parseDynamicValue(
                    clickValue,
                    placeholders
            );

            final Token.DynamicHover dynamicHover =
                    compileDynamicHover(
                            showText,
                            placeholders
                    );

            return new Token.MetaFullDyn(
                    contentToken,
                    clickType,
                    dynamicClick.staticParts,
                    dynamicClick.placeholderIndices,
                    null,
                    Token.MetaDyn.SHOW,
                    null,
                    null,
                    dynamicHover
            );
        }

        if (clickDynamic) {
            final DynamicValue dynamicClick = parseDynamicValue(
                    clickValue,
                    placeholders
            );

            return new Token.MetaDyn(
                    contentToken,
                    clickType,
                    dynamicClick.staticParts,
                    dynamicClick.placeholderIndices,
                    null
            );
        }

        return new Token.MetaDyn(
                contentToken,
                Token.MetaDyn.SHOW,
                Token.NO_PARTS,
                Token.NO_PH,
                compileDynamicHover(
                        showText,
                        placeholders
                )
        );
    }

    private static DynamicValue parseDynamicValue(String value, PlaceholderTable placeholders) {
        String[] staticParts = new String[INITIAL_PLACEHOLDER_CAPACITY + 1];
        int[] placeholderIndices = new int[INITIAL_PLACEHOLDER_CAPACITY];
        int count = 0;
        int offset = 0;

        while (offset < value.length()) {
            final int start = value.indexOf('{', offset);
            if (start == -1) break;
            final int end = value.indexOf('}', start + 1);
            if (end == -1) break;

            if (count == placeholderIndices.length) {
                placeholderIndices = Arrays.copyOf(placeholderIndices, placeholderIndices.length << 1);
                staticParts = Arrays.copyOf(staticParts, staticParts.length << 1);
            }
            staticParts[count] = start == offset ? null : value.substring(offset, start);
            placeholderIndices[count++] = placeholders.intern(value, start + 1, end - start - 1);
            offset = end + 1;
        }

        final String tail = offset == value.length() ? null : value.substring(offset);
        int staticCount = tail == null ? count : count + 1;
        while (staticCount > 0 && tail == null && staticParts[staticCount - 1] == null) {
            staticCount--;
        }

        final String[] resultStatic = staticCount == 0
                ? Token.NO_PARTS
                : Arrays.copyOf(staticParts, staticCount);
        if (tail != null) resultStatic[count] = tail;

        final int[] resultIndices = count == 0
                ? Token.NO_PH
                : Arrays.copyOf(placeholderIndices, count);
        return new DynamicValue(resultStatic, resultIndices);
    }

    private static Token.DynamicHover compileDynamicHover(
            String value,
            PlaceholderTable outerPlaceholders
    ) {
        return new Token.DynamicHover(compileToken(value, outerPlaceholders));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Utilities
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Compares a {@code char[]} region to a {@link String} without allocating an intermediate string.
     */
    static boolean regionEquals(char[] src, int off, int len, String s) {
        if (s.length() != len) return false;
        for (int i = 0; i < len; i++)
            if (src[off + i] != s.charAt(i)) return false;
        return true;
    }

    private static Token wrapContent(
            Token innerText,
            Token[] children,
            int childCount,
            Style style
    ) {
        final int count = childCount + (innerText == null ? 0 : 1);
        if (count == 0) return new Token.Plain("", style);

        if (count == 1) {
            final Token token = childCount == 1 ? children[0] : innerText;
            if (token instanceof Token.Plain plain) {
                final Style merged = plain.style.merge(
                        style,
                        Style.Merge.Strategy.IF_ABSENT_ON_TARGET
                );
                return new Token.Plain(plain.text, merged);
            }

            final Token[] content = {token};
            return isDynamic(token)
                    ? new Token.DynChildren("", style, content, 1)
                    : new Token.Children("", style, content, 1);
        }

        final Token[] content;
        if (innerText == null && children.length == childCount) {
            content = children;
        } else {
            content = Arrays.copyOf(children, count);
            if (innerText != null) content[childCount] = innerText;
        }

        return hasDynamicChildren(content, count)
                ? new Token.DynChildren("", style, content, count)
                : new Token.Children("", style, content, count);
    }

    private static boolean hasDynamicChildren(Token[] children, int count) {
        for (int index = 0; index < count; index++) {
            if (isDynamic(children[index])) return true;
        }
        return false;
    }

    private static boolean isDynamic(Token token) {
        return token instanceof Token.PlainDyn
                || token instanceof Token.MetaDyn
                || token instanceof Token.MetaFullDyn
                || token instanceof Token.ObjDyn
                || token instanceof Token.DynChildren
                || token instanceof Token.GradientContent gradientContent && gradientContent.dynamic
                || token instanceof Token.ColorContent colorContent && colorContent.dynamic
                || token instanceof Token.StyleContent styleContent && styleContent.dynamic;
    }

    private static HoverEvent<?> buildStaticHover(String show) {
        if (show != null && !show.contains("{"))
            return HoverEvent.showText(Text.of(show).render());
        return null;
    }

    private static Token buildRootToken(Token[] tokens, int count, boolean canHoistRootStyle) {
        if (canHoistRootStyle && count > 1 && tokens[0] instanceof Token.Plain first) {
            final Token[] children = Arrays.copyOfRange(tokens, 1, count);
            final boolean dynChild = hasDynamicChildren(children, children.length);
            return dynChild
                    ? new Token.DynChildren(first.text, first.style, children, children.length)
                    : new Token.Children(first.text, first.style, children, children.length);
        }
        final boolean dynChild = hasDynamicChildren(tokens, count);
        return dynChild
                ? new Token.DynChildren("", Style.empty(), Arrays.copyOf(tokens, count), count)
                : new Token.Children("", Style.empty(), tokens, count);
    }

    /**
     * Checks whether the remaining source contains an effective root-level
     * legacy reset. Escaped characters, placeholders, nested bracket content,
     * and action payloads are skipped because they do not reset the root style.
     *
     * <p>This is allocation-free and is invoked at most once per compilation,
     * only when the first root token actually carries a visual style.</p>
     */
    private static boolean hasRootResetAhead(char[] src, int start) {
        int bracketDepth = 0;

        for (int index = start; index < src.length; index++) {
            final char character = src[index];

            if (character == '\\' && index + 1 < src.length) {
                final char escaped = src[index + 1];
                if (escaped == 'n'
                        || escaped == '['
                        || escaped == ']'
                        || escaped == '{'
                        || escaped == '}'
                        || escaped == '\\') {
                    index++;
                }
                continue;
            }

            if (character == '{') {
                int end = index + 1;
                while (end < src.length && src[end] != '}') end++;
                if (end < src.length) index = end;
                continue;
            }

            if (character == '[') {
                bracketDepth++;
                continue;
            }

            if (character == ']') {
                if (bracketDepth > 0) bracketDepth--;

                if (index + 1 < src.length && src[index + 1] == '(') {
                    final int end = findClosingParen(src, index + 1);
                    if (end != -1) index = end;
                }
                continue;
            }

            if (bracketDepth == 0
                    && character == '&'
                    && index + 1 < src.length) {
                final char code = src[index + 1];
                if (code == 'r' || code == 'R') return true;
            }
        }

        return false;
    }

    private static int findClosingParen(char[] src, int start) {
        int pos = start + 1;
        final int len = src.length;
        while (pos < len) {
            final char c = src[pos];
            if (c == ')') return pos;
            if (c == '"' || c == '\'') {
                pos++;
                while (pos < len && src[pos] != c) {
                    if (src[pos] == '\\') pos++;
                    pos++;
                }
            }
            pos++;
        }
        return -1;
    }

    /**
     * Parses a dash-separated list of hex colors ({@code #RGB} or {@code #RRGGBB})
     * directly from a {@code char[]} without intermediate string allocation or regex.
     *
     * @return an array of 24-bit RGB values, or {@code null} if fewer than two colors are found
     */
    private static int[] parseGradientColors(String value) {
        final char[] src = value.toCharArray();
        final int len = src.length;

        int count = 1;
        for (int i = 1; i < len; i++) {
            if (src[i] == '-' && i + 1 < len && (src[i + 1] == '#' || StyleUtils.isHexChar(src[i + 1])))
                count++;
        }
        if (count < 2) return null;

        final int[] colors = new int[count];
        int ci = 0;
        int i = 0;

        while (i < len && ci < count) {
            if (i > 0 && src[i] == '-') i++;
            while (i < len && src[i] == ' ') i++;
            if (i >= len) return null;

            if (src[i] == '#') {
                final long packed = StyleUtils.parseHexPacked(src, i);
                if (packed == -1L) return null;
                colors[ci++] = (int) packed;
                i += (int) (packed >>> 32);
            } else {
                final TextColor legacy = StyleUtils.fromLegacyCode(src[i]);
                if (legacy == null) return null;
                colors[ci++] = legacy.value();
                i++;
            }

            while (i < len && src[i] != '-') i++;
        }

        return ci == count ? colors : null;
    }

    private static Token[] pushToken(Token[] arr, int count, Token token) {
        if (count == arr.length) arr = Arrays.copyOf(arr, arr.length * 2);
        arr[count] = token;
        return arr;
    }

    private static byte actionType(char[] source, int offset, int length) {
        return switch (length) {
            case 3 -> {
                final char first = source[offset];

                if (first == 'r'
                        && source[offset + 1] == 'u'
                        && source[offset + 2] == 'n') {
                    yield ACTION_RUN;
                }

                if (first == 'u'
                        && source[offset + 1] == 'r'
                        && source[offset + 2] == 'l') {
                    yield ACTION_URL;
                }

                yield ACTION_UNKNOWN;
            }

            case 4 -> {
                final char first = source[offset];

                if (first == 'c'
                        && source[offset + 1] == 'o'
                        && source[offset + 2] == 'p'
                        && source[offset + 3] == 'y') {
                    yield ACTION_COPY;
                }

                if (first == 's'
                        && source[offset + 1] == 'h'
                        && source[offset + 2] == 'o'
                        && source[offset + 3] == 'w') {
                    yield ACTION_SHOW;
                }

                if (first == 'h'
                        && source[offset + 1] == 'e'
                        && source[offset + 2] == 'a'
                        && source[offset + 3] == 'd') {
                    yield ACTION_HEAD;
                }

                if (first == 'p'
                        && source[offset + 1] == 'a'
                        && source[offset + 2] == 'g'
                        && source[offset + 3] == 'e') {
                    yield ACTION_PAGE;
                }

                yield ACTION_UNKNOWN;
            }

            case 5 -> {
                if (source[offset] == 'c'
                        && source[offset + 1] == 'o'
                        && source[offset + 2] == 'l'
                        && source[offset + 3] == 'o'
                        && source[offset + 4] == 'r') {
                    yield ACTION_COLOR;
                }
                if (source[offset] == 's'
                        && source[offset + 1] == 't'
                        && source[offset + 2] == 'y'
                        && source[offset + 3] == 'l'
                        && source[offset + 4] == 'e') {
                    yield ACTION_STYLE;
                }
                yield ACTION_UNKNOWN;
            }

            case 6 -> {
                final char first = source[offset];

                if (first == 'i'
                        && source[offset + 1] == 'n'
                        && source[offset + 2] == 's'
                        && source[offset + 3] == 'e'
                        && source[offset + 4] == 'r'
                        && source[offset + 5] == 't') {
                    yield ACTION_INSERT;
                }

                if (first == 's'
                        && source[offset + 1] == 'p'
                        && source[offset + 2] == 'r'
                        && source[offset + 3] == 'i'
                        && source[offset + 4] == 't'
                        && source[offset + 5] == 'e') {
                    yield ACTION_SPRITE;
                }

                yield ACTION_UNKNOWN;
            }

            case 7 -> source[offset] == 's'
                    && source[offset + 1] == 'u'
                    && source[offset + 2] == 'g'
                    && source[offset + 3] == 'g'
                    && source[offset + 4] == 'e'
                    && source[offset + 5] == 's'
                    && source[offset + 6] == 't'
                    ? ACTION_SUGGEST
                    : ACTION_UNKNOWN;

            case 8 -> source[offset] == 'g'
                    && source[offset + 1] == 'r'
                    && source[offset + 2] == 'a'
                    && source[offset + 3] == 'd'
                    && source[offset + 4] == 'i'
                    && source[offset + 5] == 'e'
                    && source[offset + 6] == 'n'
                    && source[offset + 7] == 't'
                    ? ACTION_GRADIENT
                    : ACTION_UNKNOWN;

            default -> ACTION_UNKNOWN;
        };
    }

    private static int parseIntOrMinusOne(char[] source, int start, int end) {
        if (start >= end) {
            return -1;
        }

        int index = start;
        boolean negative = false;
        int limit = -Integer.MAX_VALUE;

        final char first = source[index];

        if (first < '0' || first > '9') {
            if (first == '-') {
                negative = true;
                limit = Integer.MIN_VALUE;
            } else if (first != '+') {
                return -1;
            }

            index++;

            if (index == end) {
                return -1;
            }
        }

        final int multiplyLimit = limit / 10;
        int result = 0;

        while (index < end) {
            final int digit = source[index++] - '0';

            if (digit < 0 || digit > 9) {
                return -1;
            }

            if (result < multiplyLimit) {
                return -1;
            }

            result *= 10;

            if (result < limit + digit) {
                return -1;
            }

            result -= digit;
        }

        return negative ? result : -result;
    }

    private static int parseIntOrMinusOne(CharSequence value) {
        final int length = value.length();

        if (length == 0) {
            return -1;
        }

        int index = 0;
        boolean negative = false;
        int limit = -Integer.MAX_VALUE;

        final char first = value.charAt(0);

        if (first < '0' || first > '9') {
            if (first == '-') {
                negative = true;
                limit = Integer.MIN_VALUE;
            } else if (first != '+') {
                return -1;
            }

            index++;

            if (index == length) {
                return -1;
            }
        }

        final int multiplyLimit = limit / 10;
        int result = 0;

        while (index < length) {
            final int digit = value.charAt(index++) - '0';

            if (digit < 0 || digit > 9) {
                return -1;
            }

            if (result < multiplyLimit) {
                return -1;
            }

            result *= 10;

            if (result < limit + digit) {
                return -1;
            }

            result -= digit;
        }

        return negative ? result : -result;
    }

    private record DynamicValue(String[] staticParts, int[] placeholderIndices) {
    }

    private static Key parseSpriteKey(String value) {
        try {
            return Key.key(value);
        } catch (RuntimeException exception) {
            throw new TextFormatException("Invalid sprite key: " + value, exception);
        }
    }

    private static void releaseScratch(StringBuilder scratch) {
        if (scratch.capacity() > MAX_RETAINED_SCRATCH_CAPACITY) {
            SCRATCH_BUFFER.set(new StringBuilder(64));
        } else {
            scratch.setLength(0);
        }
    }

    private static final class Frame {

        private short outerDecorations;
        private TextColor outerColor;
        private ShadowColor outerShadow;
        private short inheritedDecorations;
        private TextColor inheritedColor;
        private ShadowColor inheritedShadow;
        private Token[] children = new Token[8];
        private int childCount;

        private void reset(
                short outerDecorations,
                TextColor outerColor,
                ShadowColor outerShadow,
                short inheritedDecorations,
                TextColor inheritedColor,
                ShadowColor inheritedShadow
        ) {
            this.outerDecorations = outerDecorations;
            this.outerColor = outerColor;
            this.outerShadow = outerShadow;
            this.inheritedDecorations = inheritedDecorations;
            this.inheritedColor = inheritedColor;
            this.inheritedShadow = inheritedShadow;
            this.childCount = 0;
        }

        private void add(Token token) {
            this.children = pushToken(this.children, this.childCount++, token);
        }
    }

    private static final class PlaceholderTable {

        private static final String[] EMPTY_KEYS = new String[0];

        private String[] keys;
        private Map<String, Integer> indices;
        private int count;

        private int intern(char[] source, int offset, int length) {
            if (this.indices == null) {
                for (int index = 0; index < this.count; index++) {
                    if (regionEquals(source, offset, length, this.keys[index])) return index;
                }

                final String key = new String(source, offset, length);
                final int index = this.add(key);
                if (this.count == PLACEHOLDER_HASH_THRESHOLD) this.promoteToHash();
                return index;
            }

            final String key = new String(source, offset, length);
            final Integer existing = this.indices.get(key);
            if (existing != null) return existing;

            final int index = this.add(key);
            this.indices.put(key, index);
            return index;
        }

        private int intern(String source, int offset, int length) {
            if (this.indices == null) {
                for (int index = 0; index < this.count; index++) {
                    final String key = this.keys[index];
                    if (key.length() == length && source.regionMatches(offset, key, 0, length)) {
                        return index;
                    }
                }

                final String key = source.substring(offset, offset + length);
                final int index = this.add(key);
                if (this.count == PLACEHOLDER_HASH_THRESHOLD) this.promoteToHash();
                return index;
            }

            final String key = source.substring(offset, offset + length);
            final Integer existing = this.indices.get(key);
            if (existing != null) return existing;

            final int index = this.add(key);
            this.indices.put(key, index);
            return index;
        }

        private int intern(String key) {
            if (this.indices == null) {
                for (int index = 0; index < this.count; index++) {
                    if (this.keys[index].equals(key)) return index;
                }

                final int index = this.add(key);
                if (this.count == PLACEHOLDER_HASH_THRESHOLD) this.promoteToHash();
                return index;
            }

            final Integer existing = this.indices.get(key);
            if (existing != null) return existing;

            final int index = this.add(key);
            this.indices.put(key, index);
            return index;
        }

        private int add(String key) {
            this.ensureCapacity();
            final int index = this.count++;
            this.keys[index] = key;
            return index;
        }

        private void promoteToHash() {
            final Map<String, Integer> map = new HashMap<>(this.count * 2);
            for (int index = 0; index < this.count; index++) {
                map.put(this.keys[index], index);
            }
            this.indices = map;
        }

        private String[] toArray() {
            return this.count == 0 ? EMPTY_KEYS : Arrays.copyOf(this.keys, this.count);
        }

        private void ensureCapacity() {
            if (this.keys == null) {
                this.keys = new String[INITIAL_PLACEHOLDER_CAPACITY];
            } else if (this.count == this.keys.length) {
                this.keys = Arrays.copyOf(this.keys, this.keys.length << 1);
            }
        }
    }
}
