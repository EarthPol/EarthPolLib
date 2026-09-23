package com.earthpol.earthpollib.messaging;

import com.earthpol.earthpollib.translation.TranslationLocales;
import com.earthpol.earthpollib.translation.TranslationService;
import com.earthpol.earthpollib.translation.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Localized component messages for one plugin. Legacy templates use {@code {name}}; MiniMessage
 * templates use {@code <name>}. Values are inserted as components or literal text, never parsed markup.
 * Existing Translations methods retain their original legacy/MessageFormat behavior.
 */
public final class Messages {
    public enum Format { LEGACY, MINI_MESSAGE }

    private static final Pattern LEGACY_PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_-]+)\\}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final TranslationService translations;
    private final Format format;
    private final String prefixKey;

    public Messages(TranslationService translations) {
        this(translations, Format.LEGACY, Translations.DEFAULT_PREFIX_KEY);
    }

    public Messages(TranslationService translations, Format format) {
        this(translations, format, Translations.DEFAULT_PREFIX_KEY);
    }

    /** A null prefix key omits the chat prefix. */
    public Messages(TranslationService translations, Format format, String prefixKey) {
        this.translations = Objects.requireNonNull(translations, "translations");
        this.format = Objects.requireNonNull(format, "format");
        this.prefixKey = prefixKey;
    }

    public Component component(CommandSender sender, String key, Map<String, ?> values) {
        return component(locale(sender), key, values);
    }

    public Component component(Locale locale, String key, Map<String, ?> values) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(values, "values");
        String template = translations.translate(key, locale);
        if (format == Format.MINI_MESSAGE) {
            TagResolver[] placeholders = values.entrySet().stream()
                    .map(entry -> Placeholder.component(entry.getKey(), value(entry.getValue())))
                    .toArray(TagResolver[]::new);
            return MINI_MESSAGE.deserialize(template, placeholders);
        }
        return LEGACY.deserialize(template).replaceText(TextReplacementConfig.builder()
                .match(LEGACY_PLACEHOLDER)
                .replacement((match, builder) -> values.containsKey(match.group(1))
                        ? value(values.get(match.group(1))) : builder.build())
                .build());
    }

    public Component format(CommandSender sender, MessageStyle style, String key, Map<String, ?> values) {
        Objects.requireNonNull(style, "style");
        Locale locale = locale(sender);
        Component body = component(locale, key, values).colorIfAbsent(style.color());
        return prefixKey == null ? body : Component.empty()
                .append(component(locale, prefixKey, values))
                .append(body);
    }

    public void send(CommandSender sender, MessageStyle style, String key, Map<String, ?> values) {
        sender.sendMessage(format(sender, style, key, values));
    }

    public void send(CommandSender sender, MessageStyle style, String key) {
        send(sender, style, key, Map.of());
    }

    public void success(CommandSender sender, String key, Map<String, ?> values) {
        send(sender, MessageStyle.SUCCESS, key, values);
    }

    public void error(CommandSender sender, String key, Map<String, ?> values) {
        send(sender, MessageStyle.ERROR, key, values);
    }

    public void warning(CommandSender sender, String key, Map<String, ?> values) {
        send(sender, MessageStyle.WARNING, key, values);
    }

    /** Action bars omit the chat prefix. Call on the sender's owning thread. */
    public void actionBar(CommandSender sender, MessageStyle style, String key, Map<String, ?> values) {
        sender.sendActionBar(component(sender, key, values).colorIfAbsent(style.color()));
    }

    private Locale locale(CommandSender sender) {
        return TranslationLocales.fromSender(Objects.requireNonNull(sender, "sender"), translations.getDefaultLocale());
    }

    private static Component value(Object value) {
        Objects.requireNonNull(value, "placeholder value");
        return value instanceof ComponentLike component ? component.asComponent() : Component.text(value.toString());
    }
}
