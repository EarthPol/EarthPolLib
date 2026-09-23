package com.earthpol.earthpollib.config;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ReloadableConfigHandlerTest {
    @TempDir Path directory;
    ReloadableConfigHandler<Settings> handler;

    @BeforeEach
    void createHandler() throws Exception {
        for (Settings key : Settings.values()) reset(key.node());
        handler = new ReloadableConfigHandler<>(plugin(), "settings.yml", Settings.class);
    }

    @Test
    void invalidValuesLeaveAllActiveValuesAndYamlUntouched() throws Exception {
        Map<Settings, Object> before = handler.snapshot();
        Object yaml = handler.yaml();
        String invalid = "count: 8\nmode: unknown\ndatabase: next\nnames: []\n";
        Files.writeString(file(), invalid);
        ConfigReloadResult result = handler.reloadValidated();
        assertFalse(result.applied());
        assertEquals(Set.of("mode", "names"), result.errors().keySet());
        assertEquals(before, handler.snapshot());
        assertSame(yaml, handler.yaml());
        assertEquals(invalid, Files.readString(file()));
    }

    @Test
    void appliesValidChangesAndReportsRestartOnlySettings() throws Exception {
        Map<Settings, Object> before = handler.snapshot();
        Files.writeString(file(), "count: 8\nmode: fast\ndatabase: next\nnames: [Carol]\n");
        ConfigReloadResult result = handler.reloadValidated();
        assertTrue(result.applied());
        assertTrue(result.errors().isEmpty());
        assertEquals(Set.of("database"), result.restartRequired());
        assertEquals(8, Settings.COUNT.getInt());
        assertEquals("fast", Settings.MODE.getString());
        assertEquals("live", Settings.DATABASE.getString());
        assertEquals("next", handler.yaml().getString("database"));
        assertEquals(List.of("Carol"), Settings.NAMES.getList());
        assertEquals(3, before.get(Settings.COUNT));
        assertEquals(List.of("Alice"), before.get(Settings.NAMES));
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        new ReloadableConfigHandler<>(plugin(), "settings.yml", Settings.class);
        assertEquals("next", Settings.DATABASE.getString());
    }

    @Test
    void malformedYamlIsRejectedWithoutRewritingTheFile() throws Exception {
        String malformed = "count: [unterminated\n";
        Map<Settings, Object> before = handler.snapshot();
        Files.writeString(file(), malformed);
        ConfigReloadResult result = handler.reloadValidated();
        assertFalse(result.applied());
        assertTrue(result.errors().containsKey("<file>"));
        assertEquals(before, handler.snapshot());
        assertEquals(malformed, Files.readString(file()));
    }

    @Test
    void missingKeysUseDefaultsWithoutWritingThemToDisk() throws Exception {
        handler.set(Settings.COUNT, 9);
        Files.writeString(file(), "mode: fast\n");
        assertTrue(handler.reloadValidated().applied());
        assertEquals(3, Settings.COUNT.getInt());
        assertEquals(List.of("Alice"), Settings.NAMES.getList());
        assertEquals("mode: fast\n", Files.readString(file()));
    }

    @Test
    void legacyReloadKeepsDefaultFallbackAndHonorsRestartMetadata() throws Exception {
        handler.set(Settings.COUNT, 8);
        Files.writeString(file(), "count: 99\nmode: fast\ndatabase: next\nnames: [Carol]\n");
        assertFalse(handler.reload());
        assertEquals(3, Settings.COUNT.getInt());
        assertEquals("fast", Settings.MODE.getString());
        assertEquals("live", Settings.DATABASE.getString());
    }

    @Test
    void programmaticValidationFailureDoesNotMutateYamlOrNodes() {
        assertThrows(IllegalArgumentException.class, () -> handler.set(Settings.COUNT, 99));
        assertEquals(3, Settings.COUNT.getInt());
        assertEquals(3, handler.yaml().getInt("count"));
        assertThrows(IllegalArgumentException.class, () -> handler.set(Settings.NAMES, List.of(123)));
        assertEquals(List.of("Alice"), Settings.NAMES.getList());
        assertEquals(List.of("Alice"), handler.yaml().getStringList("names"));
    }

    private Path file() { return directory.resolve("settings.yml"); }

    private Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (p, m, args) -> switch (m.getName()) {
                    case "getDataFolder" -> directory.toFile();
                    case "getLogger" -> Logger.getLogger("ConfigHandlerTest");
                    default -> null;
                });
    }

    private static <T> void reset(ReloadableConfigNode<T> node) {
        node.setValue(node.getDefaultValue());
    }

    private enum Settings implements ReloadableConfiguration {
        COUNT(ReloadableConfigNode.of("count", Integer.class, 3)
                .validateWith(ConfigValidators.range(1, 10), "must be between 1 and 10")),
        MODE(ReloadableConfigNode.of("mode", String.class, "safe")
                .validateWith(ConfigValidators.oneOf(List.of("safe", "fast")), "must be safe or fast")),
        DATABASE(ReloadableConfigNode.of("database", String.class, "live").restartRequired()),
        NAMES(ReloadableListNode.ofList("names", String.class, List.of("Alice"))
                .validateWith(names -> !names.isEmpty(), "must contain a name"));

        private final ReloadableConfigNode<?> node;
        Settings(ReloadableConfigNode<?> node) { this.node = node; }
        @Override public ReloadableConfigNode<?> node() { return node; }
    }
}
