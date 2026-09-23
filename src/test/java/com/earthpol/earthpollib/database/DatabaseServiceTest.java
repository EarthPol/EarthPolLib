package com.earthpol.earthpollib.database;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void migrationFailureIsSharedWithWaitingQueriesAndCanBeRetried() {
        FakePluginEnvironment environment = new FakePluginEnvironment(false);
        DatabaseService service = service(environment);
        var first = service.migrateAsyncResult().toCompletableFuture();
        var second = service.migrateAsyncResult().toCompletableFuture();
        var query = service.queryAsync(connection -> fail("Query must wait for migration")).toCompletableFuture();
        assertFalse(query.isDone());
        assertEquals(1, environment.runNowCalls);
        environment.scheduledConsumers.getFirst().accept(null);
        assertThrows(CompletionException.class, first::join);
        assertThrows(CompletionException.class, second::join);
        assertThrows(CompletionException.class, query::join);
        assertFalse(service.isReady());
        service.migrateAsyncResult();
        assertEquals(2, environment.runNowCalls);
        service.close();
    }

    @Test
    void closingPendingInitializationCompletesWaitersAndPreventsStartup() {
        FakePluginEnvironment environment = new FakePluginEnvironment(false);
        DatabaseService service = service(environment);
        var ready = service.initializeAsync().toCompletableFuture();
        var query = service.queryAsync(connection -> fail("Closed query ran")).toCompletableFuture();
        assertFalse(ready.isDone());
        service.close();
        assertThrows(CompletionException.class, ready::join);
        assertThrows(CompletionException.class, query::join);
        environment.scheduledConsumers.getFirst().accept(null);
        assertFalse(service.isReady());
        assertThrows(IllegalStateException.class, service::initializeAsync);
    }

    @Test
    void readinessOnlyCompletesAfterMigrationsAndQueriesRunInTheirOwnAsyncTask() {
        FakePluginEnvironment environment = new FakePluginEnvironment(false);
        DatabaseService service = service(environment);
        List<String> statements = new ArrayList<>();
        DataSource source = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (p, m, args) -> m.getName().equals("getConnection") ? jdbcConnection(statements) : defaultValue(m.getReturnType()));
        service.getDB().customize(config -> {
            config.setDataSource(source);
            config.setMinimumIdle(0);
            config.setMaximumPoolSize(1);
        });
        try (service) {
            var ready = service.initializeAsync().toCompletableFuture();
            var query = service.queryAsync(connection -> {
                assertTrue(statements.stream().anyMatch(sql -> sql.contains("INSERT INTO test_people")));
                return 42;
            }).toCompletableFuture();
            assertFalse(ready.isDone());
            assertEquals(1, environment.runNowCalls);
            environment.scheduledConsumers.getFirst().accept(null);
            assertEquals(2, ready.join());
            assertTrue(service.isReady());
            assertFalse(query.isDone());
            assertEquals(2, environment.runNowCalls);
            environment.scheduledConsumers.getLast().accept(null);
            assertEquals(42, query.join());
            assertEquals(2, service.initializeAsync().toCompletableFuture().join());
            assertEquals(2, environment.runNowCalls);
            var transaction = service.transactionAsync(connection -> {
                assertFalse(connection.getAutoCommit());
                return "committed";
            }).toCompletableFuture();
            environment.scheduledConsumers.getLast().accept(null);
            assertEquals("committed", transaction.join());
        }
    }

    private static DatabaseService service(FakePluginEnvironment environment) {
        DatabaseManager database = new DatabaseManager("earthpol", "secret", "test", "127.0.0.1", "3306", environment.plugin);
        return new DatabaseService(database, null, false, "db/migration/test");
    }

    private static Connection jdbcConnection(List<String> statements) {
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, args) -> switch (m.getName()) {
                    case "isValid" -> !closed.get();
                    case "isClosed" -> closed.get();
                    case "close", "abort" -> { closed.set(true); yield null; }
                    case "getAutoCommit" -> autoCommit.get();
                    case "setAutoCommit" -> { autoCommit.set((Boolean) args[0]); yield null; }
                    case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                    case "createStatement" -> jdbcStatement(null, statements);
                    case "prepareStatement" -> jdbcStatement((String) args[0], statements);
                    default -> defaultValue(m.getReturnType());
                });
    }

    private static Object jdbcStatement(String preparedSql, List<String> statements) {
        Class<?> type = preparedSql == null ? Statement.class : PreparedStatement.class;
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            if (m.getName().startsWith("execute")) {
                String sql = preparedSql == null ? (String) args[0] : preparedSql;
                statements.add(sql);
                if (m.getName().equals("executeQuery")) {
                    AtomicBoolean row = new AtomicBoolean(sql.contains("GET_LOCK"));
                    return Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                            (r, method, values) -> switch (method.getName()) {
                                case "next" -> row.getAndSet(false);
                                case "getInt" -> 1;
                                default -> defaultValue(method.getReturnType());
                            });
                }
            }
            return defaultValue(m.getReturnType());
        });
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
        private final ScheduledTask nativeTask = (ScheduledTask) Proxy.newProxyInstance(ScheduledTask.class.getClassLoader(),
                new Class<?>[]{ScheduledTask.class}, (p, m, a) -> defaultValue(m.getReturnType()));

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
                            yield nativeTask;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
            );
            GlobalRegionScheduler globalScheduler = (GlobalRegionScheduler) Proxy.newProxyInstance(
                    GlobalRegionScheduler.class.getClassLoader(), new Class<?>[]{GlobalRegionScheduler.class}, (p, m, args) -> {
                        if (m.getName().equals("run")) {
                            @SuppressWarnings("unchecked") Consumer<ScheduledTask> action = (Consumer<ScheduledTask>) args[1];
                            action.accept(nativeTask);
                            return nativeTask;
                        }
                        return defaultValue(m.getReturnType());
                    });
            this.server = (Server) Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{Server.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAsyncScheduler" -> asyncScheduler;
                        case "getGlobalRegionScheduler" -> globalScheduler;
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
