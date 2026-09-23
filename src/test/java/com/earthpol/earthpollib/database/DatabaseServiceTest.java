package com.earthpol.earthpollib.database;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseServiceTest {

    @Test
    void migrateAsyncDoesNotScheduleTwiceWhilePending() {
        FakePluginEnvironment environment = new FakePluginEnvironment(false);
        DatabaseManager databaseManager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                environment.plugin
        );
        DatabaseService service = new DatabaseService(databaseManager, null, false, "db/migration/test");

        service.migrateAsync();
        service.migrateAsync();

        assertEquals(1, environment.runNowCalls);
    }

    @Test
    void migrateAsyncDisablesPluginWhenMigrationFailsAndConfigured() {
        FakePluginEnvironment environment = new FakePluginEnvironment(true);
        DatabaseManager databaseManager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                environment.plugin
        );
        DatabaseService service = new DatabaseService(databaseManager, null, true, "db/migration/test");

        service.migrateAsync();

        assertEquals(1, environment.disablePluginCalls);
    }

    private static final class FakePluginEnvironment {
        private final Logger logger = Logger.getLogger("EarthPolLibTest-DatabaseService");
        private final List<Consumer<Object>> scheduledConsumers = new ArrayList<>();
        private final PluginManager pluginManager;
        private final AsyncScheduler asyncScheduler;
        private final Server server;
        private final Plugin plugin;
        private final boolean runImmediately;
        private int runNowCalls = 0;
        private int disablePluginCalls = 0;

        private FakePluginEnvironment(boolean runImmediately) {
            this.runImmediately = runImmediately;
            this.pluginManager = (PluginManager) Proxy.newProxyInstance(
                    PluginManager.class.getClassLoader(),
                    new Class<?>[]{PluginManager.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "disablePlugin" -> {
                            disablePluginCalls++;
                            yield null;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
            );
            this.asyncScheduler = (AsyncScheduler) Proxy.newProxyInstance(
                    AsyncScheduler.class.getClassLoader(),
                    new Class<?>[]{AsyncScheduler.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "runNow" -> {
                            runNowCalls++;
                            @SuppressWarnings("unchecked")
                            Consumer<Object> consumer = (Consumer<Object>) args[1];
                            scheduledConsumers.add(consumer);
                            if (this.runImmediately) {
                                consumer.accept(null);
                            }
                            yield null;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
            );
            this.server = (Server) Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{Server.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAsyncScheduler" -> asyncScheduler;
                        case "getPluginManager" -> pluginManager;
                        case "getLogger" -> logger;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
            );
            this.plugin = (Plugin) Proxy.newProxyInstance(
                    Plugin.class.getClassLoader(),
                    new Class<?>[]{Plugin.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getLogger" -> logger;
                        case "getName" -> "DatabaseServicePlugin";
                        case "getServer" -> server;
                        case "getDataFolder" -> Path.of("target", "test-data", "database-service").toFile();
                        case "isEnabled" -> true;
                        case "toString" -> "Plugin[DatabaseServicePlugin]";
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
            );
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
        if (type == File.class) return new File("target/test-data/database-service");
        return null;
    }
}
