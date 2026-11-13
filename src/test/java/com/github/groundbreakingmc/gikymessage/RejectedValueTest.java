package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Values that the Adventure used by the tests rejects, such as head names with spaces and
 * URLs that are no URIs. A static one is a template error; a placeholder value must not
 * fail the render.
 */
class RejectedValueTest extends TextTestBase {

    private static TestValues text(String value) {
        return (key, out) -> out.text(value);
    }

    private static boolean containsHead(Component component) {
        if (component instanceof ObjectComponent) return true;
        for (final Component child : component.children()) {
            if (containsHead(child)) return true;
        }
        return false;
    }

    @Test
    void staticHeadNameThatAdventureRejectsFailsToCompile() {
        final TextFormatException failure =
                assertThrows(TextFormatException.class, () -> Text.of("[x](head:Steve Jobs)"));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("Steve Jobs"), failure.getMessage());
    }

    @Test
    void rejectedHeadNameFromAPlaceholderLeavesTheSegmentAsText() {
        final Text<TestValues> head = bound("[&7{name}](head:{name})");
        final Text<TestValues> plain = bound("[&7{name}]");
        assertTrue(containsHead(head.render(text("Steve"))));
        for (final String name : List.of("Steve Jobs", "Игрок", "abcdefghijklmnopq")) {
            assertEquals(plain.render(text(name)), head.render(text(name)), name);
        }
        // An unresolved key renders literally, and this one is too long for a name.
        assertEquals(Text.of("[x]").render(), Text.of("[x](head:{a_long_placeholder})").render());
    }

    @Test
    void bindingReusesTheTextOfARejectedHeadName() {
        final BoundText<TestValues> binding = bound("[{name}](head:{name})").bind(text("Steve Jobs"));
        final Component first = binding.render();
        assertFalse(containsHead(first));
        assertSame(first, binding.render());
    }

    @Test
    void staticClickValueThatAdventureRejectsFailsToCompile() {
        final TextFormatException failure =
                assertThrows(TextFormatException.class, () -> Text.of("[x](url:not a url)"));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertThrows(TextFormatException.class, () -> Text.of("[x](page:0)"));
    }

    @Test
    void rejectedUrlFromAPlaceholderLeavesTheClickOut() {
        final Text<TestValues> link = bound("[Site](url:{link})");
        assertEquals(ClickEvent.openUrl("https://example.com"), link.render(text("https://example.com")).clickEvent());
        assertNull(link.render(text("not set")).clickEvent());

        final Component withHover = bound("[Site](url:{link}, show:{link})").render(text("not set"));
        assertNull(withHover.clickEvent());
        assertNotNull(withHover.hoverEvent());
    }
}
