package com.earthpol.earthpollib.translation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void localeFolderConfigControlsDiskExportDirectory() throws Exception {
        Plugin plugin = plugin(tempDir);
        Path configPath = tempDir.resolve("translations.yml");

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("default-locale", "en-US");
        cfg.set("active-locale", "en-US");
        cfg.set("fallback-to-default", true);
        cfg.set("locale-folder", "custom-locales");
        cfg.save(configPath.toFile());

        TranslationService service = new TranslationService(plugin, TranslationServiceTest.class, "test-translations");
        service.load();

        Path customLocaleFile = tempDir.resolve("custom-locales").resolve("en-US.yml");
        Path defaultLocaleFile = tempDir.resolve("test-translations").resolve("en-US.yml");

        assertTrue(Files.exists(customLocaleFile));
        assertFalse(Files.exists(defaultLocaleFile));
        assertEquals("Hello from tests", service.translate("greeting"));
    }

    private static Plugin plugin(Path dataFolder) {
        Logger logger = Logger.getLogger("EarthPolLibTest-TranslationService");
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> logger;
                    case "getName" -> "TranslationServicePlugin";
                    case "getDataFolder" -> dataFolder.toFile();
                    case "getResource" -> TranslationServiceTest.class.getClassLoader().getResourceAsStream((String) args[0]);
                    case "saveResource" -> {
                        saveResource(dataFolder, (String) args[0]);
                        yield null;
                    }
                    case "toString" -> "Plugin[TranslationServicePlugin]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static void saveResource(Path dataFolder, String resourcePath) throws IOException {
        Path output = dataFolder.resolve(resourcePath);
        Files.createDirectories(output.getParent());
        try (InputStream input = TranslationServiceTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + resourcePath);
            }
            Files.copy(input, output);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        if (type == File.class) return null;
        return null;
    }
}
