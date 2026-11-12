package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextLangTest extends TextTestBase {

    private static TestValues values(Map<String, String> values) {
        return (key, value) -> value.text(values.get(key));
    }

    @Test
    void segmentTextBecomesTheFallback() {
        assertEquals(Component.translatable("item.minecraft.diamond", "Diamond"),
                Text.of("[Diamond](lang:item.minecraft.diamond)").render());
        assertEquals(Component.translatable("item.minecraft.diamond", "Diamond"),
                Text.of("[Diamond](lang:\"item.minecraft.diamond\")").render());
    }

    @Test
    void emptySegmentHasNoFallback() {
        assertEquals(Component.translatable("item.minecraft.diamond"),
                Text.of("[](lang:item.minecraft.diamond)").render());
    }

    @Test
    void translationTakesTheFormattingAndEventsOfTheSegment() {
        final Component gold = Component.translatable("myserver.vip", "VIP").color(NamedTextColor.GOLD);
        assertEquals(gold, Text.of("[&6VIP](lang:myserver.vip)").render());
        assertEquals(gold, Text.of("&6[VIP](lang:myserver.vip)").render());
        assertEquals(Component.translatable("myserver.vip", "VIP", Style.style()
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/vip"))
                        .hoverEvent(HoverEvent.showText(Component.text("tip")))
                        .build()),
                Text.of("[&cVIP](lang:myserver.vip, run:\"/vip\", show:tip)").render());
    }

    @Test
    void nestedFormattingIsReducedToTheFallbackText() {
        assertEquals(Component.translatable("myserver.pair", "AB").color(NamedTextColor.GREEN),
                Text.of("&a[A&cB](lang:myserver.pair)").render().compact());
    }

    @Test
    void followingTextStaysOrdinaryText() {
        final Component rendered = bound("[VIP](lang:myserver.vip) {name}").render(values(Map.of("name", "Steve")));
        assertEquals(Component.text()
                        .append(Component.translatable("myserver.vip", "VIP"))
                        .append(Component.text(" Steve"))
                        .build(),
                rendered);
    }

    @Test
    void placeholdersInTheKeyAndTheFallbackAreResolvedPerRender() {
        final Text<TestValues> text = bound("[{rank}](lang:myserver.rank.{rank})");
        assertEquals(List.of("rank"), text.keys());
        assertEquals(Component.translatable("myserver.rank.vip", "vip"), text.render(values(Map.of("rank", "vip"))));
        assertEquals(Component.translatable("myserver.rank.admin", "admin"), text.render(values(Map.of("rank", "admin"))));

        final Text<TestValues> level = bound("[Level {level}](lang:myserver.level)");
        assertEquals(Component.translatable("myserver.level", "Level 5"), level.render(values(Map.of("level", "5"))));
    }

    @Test
    void emptyKeyLeavesTheSegmentAsOrdinaryText() {
        final TestValues values = values(Map.of("name", "Steve", "key", ""));
        assertEquals(bound("[{name}]").render(values), bound("[{name}](lang:{key})").render(values));
        assertEquals(Text.of("[VIP]").render(), Text.of("[VIP](lang:\"\")").render());
    }

    @Test
    void headWinsOverLangAndLangWinsOverGradient() {
        assertInstanceOf(ObjectComponent.class, Text.of("[x](lang:myserver.x, head:Steve)").render());
        assertEquals(Component.translatable("myserver.ab", "AB"),
                Text.of("[AB](gradient:#ff0000-#0000ff, lang:myserver.ab)").render());
        assertEquals(Component.translatable("myserver.second", "x"),
                Text.of("[x](lang:myserver.first, lang:myserver.second)").render());
    }

    @Test
    void colorAndStyleActionsApplyToTheTranslation() {
        assertEquals(Component.translatable("myserver.vip", "VIP").color(NamedTextColor.RED),
                Text.of("[VIP](lang:myserver.vip, color:red)").render());
        assertEquals(Component.translatable("myserver.vip", "VIP").color(NamedTextColor.RED)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                Text.of("[VIP](lang:myserver.vip, style:\"red bold\")").render());
    }

    @Test
    void staticTranslationIsBuiltOnceAndBindingsReuseDynamicOnes() {
        final Text<Void> constant = Text.of("[Diamond](lang:item.minecraft.diamond)");
        assertSame(constant.render(), constant.render());

        final BoundText<TestValues> binding = bound("[{rank}](lang:myserver.rank.{rank})")
                .bind(values(Map.of("rank", "vip")));
        assertSame(binding.render(), binding.render());
    }

    @Test
    void theAdventureOnTheTestClassPathHasFallbacks() {
        assertTrue(Platform.TRANSLATION_FALLBACKS);
    }
}
