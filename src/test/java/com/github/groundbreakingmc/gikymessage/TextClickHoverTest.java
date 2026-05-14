package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for click events, hover events, their combinations, and insertion.
 * Covers static (literal) values; for dynamic (placeholder) variants see
 * {@link TextDynamicTest}.
 */
@DisplayName("Click & hover events")
class TextClickHoverTest extends TextTestBase {

    // ════════════════════════════════════════════════════════════════════════
    //  Click events
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Click events")
    class ClickEvents {

        @Test
        @DisplayName("run command")
        void runCommand() {
            assertRenders(
                    Component.text("Run").clickEvent(ClickEvent.runCommand("/spawn")),
                    "[Run](run:/spawn)"
            );
        }

        @Test
        @DisplayName("suggest command")
        void suggestCommand() {
            assertRenders(
                    Component.text("Suggest").clickEvent(ClickEvent.suggestCommand("/gamemode ")),
                    "[Suggest](suggest:/gamemode )"
            );
        }

        @Test
        @DisplayName("open URL")
        void openUrl() {
            assertRenders(
                    Component.text("URL").clickEvent(ClickEvent.openUrl("https://minecraft.net")),
                    "[URL](url:https://minecraft.net)"
            );
        }

        @Test
        @DisplayName("copy to clipboard")
        void copyToClipboard() {
            assertRenders(
                    Component.text("Copy").clickEvent(ClickEvent.copyToClipboard("some text")),
                    "[Copy](copy:\"some text\")"
            );
        }

        @Test
        @DisplayName("change page")
        void changePage() {
            assertRenders(
                    Component.text("Page 2").clickEvent(ClickEvent.changePage(2)),
                    "[Page 2](page:2)"
            );
        }

        @Test
        @DisplayName("Colored text with run command")
        void coloredTextRunCommand() {
            assertRenders(
                    Component.text("Go", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/spawn")),
                    "&a[Go](run:/spawn)"
            );
        }

        @Test
        @DisplayName("Colored text inside block with run command")
        void coloredTextInsideBlockRunCommand() {
            assertRenders(
                    Component.text("Go", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/spawn")),
                    "[&aGo](run:/spawn)"
            );
        }

        @Test
        @DisplayName("Bold text with suggest command")
        void boldTextSuggestCommand() {
            assertRenders(
                    Component.text("Msg", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.suggestCommand("/msg ")),
                    "&e&l[Msg](suggest:/msg )"
            );
        }

        @Test
        @DisplayName("run command — command with arguments")
        void runCommandWithArguments() {
            assertRenders(
                    Component.text("TP").clickEvent(ClickEvent.runCommand("/tp 100 64 200")),
                    "[TP](run:/tp 100 64 200)"
            );
        }

        @Test
        @DisplayName("open URL — URL with query string")
        void urlWithQueryString() {
            assertRenders(
                    Component.text("Link").clickEvent(ClickEvent.openUrl("https://example.com/search?q=hello&page=1")),
                    "[Link](url:https://example.com/search?q=hello&page=1)"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Hover events
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Hover events")
    class HoverEvents {

        @Test
        @DisplayName("Plain hover text")
        void plainHover() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(Component.text("Hello!"))),
                    "[Hover](show:Hello!)"
            );
        }

        @Test
        @DisplayName("Bold hover text")
        void boldHover() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(
                            Component.text("Bold", Style.style(TextDecoration.BOLD)))),
                    "[Hover](show:&lBold)"
            );
        }

        @Test
        @DisplayName("Colored hover text")
        void coloredHover() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(
                            Component.text("Green!", NamedTextColor.GREEN))),
                    "[Hover](show:&aGreen!)"
            );
        }

        @Test
        @DisplayName("Bold + colored hover text")
        void boldColoredHover() {
            assertRenders(
                    Component.text("Hover").hoverEvent(HoverEvent.showText(
                            Component.text("Bold red", NamedTextColor.RED, TextDecoration.BOLD))),
                    "[Hover](show:&c&lBold red)"
            );
        }

        @Test
        @DisplayName("Colored trigger text + plain hover")
        void coloredTriggerPlainHover() {
            assertRenders(
                    Component.text("Tip", NamedTextColor.AQUA)
                            .hoverEvent(HoverEvent.showText(Component.text("Here"))),
                    "&d[Tip](show:Here)"
            );
        }

        @Test
        @DisplayName("Color code before bracket — style applied to block text")
        void colorBeforeBlockText() {
            assertRenders(
                    Component.text("Info", NamedTextColor.GOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Detail"))),
                    "[&6Info](show:Detail)"
            );
        }

        @Test
        @DisplayName("Hover text with single quotes (quoted value)")
        void quotedHoverValue() {
            assertRenders(
                    Component.text("X").hoverEvent(HoverEvent.showText(Component.text("Has spaces here"))),
                    "[X](show:'Has spaces here')"
            );
        }

        @Test
        @DisplayName("Hover with multi-line text (\\n in value)")
        void multilineHoverText() {
            assertRenders(
                    Component.text("X").hoverEvent(HoverEvent.showText(Component.text("Line1\nLine2"))),
                    "[X](show:Line1\\nLine2)"
            );
        }

        @Test
        @DisplayName("Italic trigger text + colored hover")
        void italicTriggerColoredHover() {
            assertRenders(
                    Component.text("Peek", NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC)
                            .hoverEvent(HoverEvent.showText(Component.text("Boo!", NamedTextColor.YELLOW))),
                    "&b&o[Peek](show:&eBoo!)"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Click + hover combined
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Click and hover combined")
    class ClickAndHover {

        @Test
        @DisplayName("run + show")
        void runAndShow() {
            assertRenders(
                    Component.text("Click or hover")
                            .clickEvent(ClickEvent.runCommand("/spawn"))
                            .hoverEvent(HoverEvent.showText(Component.text("Teleport to spawn"))),
                    "[Click or hover](run:/spawn, show:Teleport to spawn)"
            );
        }

        @Test
        @DisplayName("show + run (reversed action order)")
        void showThenRun() {
            // Verifying that action order inside the parens does not matter
            assertRenders(
                    Component.text("Click or hover")
                            .clickEvent(ClickEvent.runCommand("/spawn"))
                            .hoverEvent(HoverEvent.showText(Component.text("Teleport to spawn"))),
                    "[Click or hover](show:Teleport to spawn, run:/spawn)"
            );
        }

        @Test
        @DisplayName("Colored text with run + show (quoted value)")
        void coloredRunAndShowQuoted() {
            assertRenders(
                    Component.text("Buy", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/shop buy diamond"))
                            .hoverEvent(HoverEvent.showText(Component.text("Price: 10 coins"))),
                    "&a[Buy](run:/shop buy diamond, show:\"Price: 10 coins\")"
            );
        }

        @Test
        @DisplayName("suggest + show")
        void suggestAndShow() {
            assertRenders(
                    Component.text("Help")
                            .clickEvent(ClickEvent.suggestCommand("/help "))
                            .hoverEvent(HoverEvent.showText(Component.text("Open help menu"))),
                    "[Help](suggest:/help , show:Open help menu)"
            );
        }

        @Test
        @DisplayName("url + show")
        void urlAndShow() {
            assertRenders(
                    Component.text("Visit")
                            .clickEvent(ClickEvent.openUrl("https://example.com"))
                            .hoverEvent(HoverEvent.showText(Component.text("Open website"))),
                    "[Visit](url:https://example.com, show:Open website)"
            );
        }

        @Test
        @DisplayName("run + colored show")
        void runAndColoredShow() {
            assertRenders(
                    Component.text("Alert", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/alert"))
                            .hoverEvent(HoverEvent.showText(Component.text("Warning!", NamedTextColor.YELLOW))),
                    "&c[Alert](run:/alert, show:&eWarning!)"
            );
        }

        @Test
        @DisplayName("Bold-colored text with url + bold show")
        void boldColoredUrlBoldShow() {
            assertRenders(
                    Component.text("Docs", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.openUrl("https://docs.example.com"))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Read the docs", Style.style(TextDecoration.BOLD)))),
                    "&d&l[Docs](url:https://docs.example.com, show:&lRead the docs)"
            );
        }

        @Test
        @DisplayName("copy + show")
        void copyAndShow() {
            assertRenders(
                    Component.text("Copy IP")
                            .clickEvent(ClickEvent.copyToClipboard("play.example.com"))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy server IP"))),
                    "[Copy IP](copy:\"play.example.com\", show:Click to copy server IP)"
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Insertion (shift-click)
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Insertion")
    class Insertion {

        @Test
        @DisplayName("Quoted insertion value")
        void quotedInsertion() {
            assertRenders(
                    Component.text("Shift-click").insertion("hello "),
                    "[Shift-click](insert:\"hello \")"
            );
        }

        @Test
        @DisplayName("Insertion with click event")
        void insertionWithClick() {
            assertRenders(
                    Component.text("Go")
                            .clickEvent(ClickEvent.runCommand("/spawn"))
                            .insertion("spawn"),
                    "[Go](run:/spawn, insert:\"spawn\")"
            );
        }

        @Test
        @DisplayName("Insertion with hover event")
        void insertionWithHover() {
            assertRenders(
                    Component.text("Name")
                            .hoverEvent(HoverEvent.showText(Component.text("Shift to insert name")))
                            .insertion("Steve"),
                    "[Name](show:Shift to insert name, insert:\"Steve\")"
            );
        }
    }
}
