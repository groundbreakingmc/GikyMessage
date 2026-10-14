package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValueResolverTest extends TextTestBase {

    @Test
    void barePlaceholderReusesPlainComponentAndKeepsStyledChildren() {
        Text<Component> text = Text.of("{value}", key -> (context, value) -> value.component(context));
        Component plain = Component.text("Ж😀");
        assertSame(plain, text.render(plain));
        Component styled = Component.text("red", NamedTextColor.RED).append(Component.text("child"));
        assertEquals(Component.empty().append(styled), text.render(styled));
        assertEquals(Component.text("{value}"), text.render());
    }

    @Test
    void ordinaryRenderAcceptsTextNumbersAndStyledComponents() {
        Component styled = Component.text("open", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/open"));
        for (Text<TestValues> text : java.util.List.of(bound("{text}/{number}/{component}"),
                cached("{text}/{number}/{component}"))) {
            for (long number : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                Component rendered = text.render((key, value) -> {
                    switch (key) {
                        case "text" -> value.text("&a{literal}");
                        case "number" -> value.number(number);
                        case "component" -> value.component(styled);
                    }
                });
                assertEquals(text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("text") ? Component.text("&a{literal}") : slotKey.equals("number") ? Component.text(number) : slotKey.equals("component") ? styled : null)), rendered);
            }
        }
    }

    @Test
    void absentNullEmptyAndLastReportedValuesAreDistinct() {
        Text<TestValues> text = bound("{first}/{absent}/{nullText}/{nullComponent}/{empty}/{last}");
        Component actual = text.render((key, value) -> {
            switch (key) {
                case "first" -> value.number(7);
                case "nullText" -> { value.number(8); value.text(null); }
                case "nullComponent" -> { value.text("discarded"); value.component(null); }
                case "empty" -> value.text("");
                case "last" -> { value.number(9); value.component(Component.text("old")); value.text("new"); }
            }
        });
        assertEquals(text.render((slotKey, slotValue) -> slotValue.component(
                        slotKey.equals("first") ? Component.text(7) : slotKey.equals("empty") ? Component.empty() : slotKey.equals("last") ? Component.text("new") : null)), actual);
        assertEquals(text.render(), text.render((key, value) -> {}));
    }

    @Test
    void cacheKeepsComponentEqualityAcrossNativeValueTypes() {
        Text<TestValues> text = cached("{value}");
        Component first = text.render((key, value) -> value.number(42));
        assertSame(first, text.render((key, value) -> value.text("42")));
        assertSame(first, text.render((key, value) -> value.component(Component.text("42"))));
        Component changed = text.render((key, value) -> value.number(43));
        assertNotEquals(first, changed);
        assertSame(changed, text.render((key, value) -> value.number(43)));
        assertEquals(bound("{value}").render(), text.render());
    }

    @Test
    void nestedRenderingOfSameTextDoesNotOverwriteOuterReceiver() {
        for (Text<TestValues> text : java.util.List.of(bound("{a}/{b}"), cached("{a}/{b}"))) {
            List<String> calls = new ArrayList<>();
            Component actual = text.render((key, value) -> {
                calls.add("outer:" + key);
                if (key.equals("a")) {
                    value.text("outer");
                    Component nested = text.render((nestedKey, nestedValue) -> {
                        calls.add("inner:" + nestedKey);
                        nestedValue.text("inner");
                    });
                    assertEquals(Component.text("inner/inner"), nested);
                } else {
                    value.number(2);
                }
            });
            assertEquals(Component.text("outer/2"), actual);
            assertEquals(List.of("outer:a", "inner:a", "inner:b", "outer:b"), calls);
        }
    }

    @Test
    void resolutionFailureStopsCallsAndDoesNotPolluteNextRender() {
        Text<TestValues> text = cached("{a}/{b}");
        Component original = text.render((key, value) -> value.text("original"));
        for (Throwable failure : List.of(new IllegalStateException("resolver"), new AssertionError("resolver"))) {
            List<String> calls = new ArrayList<>();
            Throwable actual = assertThrows(failure.getClass(), () -> text.render((key, value) -> {
                calls.add(key);
                value.text("partial");
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw (Error) failure;
            }));
            assertSame(failure, actual);
            assertEquals(List.of("a"), calls);
            assertSame(original, text.render((key, value) -> value.text("original")));
            assertEquals(bound("{a}/{b}").render(), text.render((key, value) -> {}));
            original = text.render((key, value) -> value.text("original"));
        }
    }

    @Test
    void staticTextDoesNotCallResolver() {
        for (Text<TestValues> text : java.util.List.of(bound(""), bound("static"), cached("static"))) {
            assertEquals(text.render(), text.render((key, value) -> fail("Static text resolved a key")));
        }
    }
}
