package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A group that opens a template applies its actions and inner style to its own text only.
 * It must stay a sibling of what follows: as the root component it would hand its events,
 * colour and decorations down to the rest of the template.
 */
class LeadingGroupTest extends TextTestBase {

    private static final ClickEvent SPAWN = ClickEvent.runCommand("/spawn");

    private static Component siblings(Component... children) {
        return Component.text().append(children).build();
    }

    @Test
    void followingTextDoesNotInheritEvents() {
        assertEquals(siblings(Component.text("Click").clickEvent(SPAWN), Component.text(" rest")),
                Text.of("[Click](run:\"/spawn\") rest").render());
        assertEquals(siblings(Component.text("A").insertion("x"), Component.text("B")),
                Text.of("[A](insert:x)B").render());
        assertEquals(siblings(Component.text("A").hoverEvent(HoverEvent.showText(Component.text("tip"))), Component.text("B")),
                Text.of("[A](show:tip)B").render());
    }

    @Test
    void followingTextDoesNotInheritStyleSetInsideTheGroup() {
        assertEquals(siblings(Component.text("A", NamedTextColor.RED), Component.text("B")),
                Text.of("[&cA]B").render());
        assertEquals(siblings(Component.text("A").decorate(TextDecoration.BOLD), Component.text("B"),
                        Component.text("C", NamedTextColor.RED)),
                Text.of("[&lA]B&cC").render());
    }

    @Test
    void styleSetBeforeTheGroupStillAppliesToBoth() {
        assertEquals(siblings(Component.text("A", NamedTextColor.GREEN).clickEvent(SPAWN),
                        Component.text("B", NamedTextColor.GREEN)),
                Text.of("&a[A](run:\"/spawn\")B").render());
    }

    @Test
    void neighbouringGroupsKeepTheirOwnEvents() {
        assertEquals(siblings(Component.text("A").clickEvent(SPAWN),
                        Component.text("B").clickEvent(ClickEvent.suggestCommand("/home"))),
                Text.of("[A](run:\"/spawn\")[B](suggest:/home)").render());
    }

    @Test
    void followingPlaceholderDoesNotInheritEvents() {
        final Component rendered = bound("[Click](run:\"/spawn\") {name}")
                .render((key, value) -> value.text("Steve"));
        assertEquals(siblings(Component.text("Click").clickEvent(SPAWN), Component.text(" Steve")), rendered);
    }

    @Test
    void aLoneGroupAndTextBeforeAGroupKeepTheirShape() {
        assertEquals(Component.text("Click").clickEvent(SPAWN), Text.of("[Click](run:\"/spawn\")").render());
        assertEquals(Component.text("Text ", NamedTextColor.GREEN)
                        .append(Component.text("A").clickEvent(SPAWN)).append(Component.text(" B")),
                Text.of("&aText [A](run:\"/spawn\") B").render());
    }
}
