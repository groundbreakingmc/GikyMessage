package com.github.groundbreakingmc.gikymessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The library is compiled against a newer Adventure than many platforms provide. A copy of
 * the library that cannot see the types added for shadow colors (4.18) and object
 * components (4.25) must still compile and render everything else, and must reject those
 * two features while compiling instead of failing to link.
 */
class OlderAdventureTest {

    private static final String[] NEWER_TYPES = {
            "net.kyori.adventure.text.format.ShadowColor",
            "net.kyori.adventure.util.ARGBLike",
            "net.kyori.adventure.text.object.",
            "net.kyori.adventure.text.ObjectComponent",
    };

    @Test
    void everythingElseCompilesAndRendersWithoutTheNewerTypes() throws Exception {
        final String[] templates = {
                "Hello",
                "&aHello &b&lWorld&r!",
                "&#ff8800{name} &7&o({health})",
                "[{name}](gradient:#ff5555-#5555ff) [static](gradient:a-b)",
                "[{name}](color:red) [{health}](style:\"gold bold\")",
                "[Click](run:\"/msg {name}\", show:\"&aHi {name}\", insert:text)",
                "[Page](page:2) [Copy](copy:{name}) [Link](url:https://example.com)",
        };
        try (IsolatedLibrary library = new IsolatedLibrary(NEWER_TYPES)) {
            for (final String template : templates) {
                assertEquals(Text.of(template).render(), library.render(library.compile(template)), template);

                final Text<Object> expected = Text.of(template, key -> (context, value) -> value.text(key));
                assertEquals(expected.render(null), library.render(library.compileBound(template, false)), template);
                assertEquals(expected.render(null), library.render(library.compileBound(template, true)), template);
                assertEquals(expected.render(null), library.renderBound(library.compileBound(template, false)), template);
            }
        }
    }

    @Test
    void shadowColorsAreRejectedWhileCompiling() throws Exception {
        try (IsolatedLibrary library = new IsolatedLibrary(NEWER_TYPES)) {
            assertUnsupported(library, "$#000000&fShadowed", "4.18");
            assertUnsupported(library, "&a{name} $#fff{health}", "4.18");
            assertUnsupported(library, "[Hover](show:\"$#000000{name}\")", "4.18");
            // Not a shadow code, so nothing newer is needed.
            assertEquals(Text.of("$5 and $#xyz").render(), library.render(library.compile("$5 and $#xyz")));
        }
    }

    @Test
    void headsAndSpritesAreRejectedWhileCompiling() throws Exception {
        try (IsolatedLibrary library = new IsolatedLibrary(NEWER_TYPES)) {
            assertUnsupported(library, "[x](head:Steve)", "4.25");
            assertUnsupported(library, "[x](head:{name})", "4.25");
            assertUnsupported(library, "[x](sprite:minecraft:item/diamond)", "4.25");
            // A gradient is the selected content action only where no object is requested.
            assertUnsupported(library, "[x](gradient:#f00-#00f, head:Steve)", "4.25");
        }
    }

    @Test
    void theAdventureOnTheTestClassPathSupportsBoth() {
        assertTrue(Platform.SHADOW_COLORS);
        assertTrue(Platform.OBJECT_COMPONENTS);
    }

    private static void assertUnsupported(IsolatedLibrary library, String template, String version) {
        final IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> library.compile(template), template);
        assertEquals(TextFormatException.class.getName(), failure.getClass().getName(), template);
        assertTrue(failure.getMessage().contains("Adventure " + version), failure.getMessage());
    }
}
