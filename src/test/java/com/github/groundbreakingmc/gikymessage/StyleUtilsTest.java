package com.github.groundbreakingmc.gikymessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StyleUtilsTest {

    @Test
    void readsExactlySixHexDigits() {
        assertEquals(0xABCDEF, StyleUtils.parseHex("x#AbCdEf0".toCharArray(), 2));
        assertEquals(0, StyleUtils.parseHex("000000".toCharArray(), 0));
        for (final String invalid : new String[]{"", "a", "abc", "abcde", "abcdeg", "#abcde"}) {
            assertEquals(-1, StyleUtils.parseHex(invalid.toCharArray(), 0), invalid);
        }
    }

    @Test
    void colorValuesHaveNoShortForm() {
        assertEquals(0xFF5555, StyleUtils.parseColor("#ff5555").value());
        assertEquals(0xFF5555, StyleUtils.parseColor("&#FF5555").value());
        for (final String invalid : new String[]{"#f55", "&#f55", "#ff55", "#ff555", "#ff55555"}) {
            assertNull(StyleUtils.parseColor(invalid), invalid);
        }
    }
}
