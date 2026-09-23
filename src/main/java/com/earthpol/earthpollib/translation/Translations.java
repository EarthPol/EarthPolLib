package com.earthpol.earthpollib.translation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Objects;

/**
 * Convenience methods for translating keys into raw strings, colored text, and components.
 */
@SuppressWarnings("unused")
public final class Translations {
    public static final String DEFAULT_PREFIX_KEY = "general.prefix";

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.legacyAmpersand();

    private Translations() {}

    public static Locale locale(TranslationService service, CommandSender sender) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(sender, "sender");
        return TranslationLocales.fromSender(sender, service.getDefaultLocale());
    }

    public static String raw(TranslationService service, String key, Object... args) {
        Objects.requireNonNull(service, "service");
        return Translatable.of(key, args).translate(service);
    }

    public static String raw(TranslationService service, Locale locale, String key, Object... args) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(locale, "locale");
        return Translatable.of(key, args).locale(locale).translate(service);
    }

    public static String raw(TranslationService service, CommandSender sender, String key, Object... args) {
        return raw(service, locale(service, sender), key, args);
    }

    public static String text(TranslationService service, String key, Object... args) {
        return ChatColor.translateAlternateColorCodes('&', raw(service, key, args));
    }

    public static String text(TranslationService service, Locale locale, String key, Object... args) {
        return ChatColor.translateAlternateColorCodes('&', raw(service, locale, key, args));
    }

    public static String text(TranslationService service, CommandSender sender, String key, Object... args) {
        return text(service, locale(service, sender), key, args);
    }

    public static Component component(TranslationService service, String key, Object... args) {
        return LEGACY_SERIALIZER.deserialize(raw(service, key, args));
    }

    public static Component component(TranslationService service, Locale locale, String key, Object... args) {
        return LEGACY_SERIALIZER.deserialize(raw(service, locale, key, args));
    }

    public static Component component(TranslationService service, CommandSender sender, String key, Object... args) {
        return LEGACY_SERIALIZER.deserialize(raw(service, sender, key, args));
    }

    public static void send(TranslationService service, CommandSender sender, String key, Object... args) {
        Objects.requireNonNull(sender, "sender");
        sender.sendMessage(text(service, sender, key, args));
    }

    public static void sendPrefixed(TranslationService service, CommandSender sender, String key, Object... args) {
        sendPrefixed(service, sender, DEFAULT_PREFIX_KEY, key, args);
    }

    public static void sendPrefixed(
        TranslationService service,
        CommandSender sender,
        String prefixKey,
        String key,
        Object... args
    ) {
        Objects.requireNonNull(sender, "sender");
        sender.sendMessage(prefixed(service, sender, prefixKey, key, args));
    }

    public static String prefixed(TranslationService service, CommandSender sender, String key, Object... args) {
        return prefixed(service, sender, DEFAULT_PREFIX_KEY, key, args);
    }

    public static String prefixed(
        TranslationService service,
        CommandSender sender,
        String prefixKey,
        String key,
        Object... args
    ) {
        return text(service, sender, prefixKey) + text(service, sender, key, args);
    }

    public static String prefixed(TranslationService service, String key, Object... args) {
        return prefixed(service, DEFAULT_PREFIX_KEY, key, args);
    }

    public static String prefixed(TranslationService service, String prefixKey, String key, Object... args) {
        return text(service, prefixKey) + text(service, key, args);
    }
}
