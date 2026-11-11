package com.github.groundbreakingmc.gikymessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextKeysTest extends TextTestBase {

    @Test
    void staticTextsHaveNoKeys() {
        assertTrue(Text.EMPTY.keys().isEmpty());
        assertTrue(Text.of(null).keys().isEmpty());
        assertTrue(Text.of("Plain text").keys().isEmpty());
        assertTrue(Text.of("&aStyled [group](run:\"/spawn\")").keys().isEmpty());
        assertTrue(Text.of("\\{escaped\\} and {unclosed").keys().isEmpty());
    }

    @Test
    void keysAreUniqueCaseSensitiveAndInRegistrationOrder() {
        assertEquals(List.of("b", "a", "A", "c", "d"),
                Text.of("{b} &c{a} {b} {A} [{c}](show:\"{d} {a}\")").keys());
        assertEquals(List.of("", "owner:name", "papi_some_key"),
                Text.of("{} {owner:name} {papi_some_key}").keys());
    }

    @Test
    void keysOfContentReplacedByAnActionAreIncluded() {
        assertEquals(List.of("inner", "name"), Text.of("[{inner}](head:{name})").keys());
    }

    @Test
    void orderMatchesBindingAndKeysWithoutHandlerRemain() {
        final List<String> requested = new ArrayList<>();
        final Text<TestValues> text = Text.cacheableOf("{first} [{second}](color:{third}) {first}", key -> {
            requested.add(key);
            return key.equals("second") ? null : (context, value) -> value.text(key);
        });
        assertEquals(requested, text.keys());
        assertEquals(List.of("first", "second", "third"), text.keys());
    }

    @Test
    void keysCannotBeModified() {
        final Text<TestValues> text = bound("{a} {b} {c}");
        final List<String> keys = text.keys();
        assertThrows(UnsupportedOperationException.class, () -> keys.set(0, "changed"));
        assertThrows(UnsupportedOperationException.class, () -> keys.add("added"));
        assertEquals(List.of("a", "b", "c"), text.keys());
    }
}
