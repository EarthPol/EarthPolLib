package com.earthpol.earthpollib.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandPageTest {
    @Test
    void pagesAreImmutableAndNavigationUsesAdjacentPages() {
        List<String> source = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
        CommandPage<String> page = CommandPage.of(source, 2, 2);
        source.set(2, "changed");
        assertEquals(List.of("c", "d"), page.items());
        assertEquals(3, page.totalPages());
        assertEquals(5, page.totalItems());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
        Component nav = page.navigation(n -> "/town list " + n, Component.text("Back"), Component.text("Next"));
        assertEquals(ClickEvent.runCommand("/town list 1"), nav.children().getFirst().clickEvent());
        assertEquals(ClickEvent.runCommand("/town list 3"), nav.children().getLast().clickEvent());
    }

    @Test
    void validatesEmptyPagesAndBoundsWithoutIntegerOverflow() {
        CommandPage<Object> empty = CommandPage.of(List.of(), 1, Integer.MAX_VALUE);
        assertEquals(1, empty.totalPages());
        assertFalse(empty.hasNext());
        assertFalse(empty.hasPrevious());
        assertEquals(List.of(1, 2), CommandPage.of(List.of(1, 2), 1, Integer.MAX_VALUE).items());
        assertThrows(IllegalArgumentException.class, () -> CommandPage.of(List.of(1), 2, 1));
        assertThrows(IllegalArgumentException.class, () -> CommandPage.of(List.of(1), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> CommandPage.of(List.of(1), 1, 0));
    }
}
