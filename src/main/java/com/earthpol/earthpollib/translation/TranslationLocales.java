package com.earthpol.earthpollib.translation;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Utilities for resolving locale values from Bukkit objects.
 */
@SuppressWarnings("unused")
public final class TranslationLocales {
    private TranslationLocales() {}

    public static Locale fromSender(CommandSender sender, Locale fallback) {
        if (sender instanceof Player player) {
            return player.locale();
        }
        return fallback;
    }
}
