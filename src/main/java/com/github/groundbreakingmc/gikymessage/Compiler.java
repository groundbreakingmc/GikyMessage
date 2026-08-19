package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.Arrays;

import static com.github.groundbreakingmc.gikymessage.StyleUtils.*;

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
 *        {@code page}, {@code insert}, {@code gradient}, {@code head}, {@code sprite})</li>
 *   <li>{@code \n}, {@code \[}, {@code \]}, {@code \{}, {@code \}}, {@code \\} — escapes</li>
 * </ul>
 */
final class Compiler {

    private static final int PH_CAP = 16;
    private static final int STACK_CAP = 8;

    /**
     * Per-thread scratch buffer for quoted value and action parsing.
     */
    private static final ThreadLocal<StringBuilder> SCRATCH_BUFFER =
            ThreadLocal.withInitial(() -> new StringBuilder(64));

    private static final short DECO_BOLD;
    private static final short DECO_ITALIC;
    private static final short DECO_UNDERLINED;
    private static final short DECO_STRIKETHROUGH;
    private static final short DECO_OBFUSCATED;

    static {
        short bold = 0, italic = 0, underlined = 0, strikethrough = 0, obfuscated = 0;
        for (final TextDecoration d : TextDecoration.values()) {
            final short bit = (short) (StyleUtils.BIT_TRUE << (d.ordinal() * StyleUtils.BITS));
            switch (d) {
                case BOLD -> bold = bit;
                case ITALIC -> italic = bit;
                case UNDERLINED -> underlined = bit;
                case STRIKETHROUGH -> strikethrough = bit;
                case OBFUSCATED -> obfuscated = bit;
            }
        }
        DECO_BOLD = bold;
        DECO_ITALIC = italic;
        DECO_UNDERLINED = underlined;
        DECO_STRIKETHROUGH = strikethrough;
        DECO_OBFUSCATED = obfuscated;
    }

    private Compiler() {
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Entry point
    // ════════════════════════════════════════════════════════════════════════

    static Text compile(String raw, boolean cacheable) {
        if (raw == null || raw.isEmpty()) return Text.EMPTY;

        final char[] src = raw.toCharArray();
        final int len = src.length;

        String[] phKeys = new String[PH_CAP];
        int phCount = 0;

        short curDeco = 0;
        TextColor curColor = null;
        ShadowColor curShadow = null;

        final short[] stackDeco = new short[STACK_CAP];
        final TextColor[] stackColor = new TextColor[STACK_CAP];
        final ShadowColor[] stackShadow = new ShadowColor[STACK_CAP];
        final Token[][] stackChildren = new Token[STACK_CAP][8];
        final int[] stackChildCnt = new int[STACK_CAP];
        int depth = 0;

        final short[] stackInhDeco = new short[STACK_CAP];
        final TextColor[] stackInhColor = new TextColor[STACK_CAP];
        final ShadowColor[] stackInhShadow = new ShadowColor[STACK_CAP];

        short inhDeco = 0;
        TextColor inhColor = null;
        ShadowColor inhShadow = null;

        final StringBuilder textBuf = new StringBuilder();
        Token[] rootTokens = new Token[8];
        int rootTokenCnt = 0;
        boolean canHoistRootStyle = true;

        final String[] dynStatic = new String[PH_CAP + 1];
        final int[] dynPh = new int[PH_CAP];
        int dynPhCnt = 0;
        boolean hasDyn = false;

        int pos = 0;

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

                // Determine whether this is a valid style-changing code before
                // touching any state. If it is, flush any accumulated text first
                // so it gets the OLD style, not the incoming one.
                final boolean willChangeColor = fromLegacyCode(next) != null
                        || (next == '#' && parseHexPacked(src, pos + 1) != -1L);
                final boolean willChangeDeco = next == 'b' || next == 'l' || next == 'o' || next == 'n'
                        || next == 'm' || next == 'k' || next == 'r';

                if (willChangeColor || willChangeDeco) {
                    final Token styleFlush = flushText(textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                            curDeco, curColor, curShadow, inhDeco, inhColor, inhShadow);
                    if (styleFlush != null) {
                        if (depth == 0) {
                            if (rootTokenCnt == 0 && styleFlush instanceof Token.Plain) {
                                inhDeco = curDeco;
                                inhColor = curColor;
                                inhShadow = curShadow;
                            }
                            rootTokens = pushToken(rootTokens, rootTokenCnt++, styleFlush);
                        } else {
                            final int d = depth - 1;
                            stackChildren[d] = pushToken(stackChildren[d], stackChildCnt[d]++, styleFlush);
                        }
                    }
                    textBuf.setLength(0);
                    dynPhCnt = 0;
                    hasDyn = false;
                }

                if (next == '#') {
                    final long packed = parseHexPacked(src, pos + 1);
                    if (packed != -1L) {
                        curColor = textColorOf((int) packed);
                        pos += 1 + (int) (packed >>> 32);
                        continue;
                    }
                }

                final TextColor legacy = fromLegacyCode(next);
                if (legacy != null) {
                    curColor = legacy;
                    pos += 2;
                    continue;
                }

                switch (next) {
                    case 'l' -> {
                        curDeco |= DECO_BOLD;
                        pos += 2;
                        continue;
                    }
                    case 'o' -> {
                        curDeco |= DECO_ITALIC;
                        pos += 2;
                        continue;
                    }
                    case 'n' -> {
                        curDeco |= DECO_UNDERLINED;
                        pos += 2;
                        continue;
                    }
                    case 'm' -> {
                        curDeco |= DECO_STRIKETHROUGH;
                        pos += 2;
                        continue;
                    }
                    case 'k' -> {
                        curDeco |= DECO_OBFUSCATED;
                        pos += 2;
                        continue;
                    }
                    case 'r' -> {
                        if (depth == 0 && rootTokenCnt > 0) canHoistRootStyle = false;
                        curDeco = 0;
                        curColor = null;
                        curShadow = null;
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
                    final long packed = parseHexPacked(src, pos + 1);
                    if (packed != -1L) {
                        // Flush accumulated text before the shadow style changes.
                        final Token shadowFlush = flushText(textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                                curDeco, curColor, curShadow, inhDeco, inhColor, inhShadow);
                        if (shadowFlush != null) {
                            if (depth == 0) {
                                if (rootTokenCnt == 0 && shadowFlush instanceof Token.Plain) {
                                    inhDeco = curDeco;
                                    inhColor = curColor;
                                    inhShadow = curShadow;
                                }
                                rootTokens = pushToken(rootTokens, rootTokenCnt++, shadowFlush);
                            } else {
                                final int d = depth - 1;
                                stackChildren[d] = pushToken(stackChildren[d], stackChildCnt[d]++, shadowFlush);
                            }
                        }
                        textBuf.setLength(0);
                        dynPhCnt = 0;
                        hasDyn = false;
                        curShadow = shadowColorOf((int) packed);
                        pos += 1 + (int) (packed >>> 32);
                        continue;
                    }
                }

                textBuf.append(c);
                pos++;
                continue;
            }

            // ── Placeholder: {key} ───────────────────────────────────────────
            if (c == '{') {
                int braceEnd = pos + 1;
                while (braceEnd < len && src[braceEnd] != '}') braceEnd++;

                if (braceEnd < len) {
                    final int keyOff = pos + 1;
                    final int keyLen = braceEnd - keyOff;
                    int phIdx = -1;
                    for (int i = 0; i < phCount; i++) {
                        if (regionEquals(src, keyOff, keyLen, phKeys[i])) {
                            phIdx = i;
                            break;
                        }
                    }
                    if (phIdx == -1) {
                        if (phCount == phKeys.length)
                            phKeys = Arrays.copyOf(phKeys, phKeys.length * 2);
                        phKeys[phCount] = new String(src, keyOff, keyLen);
                        phIdx = phCount++;
                    }

                    dynStatic[dynPhCnt] = textBuf.isEmpty() ? "" : textBuf.toString();
                    textBuf.setLength(0);
                    dynPh[dynPhCnt] = phIdx;
                    dynPhCnt++;
                    hasDyn = true;

                    pos = braceEnd + 1;
                    continue;
                }

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
                            inhDeco = curDeco;
                            inhColor = curColor;
                            inhShadow = curShadow;
                        }
                        rootTokens = pushToken(rootTokens, rootTokenCnt++, accumulated);
                    } else {
                        final int d = depth - 1;
                        stackChildren[d] = pushToken(stackChildren[d], stackChildCnt[d]++, accumulated);
                    }
                }
                textBuf.setLength(0);
                dynPhCnt = 0;
                hasDyn = false;

                stackDeco[depth] = curDeco;
                stackColor[depth] = curColor;
                stackShadow[depth] = curShadow;
                stackChildCnt[depth] = 0;
                stackInhDeco[depth] = inhDeco;
                stackInhColor[depth] = inhColor;
                stackInhShadow[depth] = inhShadow;
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

                // Save the style active at the closing ']' (inner scope)
                // BEFORE restoring the outer scope — this is the bracket's own style.
                final short bracketDeco = curDeco;
                final TextColor bracketColor = curColor;
                final ShadowColor bracketShadow = curShadow;

                depth--;
                curDeco = stackDeco[depth];
                curColor = stackColor[depth];
                curShadow = stackShadow[depth];
                inhDeco = stackInhDeco[depth];
                inhColor = stackInhColor[depth];
                inhShadow = stackInhShadow[depth];

                final int[] phCountHolder = {phCount};
                final Token bracketToken = buildBracketToken(
                        innerText, bracketDeco, bracketColor, bracketShadow,
                        stackChildren[depth], stackChildCnt[depth],
                        src, actStart, actEnd, phKeys, phCountHolder,
                        inhDeco, inhColor, inhShadow);
                phCount = phCountHolder[0];

                if (depth == 0) rootTokens = pushToken(rootTokens, rootTokenCnt++, bracketToken);
                else {
                    final int d = depth - 1;
                    stackChildren[d] = pushToken(stackChildren[d], stackChildCnt[d]++, bracketToken);
                }
                continue;
            }

            // ── Plain character ──────────────────────────────────────────────
            textBuf.append(c);
            pos++;
        }

        // ── End of input ─────────────────────────────────────────────────────
        final Token last = flushText(textBuf, dynStatic, dynPh, dynPhCnt, hasDyn,
                curDeco, curColor, curShadow,
                inhDeco, inhColor, inhShadow);
        if (last != null) rootTokens = pushToken(rootTokens, rootTokenCnt++, last);

        final String[] finalPhKeys = Arrays.copyOf(phKeys, phCount);
        final Token root = rootTokenCnt == 1
                ? rootTokens[0]
                : buildRootToken(rootTokens, rootTokenCnt, canHoistRootStyle);

        return new TextImpl(root, finalPhKeys, cacheable);
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

        final short deltaDeco = decorationDelta(deco, inhDeco);
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

    /**
     * Computes the decoration delta — the bits from {@code cur} that differ from {@code inh}
     * and are not {@link StyleUtils#BIT_NOT_SET}. Unrolled over the five decoration slots
     * (5 decorations × 2 bits = shifts 0, 2, 4, 6, 8).
     */
    private static short decorationDelta(short cur, short inh) {
        short result = 0;
        int c, h;

        c = cur & StyleUtils.MASK;
        h = inh & StyleUtils.MASK;
        if (c != StyleUtils.BIT_NOT_SET && c != h) result |= (short) c;

        c = (cur >> 2) & StyleUtils.MASK;
        h = (inh >> 2) & StyleUtils.MASK;
        if (c != StyleUtils.BIT_NOT_SET && c != h) result |= (short) (c << 2);

        c = (cur >> 4) & StyleUtils.MASK;
        h = (inh >> 4) & StyleUtils.MASK;
        if (c != StyleUtils.BIT_NOT_SET && c != h) result |= (short) (c << 4);

        c = (cur >> 6) & StyleUtils.MASK;
        h = (inh >> 6) & StyleUtils.MASK;
        if (c != StyleUtils.BIT_NOT_SET && c != h) result |= (short) (c << 6);

        c = (cur >> 8) & StyleUtils.MASK;
        h = (inh >> 8) & StyleUtils.MASK;
        if (c != StyleUtils.BIT_NOT_SET && c != h) result |= (short) (c << 8);

        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Build bracket token
    //  Receives src + actStart/actEnd offsets to avoid substring allocation.
    // ════════════════════════════════════════════════════════════════════════

    private static Token buildBracketToken(
            Token innerText,
            short deco, TextColor color, ShadowColor shadow,
            Token[] children, int childCnt,
            char[] src, int actStart, int actEnd,
            String[] phKeys, int[] phCount,
            short inhDeco, TextColor inhColor, ShadowColor inhShadow
    ) {
        final short deltaDeco = decorationDelta(deco, inhDeco);
        final TextColor deltaColor = color != inhColor ? color : null;
        final ShadowColor deltaShadow = shadow != inhShadow ? shadow : null;
        deco = deltaDeco;
        color = deltaColor;
        shadow = deltaShadow;

        if (actStart == -1) {
            if (childCnt == 0) return innerText != null ? innerText : new Token.Plain("", Style.empty());
            final boolean dynChild = hasDynamicChildren(children, childCnt);
            final Style style = StyleUtils.create(color, shadow, deco);
            final String text = innerText instanceof Token.Plain p ? p.text : "";
            final Token[] ch = Arrays.copyOf(children, childCnt);
            return new Token.Children(text, style, ch, childCnt, dynChild);
        }

        // ── Parse actions directly from src[actStart..actEnd) ────────────────
        String runCmd = null;
        boolean runDyn = false;
        String suggestCmd = null;
        boolean suggestDyn = false;
        String urlVal = null;
        boolean urlDyn = false;
        String copyVal = null;
        boolean copyDyn = false;
        String showText = null;
        boolean showDyn = false;
        int pageVal = -1;
        String dialogVal = null;
        String itemVal = null;
        String entityVal = null;
        int[] gradColors = null;
        String headVal = null;
        boolean headDyn = false;
        String spriteVal = null;
        String insertVal = null;

        final StringBuilder sb = SCRATCH_BUFFER.get();

        int aPos = actStart;

        while (aPos < actEnd) {
            while (aPos < actEnd && (src[aPos] == ' ' || src[aPos] == ',')) aPos++;
            if (aPos >= actEnd) break;

            final int keyStart = aPos;
            while (aPos < actEnd && src[aPos] != ':') aPos++;
            if (aPos >= actEnd) break;

            int keyS = keyStart, keyE = aPos;
            while (keyS < keyE && src[keyS] == ' ') keyS++;
            while (keyE > keyS && src[keyE - 1] == ' ') keyE--;
            aPos++;

            while (aPos < actEnd && src[aPos] == ' ') aPos++;
            if (aPos >= actEnd) break;

            final String value;
            final boolean valueDyn;
            if (src[aPos] == '"' || src[aPos] == '\'') {
                final char quote = src[aPos++];
                sb.setLength(0);
                boolean dyn = false;
                while (aPos < actEnd && src[aPos] != quote) {
                    if (src[aPos] == '\\' && aPos + 1 < actEnd) aPos++;
                    if (src[aPos] == '{') dyn = true;
                    sb.append(src[aPos++]);
                }
                aPos++;
                value = sb.toString();
                valueDyn = dyn;
            } else {
                int valStart = aPos;
                boolean dyn = false;
                while (aPos < actEnd && src[aPos] != ',') {
                    if (src[aPos] == '{') dyn = true;
                    aPos++;
                }
                while (valStart < aPos && src[valStart] == ' ') valStart++;
                value = new String(src, valStart, aPos - valStart);
                valueDyn = dyn;
            }

            if (regionEquals(src, keyS, keyE - keyS, "run")) {
                runCmd = value;
                runDyn = valueDyn;
            } else if (regionEquals(src, keyS, keyE - keyS, "suggest")) {
                suggestCmd = value;
                suggestDyn = valueDyn;
            } else if (regionEquals(src, keyS, keyE - keyS, "url")) {
                urlVal = value;
                urlDyn = valueDyn;
            } else if (regionEquals(src, keyS, keyE - keyS, "copy")) {
                copyVal = value;
                copyDyn = valueDyn;
            } else if (regionEquals(src, keyS, keyE - keyS, "insert")) {
                insertVal = value;
            } else if (regionEquals(src, keyS, keyE - keyS, "show")) {
                showText = value;
                showDyn = valueDyn;
            } else if (regionEquals(src, keyS, keyE - keyS, "item")) {
                itemVal = value;
            } else if (regionEquals(src, keyS, keyE - keyS, "entity")) {
                entityVal = value;
            } else if (regionEquals(src, keyS, keyE - keyS, "head")) {
                headVal = value;
                headDyn = valueDyn;
            } else if (regionEquals(src, keyS, keyE - keyS, "sprite")) {
                spriteVal = value;
            } else if (regionEquals(src, keyS, keyE - keyS, "dialog")) {
                dialogVal = value;
            } else if (regionEquals(src, keyS, keyE - keyS, "page")) {
                try {
                    pageVal = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
            } else if (regionEquals(src, keyS, keyE - keyS, "gradient")) {
                gradColors = parseGradientColors(value);
            }
        }

        final boolean isObject = headVal != null || spriteVal != null;
        final boolean hasGradient = gradColors != null;
        final boolean isContentDyn = innerText instanceof Token.PlainDyn
                || innerText instanceof Token.MetaDynContent;
        final boolean hasMetaDyn = runDyn || suggestDyn || urlDyn || copyDyn || showDyn;
        final boolean hasMeta = runCmd != null || suggestCmd != null || urlVal != null
                || copyVal != null || pageVal != -1 || dialogVal != null
                || showText != null || itemVal != null || entityVal != null;

        final ClickEvent staticClick = buildStaticClick(runCmd, suggestCmd, urlVal, copyVal, pageVal, dialogVal);
        final HoverEvent<?> staticHover = buildStaticHover(showText);

        // ── Object (head / sprite) ───────────────────────────────────────────
        if (isObject) {
            final Style style = StyleUtils.create(color, shadow, deco, staticClick, staticHover, insertVal, null);
            if (headVal != null) {
                if (headDyn) {
                    final int phIdx = internPlaceholder(phKeys, phCount, headVal.substring(1, headVal.length() - 1));
                    return new Token.ObjDyn(phIdx, style);
                }
                return new Token.Obj(ObjectContents.playerHead(headVal), style);
            }
            return new Token.Obj(ObjectContents.sprite(Key.key(spriteVal)), style);
        }

        // ── Gradient ─────────────────────────────────────────────────────────
        if (hasGradient) {
            final String text = extractText(innerText);
            if (isContentDyn) {
                final Token.PlainDyn pd = (Token.PlainDyn) innerText;
                return new Token.GradientDyn(pd.staticParts, pd.phIndices, gradColors,
                        deco, shadow, staticClick, staticHover);
            }
            return new Token.Gradient(text, gradColors, deco, shadow, staticClick, staticHover);
        }

        // ── Meta (click / hover / insert) ────────────────────────────────────
        if (hasMeta || insertVal != null) {
            final Style fullStyle = StyleUtils.create(color, shadow, deco, staticClick, staticHover, insertVal, null);

            if (!hasMetaDyn) {
                if (isContentDyn) {
                    final Token.PlainDyn pd = (Token.PlainDyn) innerText;
                    return new Token.MetaDynContent(pd.staticParts, pd.phIndices, fullStyle);
                }
                if (childCnt > 0) {
                    final boolean dynChild = hasDynamicChildren(children, childCnt);
                    final String txt = extractText(innerText);
                    return new Token.Children(txt, fullStyle, Arrays.copyOf(children, childCnt), childCnt, dynChild);
                }
                return new Token.Plain(extractText(innerText), fullStyle);
            }

            // Determine the dynamic action type and its raw value string
            byte dynActionType = -1;
            String dynActionVal = null;
            if (runDyn) {
                dynActionType = Token.MetaDyn.RUN;
                dynActionVal = runCmd;
            } else if (suggestDyn) {
                dynActionType = Token.MetaDyn.SUGGEST;
                dynActionVal = suggestCmd;
            } else if (urlDyn) {
                dynActionType = Token.MetaDyn.URL;
                dynActionVal = urlVal;
            } else if (copyDyn) {
                dynActionType = Token.MetaDyn.COPY;
                dynActionVal = copyVal;
            } else if (showDyn) {
                dynActionType = Token.MetaDyn.SHOW;
                dynActionVal = showText;
            } else {
                // unreachable
                throw new UnsupportedOperationException();
            }

            // Parse placeholders inside the dynamic action value
            final char[] av = dynActionVal.toCharArray();
            final String[] mStatic = new String[PH_CAP + 1];
            final int[] mPh = new int[PH_CAP];
            int mPhCnt = 0;
            sb.setLength(0);

            for (int i = 0; i < av.length; i++) {
                if (av[i] == '{') {
                    int end = i + 1;
                    while (end < av.length && av[end] != '}') end++;
                    if (end < av.length) {
                        mStatic[mPhCnt] = sb.isEmpty() ? null : sb.toString();
                        sb.setLength(0);
                        mPh[mPhCnt] = internPlaceholder(phKeys, phCount, new String(av, i + 1, end - i - 1));
                        mPhCnt++;
                        i = end;
                        continue;
                    }
                }
                sb.append(av[i]);
            }

            final String tail = sb.isEmpty() ? null : sb.toString();
            int mTrimLen;
            if (tail != null) {
                mTrimLen = mPhCnt + 1;
            } else {
                mTrimLen = mPhCnt;
                while (mTrimLen > 0 && mStatic[mTrimLen - 1] == null) mTrimLen--;
            }

            final String[] metaStatic = mTrimLen == 0 ? Token.NO_PARTS : Arrays.copyOf(mStatic, mTrimLen);
            if (tail != null) metaStatic[mPhCnt] = tail;
            final int[] metaPh = mPhCnt == 0 ? Token.NO_PH : Arrays.copyOf(mPh, mPhCnt);

            // ── Second dynamic action ─────────────────────────────────────────
            // When two actions are both dynamic (e.g. run:'/cmd {x}', show:'{y}')
            // the first if/else chain above only captured one. Detect the second here.
            byte dynAction2Type = -1;
            String[] meta2Static = null;
            int[] meta2Ph = null;

            final String dynAction2Val;
            if (dynActionType != Token.MetaDyn.SHOW && showDyn) {
                dynAction2Type = Token.MetaDyn.SHOW;
                dynAction2Val = showText;
            } else if (dynActionType != Token.MetaDyn.RUN && runDyn) {
                dynAction2Type = Token.MetaDyn.RUN;
                dynAction2Val = runCmd;
            } else if (dynActionType != Token.MetaDyn.SUGGEST && suggestDyn) {
                dynAction2Type = Token.MetaDyn.SUGGEST;
                dynAction2Val = suggestCmd;
            } else if (dynActionType != Token.MetaDyn.URL && urlDyn) {
                dynAction2Type = Token.MetaDyn.URL;
                dynAction2Val = urlVal;
            } else if (dynActionType != Token.MetaDyn.COPY && copyDyn) {
                dynAction2Type = Token.MetaDyn.COPY;
                dynAction2Val = copyVal;
            } else {
                dynAction2Val = null;
            }

            if (dynAction2Val != null) {
                final char[] av2 = dynAction2Val.toCharArray();
                final String[] m2St = new String[PH_CAP + 1];
                final int[] m2Ph = new int[PH_CAP];
                int m2PhCnt = 0;
                sb.setLength(0);
                for (int i = 0; i < av2.length; i++) {
                    if (av2[i] == '{') {
                        int end = i + 1;
                        while (end < av2.length && av2[end] != '}') end++;
                        if (end < av2.length) {
                            m2St[m2PhCnt] = sb.isEmpty() ? null : sb.toString();
                            sb.setLength(0);
                            m2Ph[m2PhCnt] = internPlaceholder(phKeys, phCount, new String(av2, i + 1, end - i - 1));
                            m2PhCnt++;
                            i = end;
                            continue;
                        }
                    }
                    sb.append(av2[i]);
                }
                final String tail2 = sb.isEmpty() ? null : sb.toString();
                int m2TrimLen = tail2 != null ? m2PhCnt + 1 : m2PhCnt;
                while (m2TrimLen > 0 && tail2 == null && m2St[m2TrimLen - 1] == null) m2TrimLen--;
                meta2Static = m2TrimLen == 0 ? Token.NO_PARTS : Arrays.copyOf(m2St, m2TrimLen);
                if (tail2 != null) meta2Static[m2PhCnt] = tail2;
                meta2Ph = m2PhCnt == 0 ? Token.NO_PH : Arrays.copyOf(m2Ph, m2PhCnt);
            }

            // For the static second action (only when it is truly static, i.e. no dynamic second was found)
            final ClickEvent sc2 = (dynAction2Type == -1 && dynActionType == Token.MetaDyn.SHOW) ? staticClick : null;
            final HoverEvent<?> sh2 = (dynAction2Type == -1 && dynActionType != Token.MetaDyn.SHOW) ? staticHover : null;

            if (!isContentDyn) {
                final Token[] ch = childCnt > 0 ? Arrays.copyOf(children, childCnt) : null;
                final boolean dynChild = childCnt > 0 && hasDynamicChildren(children, childCnt);
                // If there is a second dynamic action, represent static content via MetaFullDyn
                if (dynAction2Type != -1) {
                    final String txt = extractText(innerText);
                    final String[] singleStatic = txt.isEmpty() ? Token.NO_PARTS : new String[]{txt};
                    return new Token.MetaFullDyn(singleStatic, Token.NO_PH, deco, color, shadow,
                            dynActionType, metaStatic, metaPh,
                            dynAction2Type, meta2Static, meta2Ph,
                            ch, childCnt, dynChild);
                }
                return new Token.MetaDyn(extractText(innerText), deco, color, shadow,
                        dynActionType, metaStatic, metaPh, sh2, sc2, ch, childCnt, dynChild);
            }

            final Token.PlainDyn pd = (Token.PlainDyn) innerText;

            if (childCnt > 0) {
                final Token[] ch = Arrays.copyOf(children, childCnt);
                final boolean dynChild = hasDynamicChildren(children, childCnt);
                return new Token.MetaFullDyn(pd.staticParts, pd.phIndices, deco, color, shadow,
                        dynActionType, metaStatic, metaPh,
                        dynAction2Type, meta2Static, meta2Ph,
                        ch, childCnt, dynChild);
            }

            return new Token.MetaFullDyn(pd.staticParts, pd.phIndices, deco, color, shadow,
                    dynActionType, metaStatic, metaPh,
                    dynAction2Type, meta2Static, meta2Ph,
                    null, 0, false);
        }

        // ── No meta — plain styled block or children ─────────────────────────
        if (childCnt > 0) {
            final boolean dynChild = hasDynamicChildren(children, childCnt);
            final Style style = StyleUtils.create(color, shadow, deco);
            final String txt = extractText(innerText);
            return new Token.Children(txt, style, Arrays.copyOf(children, childCnt), childCnt, dynChild);
        }

        return innerText != null ? innerText : new Token.Plain("", Style.empty());
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

    /**
     * Returns the index of {@code key} in the placeholder table, inserting it if absent.
     *
     * @throws IllegalStateException if the placeholder capacity is exceeded
     */
    private static int internPlaceholder(String[] phKeys, int[] phCount, String key) {
        for (int i = 0; i < phCount[0]; i++)
            if (phKeys[i].equals(key)) return i;
        if (phCount[0] == phKeys.length)
            throw new IllegalStateException("Too many placeholders (max " + phKeys.length + ")");
        phKeys[phCount[0]] = key;
        return phCount[0]++;
    }

    private static String extractText(Token token) {
        if (token instanceof Token.Plain p) return p.text;
        return "";
    }

    private static boolean hasDynamicChildren(Token[] children, int cnt) {
        for (int i = 0; i < cnt; i++)
            if (isDynamic(children[i])) return true;
        return false;
    }

    private static boolean isDynamic(Token t) {
        return t instanceof Token.PlainDyn
                || t instanceof Token.MetaDynContent
                || t instanceof Token.MetaDyn
                || t instanceof Token.MetaFullDyn
                || t instanceof Token.GradientDyn
                || t instanceof Token.ObjDyn
                || (t instanceof Token.Children ch && ch.hasDynChild);
    }

    private static ClickEvent buildStaticClick(
            String run, String suggest, String url, String copy, int page, String dialog) {
        if (run != null && !run.contains("{")) return ClickEvent.runCommand(run);
        if (suggest != null && !suggest.contains("{")) return ClickEvent.suggestCommand(suggest);
        if (url != null && !url.contains("{")) return ClickEvent.openUrl(url);
        if (copy != null && !copy.contains("{")) return ClickEvent.copyToClipboard(copy);
        if (page != -1) return ClickEvent.changePage(page);
        if (dialog != null) return ClickEvent.showDialog(null); // TODO: make
        return null;
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
            return new Token.Children(first.text, first.style, children, children.length, dynChild);
        }
        final boolean dynChild = hasDynamicChildren(tokens, count);
        return new Token.Children("", Style.empty(),
                Arrays.copyOf(tokens, count), count, dynChild);
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
            if (src[i] == '-' && i + 1 < len && (src[i + 1] == '#' || isHexChar(src[i + 1])))
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
                final long packed = parseHexPacked(src, i);
                if (packed == -1L) return null;
                colors[ci++] = (int) packed;
                i += (int) (packed >>> 32);
            } else {
                final TextColor legacy = fromLegacyCode(src[i]);
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
}
