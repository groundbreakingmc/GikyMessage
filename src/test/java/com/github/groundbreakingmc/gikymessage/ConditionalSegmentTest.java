package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionalSegmentTest extends TextTestBase {

    /** Reports strings as text, longs as numbers and components as they are; anything else is absent. */
    private static TestValues values(Object... pairs) {
        final Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        return (key, value) -> {
            final Object current = values.get(key);
            if (current instanceof String text) value.text(text);
            else if (current instanceof Long number) value.number(number);
            else if (current instanceof Component component) value.component(component);
        };
    }

    private static String plain(Component component) {
        final StringBuilder builder = new StringBuilder();
        append(builder, component);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Component component) {
        if (component instanceof TextComponent text) builder.append(text.content());
        for (final Component child : component.children()) append(builder, child);
    }

    @Test
    void ifShowsTheSegmentOnlyWhileTheValueHasText() {
        // Brackets meant as text inside a segment are escaped.
        final Text<TestValues> text = bound("[&7\\[{clan}&7\\] ](if:{clan}){name}");
        assertEquals("[Knights] Steve", plain(text.render(values("clan", "Knights", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("clan", "", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("clan", "   ", "name", "Steve"))));
    }

    @Test
    void unlessShowsTheSegmentOnlyWhileTheValueHasNoText() {
        final Text<TestValues> text = bound("{name}[ &8(no clan)](unless:{clan})");
        assertEquals("Steve (no clan)", plain(text.render(values("clan", "", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("clan", "Knights", "name", "Steve"))));
    }

    @Test
    void componentsWithOnlyFormattingAreEmptyAndNumbersHaveText() {
        final Text<TestValues> text = bound("[<{prefix}> ](if:{prefix})x");
        assertEquals("x", plain(text.render(values("prefix", Component.text("", NamedTextColor.RED)))));
        assertEquals("x", plain(text.render(values("prefix",
                Component.text("").append(Component.text(" ", NamedTextColor.GOLD))))));
        assertEquals("<A> x", plain(text.render(values("prefix",
                Component.text("").append(Component.text("A", NamedTextColor.GOLD))))));
        assertEquals("<0> x", plain(text.render(values("prefix", 0L))));
        assertTrue(text.render(values("prefix", Component.translatable("myserver.rank"))).children().size() > 1);
    }

    @Test
    void missingValueRendersLiterallyAndCountsAsText() {
        assertEquals("[{clan}] Steve", plain(bound("[\\[{clan}\\] ](if:{clan}){name}").render(values("name", "Steve"))));
    }

    @Test
    void anyPlaceholderWithTextSatisfiesTheCondition() {
        final Text<TestValues> text = bound("[shown](if:\"{a} {b}\")");
        assertEquals("shown", plain(text.render(values("a", "", "b", "x"))));
        assertEquals("", plain(text.render(values("a", "", "b", ""))));
    }

    @Test
    void ifAndUnlessMustBothHold() {
        final Text<TestValues> text = bound("[{prefix} ](if:{prefix}, unless:{hidden}){name}");
        assertEquals("VIP Steve", plain(text.render(values("prefix", "VIP", "hidden", "", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("prefix", "VIP", "hidden", "yes", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("prefix", "", "hidden", "", "name", "Steve"))));
        assertEquals("x", plain(bound("[x](if:{a}, if:{b})").render(values("a", "", "b", "1"))));
    }

    @Test
    void staticConditionsAreDecidedWhileCompiling() {
        assertEquals("x", plain(Text.of("[x](if:yes)").render()));
        assertEquals("", plain(Text.of("[x](if:\"\")").render()));
        assertEquals("", plain(Text.of("[x](unless:yes)").render()));
        assertEquals("x", plain(Text.of("[x](unless:\" \")").render()));
        assertTrue(Text.of("[x](if:yes) [y](unless:yes)").keys().isEmpty());
    }

    @Test
    void conditionsCombineWithActionsAndNest() {
        final Component shown = bound("[{clan}](if:{clan}, run:\"/clan {clan}\")").render(values("clan", "Knights"));
        assertEquals(ClickEvent.runCommand("/clan Knights"), shown.clickEvent());

        final Text<TestValues> nested = bound("[[{a}](if:{a})/[{b}](if:{b})](unless:{off})");
        assertEquals("1/2", plain(nested.render(values("a", "1", "b", "2", "off", ""))));
        assertEquals("/2", plain(nested.render(values("a", "", "b", "2", "off", ""))));
        assertEquals("", plain(nested.render(values("a", "1", "b", "2", "off", "on"))));
    }

    @Test
    void comparisonsHoldWhileBothSidesHaveTheSameText() {
        final Text<TestValues> text = bound("[&aTeammate ](if:{a}=={b}){name}");
        assertEquals("Teammate Steve", plain(text.render(values("a", "Knights", "b", "Knights", "name", "Steve"))));
        assertEquals("Steve", plain(text.render(values("a", "Knights", "b", "Pirates", "name", "Steve"))));

        final Text<TestValues> spaced = bound("[x](if:\"{a} == {b}\")");
        assertEquals("x", plain(spaced.render(values("a", "1", "b", "1"))));
        assertEquals("x", plain(spaced.render(values("a", " Knights ", "b", "Knights"))));
        assertEquals("", plain(spaced.render(values("a", "Kni ghts", "b", "Knights"))));

        final Text<TestValues> different = bound("[x](if:{a}!={b})");
        assertEquals("x", plain(different.render(values("a", "1", "b", "2"))));
        assertEquals("", plain(different.render(values("a", "1", "b", "1"))));

        final Text<TestValues> literal = bound("[VIP](if:{group}==vip)");
        assertEquals("VIP", plain(literal.render(values("group", "vip"))));
        assertEquals("", plain(literal.render(values("group", "default"))));

        final Text<TestValues> negated = bound("[x](unless:{a}=={b})");
        assertEquals("", plain(negated.render(values("a", "1", "b", "1"))));
        assertEquals("x", plain(negated.render(values("a", "1", "b", "2"))));

        // Numbers and components compare by their text.
        assertEquals("x", plain(bound("[x](if:{a}=={b})").render(values("a", 5L, "b", "5"))));
        assertEquals("x", plain(bound("[x](if:{a}=={b})").render(values("a", Component.text("A"), "b", "A"))));

        // Without placeholders a comparison is decided while compiling.
        assertEquals("", plain(Text.of("[x](if:a==b)").render()));
        assertEquals("x", plain(Text.of("[x](if:a!=b)").render()));
    }

    @Test
    void numbersCompareAsNumbers() {
        final Text<TestValues> level = bound("[&6Veteran ](if:{level}>=10){name}");
        assertEquals("Veteran Steve", plain(level.render(values("level", 10L, "name", "Steve"))));
        assertEquals("Steve", plain(level.render(values("level", 9L, "name", "Steve"))));

        final Text<TestValues> more = bound("[x](if:{a}>{b})");
        assertEquals("x", plain(more.render(values("a", "1000.5", "b", 1000L))));
        assertEquals("", plain(more.render(values("a", "-2", "b", "1.5"))));
        assertEquals("x", plain(more.render(values("a", "123456789012345678901", "b", "1"))));
        assertEquals("x", plain(bound("[x](if:{a} < {b})").render(values("a", "-2", "b", "1.5"))));
        assertEquals("x", plain(bound("[x](if:{a}<=7)").render(values("a", "+7"))));

        // Equal numbers written differently are equal; other text compares as text.
        final Text<TestValues> equal = bound("[x](if:{a}=={b})");
        assertEquals("x", plain(equal.render(values("a", "10.0", "b", "10"))));
        assertEquals("x", plain(equal.render(values("a", "007", "b", 7L))));
        assertEquals("", plain(equal.render(values("a", "1e3", "b", "1000"))));
        assertEquals("", plain(bound("[x](if:{a}!={b})").render(values("a", "10.5", "b", "10.50"))));
    }

    @Test
    void aNumberComparesWithFixedTextOfAnyForm() {
        assertEquals("x", plain(bound("[x](if:{a}== 007 )").render(values("a", 7L))));
        assertEquals("x", plain(bound("[x](if:{a}==+7)").render(values("a", 7L))));
        assertEquals("x", plain(bound("[x](if:{a}==7.0)").render(values("a", 7L))));
        assertEquals("x", plain(bound("[x](if:{a}<7.5)").render(values("a", 7L))));
        assertEquals("x", plain(bound("[x](if:{a}>=-3)").render(values("a", -3L))));
        assertEquals("x", plain(bound("[x](if:{a}<123456789012345678901)").render(values("a", Long.MAX_VALUE))));
        assertEquals("", plain(bound("[x](if:{a}==seven)").render(values("a", 7L))));
        assertEquals("x", plain(bound("[x](if:{a}!=seven)").render(values("a", 7L))));
        assertEquals("x", plain(bound("[x](if:{a}<{b})").render(values("a", -5L, "b", 3L))));
        assertEquals("x", plain(bound("[x](if:5<{b})").render(values("b", 6L))));
        // A number used twice, by the text and by the condition, compares the same.
        assertEquals("7 x", plain(bound("{a} [x](if:{a}==7)").render(values("a", 7L))));
    }

    @Test
    void orderingNeedsNumbersOnBothSides() {
        assertEquals("", plain(bound("[x](if:{a}>5)").render(values("a", "many"))));
        assertEquals("", plain(bound("[x](if:{a}<5)").render(values("a", ""))));
        // unless negates the result, so it shows the segment for text that is no number.
        assertEquals("x", plain(bound("[x](unless:{a}<5)").render(values("a", "many"))));
        assertEquals("x", plain(Text.of("[x](if:3<10)").render()));
        assertTrue(Text.of("[x](if:3<10) [y](if:2>=3)").keys().isEmpty());
    }

    @Test
    void numbersChooseBranches() {
        final Text<TestValues> rank = bound("[&6Gold|&7Silver|&cBronze](if:{points}>=1000, if:{points}>=100)");
        assertEquals("Gold", plain(rank.render(values("points", 1500L))));
        assertEquals("Silver", plain(rank.render(values("points", 100L))));
        assertEquals("Bronze", plain(rank.render(values("points", 99L))));
    }

    @Test
    void andAndOrCombineParts() {
        final Text<TestValues> both = bound("[x](if:{a} && {b})");
        assertEquals("x", plain(both.render(values("a", "1", "b", "1"))));
        assertEquals("", plain(both.render(values("a", "1", "b", ""))));

        final Text<TestValues> either = bound("[x](if:{a} || {b})");
        assertEquals("x", plain(either.render(values("a", "", "b", "1"))));
        assertEquals("", plain(either.render(values("a", "", "b", ""))));

        // && binds tighter than ||.
        final Text<TestValues> mixed = bound("[x](if:{a}==1 || {b}==1 && {c}==1)");
        assertEquals("x", plain(mixed.render(values("a", "1", "b", "0", "c", "0"))));
        assertEquals("x", plain(mixed.render(values("a", "0", "b", "1", "c", "1"))));
        assertEquals("", plain(mixed.render(values("a", "0", "b", "1", "c", "0"))));
    }

    @Test
    void unlessNegatesTheWholeCondition() {
        final Text<TestValues> notBoth = bound("[x](unless:{a} && {b})");
        assertEquals("", plain(notBoth.render(values("a", "1", "b", "1"))));
        assertEquals("x", plain(notBoth.render(values("a", "1", "b", ""))));

        final Text<TestValues> neither = bound("[x](unless:{a} || {b})");
        assertEquals("x", plain(neither.render(values("a", "", "b", ""))));
        assertEquals("", plain(neither.render(values("a", "", "b", "1"))));
    }

    @Test
    void operatorsInsidePlaceholdersAndStaticPartsAreDecidedWhileCompiling() {
        assertEquals("x", plain(Text.of("[x](if:yes && yes)").render()));
        assertEquals("", plain(Text.of("[x](if:\"yes && \")").render()));
        assertEquals("x", plain(Text.of("[x](if:\" || yes\")").render()));
        assertTrue(Text.of("[x](if:yes || no)").keys().isEmpty());
        assertEquals("[a|b]", plain(bound("[{a||b}](if:{a||b})").render(values("a||b", "[a|b]"))));
    }

    @Test
    void aBranchAfterTheConditionIsShownOtherwise() {
        final Text<TestValues> text = bound("[&aTeammate|&cEnemy](if:{a}=={b}) {name}");
        assertEquals("Teammate Steve", plain(text.render(values("a", "K", "b", "K", "name", "Steve"))));
        assertEquals("Enemy Steve", plain(text.render(values("a", "K", "b", "P", "name", "Steve"))));

        final Text<TestValues> presence = bound("[{clan}|no clan](if:{clan})");
        assertEquals("Knights", plain(presence.render(values("clan", "Knights"))));
        assertEquals("no clan", plain(presence.render(values("clan", ""))));

        final Text<TestValues> negated = bound("[A|B](unless:{x})");
        assertEquals("A", plain(negated.render(values("x", ""))));
        assertEquals("B", plain(negated.render(values("x", "1"))));
    }

    @Test
    void eachConditionDecidesAboutItsBranchInOrder() {
        final Text<TestValues> text = bound("[A|B|C](if:{x}==1, if:{x}==2)");
        assertEquals("A", plain(text.render(values("x", "1"))));
        assertEquals("B", plain(text.render(values("x", "2"))));
        assertEquals("C", plain(text.render(values("x", "3"))));

        // Without a branch left for "otherwise", nothing is shown.
        assertEquals("", plain(bound("[A|B](if:{x}, if:{y})").render(values("x", "", "y", ""))));
        // Branches after the one without a condition are unused.
        assertEquals("B", plain(bound("[A|B|C](if:{x})").render(values("x", ""))));
    }

    @Test
    void aPipeIsTextOutsideConditionalGroupsAndWhenEscaped() {
        assertEquals("a|b", plain(Text.of("[a|b](color:red)").render()));
        assertEquals("a|b c", plain(Text.of("[a|b] c").render()));
        assertEquals("a|b", plain(bound("[a\\|b|c](if:{x})").render(values("x", "1"))));
        assertEquals("c", plain(bound("[a\\|b|c](if:{x})").render(values("x", ""))));
    }

    @Test
    void nestedGroupsKeepTheirOwnPipes() {
        final Text<TestValues> text = bound("[[x](run:/a|b)|y](if:{c})");
        final Component shown = text.render(values("c", "1"));
        assertEquals("x", plain(shown));
        assertEquals(ClickEvent.runCommand("/a|b"), find(shown, "x").clickEvent());
        assertEquals("y", plain(text.render(values("c", ""))));

        final Text<TestValues> inner = bound("[[A|B](if:{a}) / C|D](if:{d})");
        assertEquals("B / C", plain(inner.render(values("a", "", "d", "1"))));
        assertEquals("D", plain(inner.render(values("a", "", "d", ""))));
    }

    @Test
    void staticConditionsDecideAboutBranchesWhileCompiling() {
        assertEquals("A", plain(Text.of("[A|B](if:yes)").render()));
        assertEquals("B", plain(Text.of("[A|B](if:\"\")").render()));
        assertEquals("", plain(Text.of("[A](if:a==b)").render()));
        assertEquals("A", plain(Text.of("[A](if:a==a)").render()));

        final Text<TestValues> mixed = bound("[A|B|C](if:\"\", if:{x})");
        assertEquals("B", plain(mixed.render(values("x", "1"))));
        assertEquals("C", plain(mixed.render(values("x", ""))));
        assertEquals("A", plain(bound("[A|B|C](if:yes, if:{x})").render(values("x", "1"))));
    }

    @Test
    void theGroupsActionsAndStyleApplyToEveryBranch() {
        final Text<TestValues> text = bound("&a[A|B](if:{x}, run:/cmd)");
        final Component first = text.render(values("x", "1"));
        final Component second = text.render(values("x", ""));
        assertEquals(ClickEvent.runCommand("/cmd"), find(first, "A").clickEvent());
        assertEquals(ClickEvent.runCommand("/cmd"), find(second, "B").clickEvent());
        assertEquals(NamedTextColor.GREEN, colorOf(first, "A"));
        assertEquals(NamedTextColor.GREEN, colorOf(second, "B"));
    }

    @Test
    void aBranchStartsInTheStyleOfItsGroup() {
        // &r cancels the style from outside the group, as it does without branches.
        final Component branch = bound("&c&l[A&r B|C](if:{x}) D").render(values("x", "1"));
        final Component group = bound("&c&l[A&r B](if:{x}) D").render(values("x", "1"));
        for (final String text : new String[]{"A", " B", " D"}) {
            assertEquals(colorOf(group, text), colorOf(branch, text), text);
        }
        assertEquals(NamedTextColor.WHITE, colorOf(branch, " B"));
        assertEquals(TextDecoration.State.FALSE, find(branch, " B").decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.WHITE, colorOf(bound("&c[A|&rB](if:{x})").render(values("x", "")), "B"));
    }

    @Test
    void branchKeysAreBoundInWrittenOrder() {
        final Text<TestValues> text = bound("[{a}|{b}](if:{c})");
        assertEquals(List.of("a", "b", "c"), text.keys());
        final BoundText<TestValues> binding = text.bind(values("a", "A", "b", "B", "c", ""));
        assertEquals("B", plain(binding.render()));
        assertSame(binding.render(), binding.render());
    }

    // The color in effect for the component whose own text is the given one.
    private static TextColor colorOf(Component component, String content) {
        return colorOf(component, content, null);
    }

    private static TextColor colorOf(Component component, String content, TextColor inherited) {
        final TextColor color = component.color() != null ? component.color() : inherited;
        if (component instanceof TextComponent text && text.content().equals(content)) return color;
        for (final Component child : component.children()) {
            final TextColor found = colorOf(child, content, color);
            if (found != null) return found;
        }
        return null;
    }

    // The component whose own text is the given one, searched depth first.
    private static Component find(Component component, String content) {
        if (component instanceof TextComponent text && text.content().equals(content)) return component;
        for (final Component child : component.children()) {
            final Component found = find(child, content);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void conditionKeysAreBoundInOrderAndBindingsReuseResults() {
        final Text<TestValues> text = bound("[{name}](if:{clan}, unless:{hidden})");
        assertEquals(List.of("name", "clan", "hidden"), text.keys());
        final BoundText<TestValues> binding = text.bind(values("name", "Steve", "clan", "", "hidden", ""));
        assertSame(binding.render(), binding.render());
    }
}
