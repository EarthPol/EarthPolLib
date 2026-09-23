package com.earthpol.earthpollib.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** An immutable, one-based page of command results. An empty collection has one empty page. */
public final class CommandPage<T> {
    private final List<T> items;
    private final int number;
    private final int totalPages;
    private final int totalItems;

    private CommandPage(List<T> items, int number, int totalPages, int totalItems) {
        this.items = items;
        this.number = number;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
    }

    public static <T> CommandPage<T> of(List<T> items, int number, int pageSize) {
        Objects.requireNonNull(items, "items");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        int total = items.size();
        int pages = total == 0 ? 1 : 1 + (total - 1) / pageSize;
        if (number < 1 || number > pages) throw new IllegalArgumentException("Page must be between 1 and " + pages);
        int start = (number - 1) * pageSize;
        int end = (int) Math.min((long) start + pageSize, total);
        return new CommandPage<>(List.copyOf(items.subList(start, end)), number, pages, total);
    }

    public List<T> items() { return items; }
    public int number() { return number; }
    public int totalPages() { return totalPages; }
    public int totalItems() { return totalItems; }
    public boolean hasPrevious() { return number > 1; }
    public boolean hasNext() { return number < totalPages; }

    /** The command factory receives the destination page number; labels may be translated components. */
    public Component navigation(IntFunction<String> command, Component previousLabel, Component nextLabel) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(previousLabel, "previousLabel");
        Objects.requireNonNull(nextLabel, "nextLabel");
        Component controls = Component.empty();
        if (hasPrevious()) controls = controls.append(previousLabel.clickEvent(ClickEvent.runCommand(command.apply(number - 1))));
        if (hasPrevious() && hasNext()) controls = controls.append(Component.text(" | "));
        if (hasNext()) controls = controls.append(nextLabel.clickEvent(ClickEvent.runCommand(command.apply(number + 1))));
        return controls;
    }
}
