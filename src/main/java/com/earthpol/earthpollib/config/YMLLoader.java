package com.earthpol.earthpollib.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and parses values from a Bukkit YAML configuration section.
 *
 * <p>Provides strict type parsing for scalars and lists, delegating to
 * {@link ExtendedParser} for conversion and validation.</p>
 */
@SuppressWarnings("unused")
final class YMLLoader {
    private YMLLoader() {}

    static <T> T get(ConfigurationSection cfg, String path, Class<T> type) {
        Objects.requireNonNull(cfg); Objects.requireNonNull(path); Objects.requireNonNull(type);
        if (!cfg.contains(path)) throw new NoSuchElementException("Missing config key: " + path);

        Class<T> t = TypeUtil.normalize(type);
        Object raw = cfg.get(path);

        try {
            return ExtendedParser.parseFromObject(t, raw);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Failed to parse key '" + path + "' as " + t.getSimpleName() + ": " + String.valueOf(raw), ex
            );
        }
    }

    static <T> T get(FileConfiguration cfg, String path, Class<T> type) {
        return get((ConfigurationSection) cfg, path, type);
    }

    static <T> T getOrDefault(ConfigurationSection cfg, String path, Class<T> type, T def) {
        return cfg.contains(path) ? get(cfg, path, type) : def;
    }

    static <T> Optional<T> getOptional(ConfigurationSection cfg, String path, Class<T> type) {
        return cfg.contains(path) ? Optional.of(get(cfg, path, type)) : Optional.empty();
    }

    static <T> List<T> getList(ConfigurationSection cfg, String path, Class<T> elementType) {
        Objects.requireNonNull(cfg); Objects.requireNonNull(path); Objects.requireNonNull(elementType);
        if (!cfg.contains(path)) return List.of();
        if (!cfg.isList(path)) throw new IllegalArgumentException("Key '" + path + "' is not a YAML list.");

        Class<T> et = TypeUtil.normalize(elementType);
        List<?> raw = Objects.requireNonNull(cfg.getList(path));

        try {
            return ExtendedParser.parseList(et, raw);
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            throw new IllegalArgumentException("Failed to parse list at '" + path + "': " + ex.getMessage(), ex);
        }
    }

    static <T> Set<T> getSet(ConfigurationSection cfg, String path, Class<T> elementType) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(getList(cfg, path, elementType)));
    }

    static <T> List<T> getList(FileConfiguration cfg, String path, Class<T> elementType) {
        return getList((ConfigurationSection) cfg, path, elementType);
    }

    static <T> Set<T> getSet(FileConfiguration cfg, String path, Class<T> elementType) {
        return getSet((ConfigurationSection) cfg, path, elementType);
    }
}
