package com.earthpol.earthpollib.messaging;

import net.kyori.adventure.text.format.NamedTextColor;

/** Fallback colors; explicit formatting in a translation takes precedence. */
public enum MessageStyle {
    INFO(NamedTextColor.GRAY),
    SUCCESS(NamedTextColor.GREEN),
    WARNING(NamedTextColor.YELLOW),
    ERROR(NamedTextColor.RED);

    private final NamedTextColor color;

    MessageStyle(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return color;
    }
}
