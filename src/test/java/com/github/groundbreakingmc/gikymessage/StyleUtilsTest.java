package com.github.groundbreakingmc.gikymessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleUtilsTest {

    @Test
    void expandsEveryShortHexColor() {
        for (int value = 0; value < 4096; value++) {
            final String hex = String.format("#%03x", value);
            final int expected = (((value >>> 8) & 15) * 17 << 16)
                    | (((value >>> 4) & 15) * 17 << 8) | ((value & 15) * 17);
            assertEquals((4L << 32) | expected, StyleUtils.parseHexPacked(hex.toCharArray(), 0));
        }
    }

    @Test
    void preservesHexBoundariesAndOffsets() {
        assertEquals((7L << 32) | 0xABCDEF, StyleUtils.parseHexPacked("x#AbCdEf0".toCharArray(), 1));
        assertEquals((4L << 32) | 0xAABBCC, StyleUtils.parseHexPacked("#aBc!".toCharArray(), 0));
        for (final String invalid : new String[]{"", "#", "#a", "#ab", "#abcd", "#abcde", "#xyz", "abcdef"}) {
            assertEquals(-1L, StyleUtils.parseHexPacked(invalid.toCharArray(), 0), invalid);
        }
    }
}
