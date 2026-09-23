package com.earthpol.earthpollib.translation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * A plugin-local translation API intended to be shaded into each plugin that uses EarthPolLib.
 *
 * <p>This service loads locale files from your plugin's jar and server files, keeping all
 * translations local to the owning plugin instead of depending on a shared runtime plugin.</p>
 */
@SuppressWarnings("unused")
public final class TranslationService {
    private final Plugin plugin;
    private final Class<?> owningClass;
    private final String bundledFolder;
    private final Path configPath;
    private Path localeFolderPath;
    private final Map<String, Map<String, String>> translations = new HashMap<>();

    private Locale defaultLocale = Locale.US;
    private Locale activeLocale = Locale.US;
    private boolean fallbackToDefault = true;

    public TranslationService(Plugin plugin, Class<?> owningClass) {
        this(plugin, owningClass, "translations");
    }

    public TranslationService(Plugin plugin, Class<?> owningClass, String bundledFolder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.owningClass = Objects.requireNonNull(owningClass, "owningClass");
        this.bundledFolder = Objects.requireNonNull(bundledFolder, "bundledFolder");
        this.configPath = plugin.getDataFolder().toPath().resolve("translations.yml");
        this.localeFolderPath = plugin.getDataFolder().toPath().resolve(bundledFolder);
    }

    /**
     * Initializes translation files and loads all configured locales into memory.
     */
    public void load() {
        createConfigIfNeeded();
        loadConfigValues();
        Set<String> bundledLocaleFiles = getBundledLocaleFileNames();
        exportBundledLocaleFiles(bundledLocaleFiles);
        ensureDefaultLocaleFileExists(bundledLocaleFiles);
        mergeMissingKeysIntoLocaleFiles(bundledLocaleFiles);
        loadLocalesFromDisk();
        plugin.getLogger().info("Loaded " + translations.size() + " locale(s) for " + plugin.getName() + ".");
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public Locale getActiveLocale() {
        return activeLocale;
    }

    public void setActiveLocale(Locale activeLocale) {
        this.activeLocale = Objects.requireNonNull(activeLocale, "activeLocale");
    }

    public Map<String, Map<String, String>> getTranslations() {
        Map<String, Map<String, String>> snapshot = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : translations.entrySet()) {
            snapshot.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public String translate(String key, Object... args) {
        return translate(key, activeLocale, args);
    }

    public String translate(String key, Locale locale, Object... args) {
        String normalizedKey = normalizeKey(key);
        String localeKey = toLocaleKey(locale);
        String message = find(localeKey, normalizedKey);

        if (message == null && fallbackToDefault) {
            message = find(toLocaleKey(defaultLocale), normalizedKey);
        }

        if (message == null) {
            return key;
        }

        if (args == null || args.length == 0) {
            return message;
        }

        return MessageFormat.format(message, args);
    }

    private String find(String localeKey, String key) {
        Map<String, String> map = translations.get(localeKey);
        return map == null ? null : map.get(key);
    }

    private void createConfigIfNeeded() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            if (Files.exists(configPath)) {
                return;
            }

            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("default-locale", "en-US");
            cfg.set("active-locale", "en-US");
            cfg.set("fallback-to-default", true);
            cfg.set("locale-folder", bundledFolder);
            cfg.save(configPath.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed creating translations.yml for " + plugin.getName(), e);
        }
    }

    private void loadConfigValues() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configPath.toFile());
        defaultLocale = toLocale(cfg.getString("default-locale", "en-US"));
        activeLocale = toLocale(cfg.getString("active-locale", cfg.getString("default-locale", "en-US")));
        fallbackToDefault = cfg.getBoolean("fallback-to-default", true);
        String configuredLocaleFolder = cfg.getString("locale-folder", bundledFolder);
        if (configuredLocaleFolder == null || configuredLocaleFolder.isBlank()) {
            configuredLocaleFolder = bundledFolder;
        }
        localeFolderPath = plugin.getDataFolder().toPath().resolve(configuredLocaleFolder);
    }

    private void exportBundledLocaleFiles(Set<String> bundledLocaleFiles) {
        try {
            Files.createDirectories(localeFolderPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create translation locale folder: " + localeFolderPath, e);
        }

        for (String fileName : bundledLocaleFiles) {
            Path out = localeFolderPath.resolve(fileName);
            if (Files.exists(out)) {
                continue;
            }

            copyBundledLocaleFile(fileName, out);
        }
    }

    private Set<String> getBundledLocaleFileNames() {
        Set<String> localeFiles = new java.util.HashSet<>();
        try {
            URI jarUri = owningClass.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path rootPath = Paths.get(jarUri);

            if (Files.isDirectory(rootPath)) {
                Path bundledPath = rootPath.resolve(bundledFolder);
                if (!Files.exists(bundledPath)) {
                    return localeFiles;
                }

                try (Stream<Path> stream = Files.list(bundledPath)) {
                    stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".yml"))
                        .forEach(localeFiles::add);
                }
                return localeFiles;
            }

            // Open the plugin jar as a ZIP filesystem from its path.
            try (FileSystem fs = FileSystems.newFileSystem(rootPath, (ClassLoader) null)) {
                Path inside = fs.getPath("/" + bundledFolder);
                if (!Files.exists(inside)) {
                    return localeFiles;
                }

                try (Stream<Path> stream = Files.list(inside)) {
                    stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".yml"))
                        .forEach(localeFiles::add);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to scan bundled translations in /" + bundledFolder, ex);
        }

        return localeFiles;
    }

    private void ensureDefaultLocaleFileExists(Set<String> bundledLocaleFiles) {
        String defaultLocaleKey = toLocaleKey(defaultLocale);
        if (hasLocaleFile(defaultLocaleKey)) {
            return;
        }

        Map<String, String> bundledByLocale = mapBundledFilesByLocale(bundledLocaleFiles);
        String bundledDefault = bundledByLocale.get(defaultLocaleKey);
        if (bundledDefault != null) {
            copyBundledLocaleFile(bundledDefault, localeFolderPath.resolve(bundledDefault));
            return;
        }

        Path defaultLocalePath = localeFolderPath.resolve(defaultLocaleKey + ".yml");
        try {
            Files.createDirectories(localeFolderPath);
            YamlConfiguration empty = new YamlConfiguration();
            empty.save(defaultLocalePath.toFile());
            plugin.getLogger().warning(
                "Default locale " + defaultLocaleKey + " is not bundled. Created empty file at " + defaultLocalePath
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed creating default locale file " + defaultLocalePath, e);
        }
    }

    private void mergeMissingKeysIntoLocaleFiles(Set<String> bundledLocaleFiles) {
        Map<String, String> bundledByLocale = mapBundledFilesByLocale(bundledLocaleFiles);
        String defaultLocaleKey = toLocaleKey(defaultLocale);
        Map<String, String> defaultTemplate = Optional
            .ofNullable(bundledByLocale.get(defaultLocaleKey))
            .map(this::loadBundledLocaleKeys)
            .or(() -> findLocaleFile(defaultLocaleKey).map(this::loadLocaleKeysFromDisk))
            .orElseGet(Map::of);

        try {
            Files.createDirectories(localeFolderPath);
            try (Stream<Path> stream = Files.list(localeFolderPath)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .forEach(path -> mergeLocaleFile(path, bundledByLocale, defaultTemplate));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed syncing translation keys in " + localeFolderPath, e);
        }
    }

    private void mergeLocaleFile(Path file, Map<String, String> bundledByLocale, Map<String, String> defaultTemplate) {
        String fileName = file.getFileName().toString();
        String localeKey = fileNameToLocaleKey(fileName);

        YamlConfiguration localeCfg = YamlConfiguration.loadConfiguration(file.toFile());
        int added = 0;

        String bundledFileName = bundledByLocale.get(localeKey);
        if (bundledFileName != null) {
            added += addMissingKeys(localeCfg, loadBundledLocaleKeys(bundledFileName));
        }

        if (!defaultTemplate.isEmpty()) {
            added += addMissingKeys(localeCfg, defaultTemplate);
        }

        if (added <= 0) {
            return;
        }

        try {
            localeCfg.save(file.toFile());
            plugin.getLogger().info("Added " + added + " missing translation key(s) to " + fileName + ".");
        } catch (IOException e) {
            throw new IllegalStateException("Failed saving updated locale file " + file, e);
        }
    }

    private int addMissingKeys(YamlConfiguration target, Map<String, String> source) {
        int added = 0;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (target.contains(entry.getKey())) {
                continue;
            }
            target.set(entry.getKey(), entry.getValue());
            added++;
        }
        return added;
    }

    private Map<String, String> loadBundledLocaleKeys(String bundledFileName) {
        String resourcePath = bundledFolder + "/" + bundledFileName;
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                return Map.of();
            }

            YamlConfiguration cfg;
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                cfg = YamlConfiguration.loadConfiguration(reader);
            }

            Map<String, String> flat = new HashMap<>();
            flatten("", cfg, flat);
            return flat;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed reading bundled translation file " + resourcePath, e);
            return Map.of();
        }
    }

    private void copyBundledLocaleFile(String bundledFileName, Path destination) {
        String resourcePath = bundledFolder + "/" + bundledFileName;
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Bundled locale resource not found: " + resourcePath);
            }
            Files.createDirectories(destination.getParent());
            Files.copy(input, destination);
        } catch (IOException e) {
            throw new IllegalStateException("Failed exporting bundled locale file " + resourcePath + " to " + destination, e);
        }
    }

    private Map<String, String> loadLocaleKeysFromDisk(Path localeFile) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(localeFile.toFile());
        Map<String, String> flat = new HashMap<>();
        flatten("", cfg, flat);
        return flat;
    }

    private Map<String, String> mapBundledFilesByLocale(Set<String> bundledLocaleFiles) {
        Map<String, String> out = new HashMap<>();
        for (String fileName : bundledLocaleFiles) {
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                continue;
            }
            out.put(fileNameToLocaleKey(fileName), fileName);
        }
        return out;
    }

    private Optional<Path> findLocaleFile(String localeKey) {
        try {
            Files.createDirectories(localeFolderPath);
            try (Stream<Path> stream = Files.list(localeFolderPath)) {
                return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .filter(path -> localeKey.equals(fileNameToLocaleKey(path.getFileName().toString())))
                    .findFirst();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed locating locale file " + localeKey + " in " + localeFolderPath, e);
        }
    }

    private boolean hasLocaleFile(String localeKey) {
        try {
            Files.createDirectories(localeFolderPath);
            try (Stream<Path> stream = Files.list(localeFolderPath)) {
                return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .map(TranslationService::fileNameToLocaleKey)
                    .anyMatch(localeKey::equals);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed checking existing locale files in " + localeFolderPath, e);
        }
    }

    private void loadLocalesFromDisk() {
        translations.clear();
        try {
            Files.createDirectories(localeFolderPath);
            try (Stream<Path> stream = Files.list(localeFolderPath)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .forEach(this::loadLocaleFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read translations from " + localeFolderPath, e);
        }
    }

    private void loadLocaleFile(Path file) {
        String raw = file.getFileName().toString();
        String localeName = raw.substring(0, raw.length() - 4);
        String localeKey = toLocaleKey(toLocale(localeName));

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file.toFile());
        Map<String, String> flat = translations.computeIfAbsent(localeKey, ignored -> new HashMap<>());
        flatten("", cfg, flat);
    }

    private void flatten(String parent, ConfigurationSection section, Map<String, String> out) {
        for (String key : section.getKeys(false)) {
            String fullKey = parent.isBlank() ? key : parent + "." + key;
            Object value = section.get(key);

            if (value instanceof ConfigurationSection child) {
                flatten(fullKey, child, out);
            } else if (value != null) {
                out.put(normalizeKey(fullKey), String.valueOf(value));
            }
        }
    }

    private static String normalizeKey(String key) {
        return Objects.requireNonNull(key, "key").trim().toLowerCase(Locale.ROOT);
    }

    private static String fileNameToLocaleKey(String fileName) {
        if (fileName.length() < 5 || !fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            throw new IllegalArgumentException("Not a valid locale file name: " + fileName);
        }
        String localeName = fileName.substring(0, fileName.length() - 4);
        return toLocaleKey(toLocale(localeName));
    }

    private static Locale toLocale(String name) {
        String[] parts = name.replace('_', '-').split("-");
        if (parts.length == 1) {
            return Locale.of(parts[0]);
        }
        if (parts.length == 2) {
            return Locale.of(parts[0], parts[1]);
        }
        return Locale.of(parts[0], parts[1], parts[2]);
    }

    private static String toLocaleKey(Locale locale) {
        return locale.toLanguageTag();
    }
}
