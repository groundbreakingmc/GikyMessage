package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompilerOwnershipTest extends TextTestBase {

    @Test
    void neighbouringGroupsKeepTheirOwnChildrenAcrossCapacityBoundaries() {
        for (int firstSize : new int[]{7, 8, 9, 15, 16, 17, 32}) {
            for (int nextSize : new int[]{1, 8, 17}) {
                String raw = group("a", firstSize) + group("b", nextSize);
                List<String> keys = keys("a", firstSize);
                keys.addAll(keys("b", nextSize));
                for (boolean styled : new boolean[]{false, true}) {
                    for (Text<TestValues> text : java.util.List.of(bound(raw), cached(raw))) {
                        List<String> calls = new ArrayList<>();
                        Component rendered = text.render((key, output) -> {
                            calls.add(key);
                            output.component(value(key, styled));
                        });
                        assertEquals(keys, calls, raw);
                        assertValues(keys, rendered, styled, raw);
                        assertValues(keys, text.render((key, output) ->
                                output.component(value(key, styled))), styled, raw);
                    }
                }
            }
        }
    }

    @Test
    void reusedFramesRemainIndependentInsideClosedAndUnclosedParents() {
        for (int size : new int[]{8, 16}) {
            String groups = group("a", size) + group("b", 1) + group("c", size + 1);
            List<String> keys = keys("a", size);
            keys.addAll(keys("b", 1));
            keys.addAll(keys("c", size + 1));
            Component expected = Component.text(String.join("", keys));
            for (String raw : new String[]{"[" + groups + "]", "[" + groups,
                    "[[" + groups + "]]", "[[" + groups}) {
                assertEquals(expected, bound(raw).render((key, output) -> output.text(key)).compact(), raw);
            }
        }
    }

    @Test
    void boundContextChangesInTheFirstGroupAfterLaterGroupsCompile() {
        String raw = group("a", 8) + group("b", 8);
        List<String> keys = keys("a", 8);
        keys.addAll(keys("b", 8));
        List<String> calls = new ArrayList<>();
        Text<Boolean> text = Text.of(raw, key -> (changed, output) -> {
            calls.add(key);
            output.text(changed && key.equals("a0") ? "changed" : key);
        });
        Component first = text.render(false);
        assertEquals(keys, calls);
        assertEquals(Component.text(String.join("", keys)), first.compact());
        calls.clear();
        Component second = text.render(true);
        assertEquals(keys, calls);
        keys.set(0, "changed");
        assertEquals(Component.text(String.join("", keys)), second.compact());
    }

    private static String group(String prefix, int count) {
        StringBuilder result = new StringBuilder("[");
        for (String key : keys(prefix, count)) result.append("[{").append(key).append("}]");
        return result.append(']').toString();
    }

    private static List<String> keys(String prefix, int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(prefix + i);
        return result;
    }

    private static Component value(String key, boolean styled) {
        return styled ? Component.text(key, key.startsWith("a") ? NamedTextColor.RED : NamedTextColor.BLUE)
                .clickEvent(ClickEvent.runCommand("/" + key)) : Component.text(key);
    }

    private static void assertValues(List<String> keys, Component result, boolean styled, String raw) {
        if (!styled) {
            assertEquals(Component.text(String.join("", keys)), result.compact(), raw);
            return;
        }
        List<Component> leaves = new ArrayList<>();
        collectStyledLeaves(result, leaves);
        assertEquals(keys.stream().map(key -> value(key, true)).toList(), leaves, raw);
    }

    // Empty grouping nodes are allowed; every replacement must keep its full style/event data.
    private static void collectStyledLeaves(Component component, List<Component> leaves) {
        TextComponent text = assertInstanceOf(TextComponent.class, component);
        if (!text.content().isEmpty()) {
            assertTrue(text.children().isEmpty());
            leaves.add(text);
        } else {
            assertTrue(text.style().isEmpty());
            for (Component child : text.children()) collectStyledLeaves(child, leaves);
        }
    }
}
