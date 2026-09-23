package com.earthpol.earthpollib.database;

import com.earthpol.earthpollib.database.migration.SchemaMigrator;
import com.earthpol.earthpollib.logging.EnhancedLogger;
import com.earthpol.earthpollib.scheduling.PluginScheduler;
import com.earthpol.earthpollib.scheduling.TaskHandle;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/**
 * DatabaseService
 * <p>
 * A small façade that:
 * <ol>
 *   <li>Creates and starts a {@link DatabaseManager} (connection pool, ping, etc.).</li>
 *   <li>Runs bundled SQL schema migrations (via {@link SchemaMigrator})
 *       from one or more classpath locations.</li>
 *   <li>Optionally disables the owning Bukkit {@link org.bukkit.plugin.Plugin} on failure.</li>
 * </ol>
 */
@SuppressWarnings("unused")
public final class DatabaseService implements AutoCloseable {

    private volatile DatabaseManager databaseManager = null;
    private volatile boolean migrated = false;
    private final boolean disablePluginOnFailure;
    private final Object lifecycle = new Object();
    private final PluginScheduler scheduler;
    private CompletableFuture<Integer> migrationResult;
    private volatile boolean closed;

    private final @Nullable EnhancedLogger logger;
    private final Plugin plugin;
    private final String serviceName;
    private final String schemaFilesLocation;

    public DatabaseService(
            EnhancedLogger logger,
            Plugin plugin,
            String username,
            String password,
            String databaseName,
            String ip,
            String port,
            boolean disablePluginOnFailure,
            @Nullable String serviceName,
            String schemaFilesLocation) {
        this.logger = logger;
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = new PluginScheduler(this.plugin);
        this.disablePluginOnFailure = disablePluginOnFailure;
        this.schemaFilesLocation = Objects.requireNonNull(schemaFilesLocation, "schemaFilesLocation");
        this.serviceName = (serviceName == null) ? plugin.getName() + "-Database" : serviceName;

        databaseManager = new DatabaseManager(
                username,
                password,
                databaseName,
                ip,
                port,
                this.plugin
        );
        databaseManager.setLogger(logger);
    }

    public DatabaseService(DatabaseManager databaseManager,
                           @Nullable String serviceName,
                           boolean disablePluginOnFailure,
                           String schemaFilesLocation
    ) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.logger = databaseManager.getLogger();
        this.plugin = databaseManager.getOwningPlugin();
        this.scheduler = new PluginScheduler(this.plugin);
        this.serviceName = (serviceName == null) ? plugin.getName() + "-Database" : serviceName;
        this.disablePluginOnFailure = disablePluginOnFailure;
        this.schemaFilesLocation = Objects.requireNonNull(schemaFilesLocation, "schemaFilesLocation");
    }

    public void start() {
        ensureOpen();
        try {
            synchronized (lifecycle) {
                getDB().start();
            }
        }

        catch (Exception e) {
            logSevere(serviceName + ": databaseManager failed to start. " + e.getMessage(), e);
            logInfo("disablePluginOnFailure: " + disablePluginOnFailure);
            if (disablePluginOnFailure) {
                logInfo("Shutting down plugin.");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            } else {
                logInfo("databaseManager was set to null.");
                databaseManager = null;
            }
        }
    }

    public void migrateAsync() {
        migrateAsyncResult();
    }

    /** Starts the pool and migrates on an async task. Concurrent calls share the same attempt. */
    public CompletionStage<Integer> initializeAsync() {
        return scheduleMigration(true);
    }

    /** Migrates an already started pool; the result is the number of applied migrations. */
    public CompletionStage<Integer> migrateAsyncResult() {
        return scheduleMigration(false);
    }

    private CompletionStage<Integer> scheduleMigration(boolean startDatabase) {
        CompletableFuture<Integer> result;
        synchronized (lifecycle) {
            ensureOpen();
            if (migrationResult != null && (!migrationResult.isDone() || migrated)) {
                return migrationResult.minimalCompletionStage();
            }
            result = new CompletableFuture<>();
            migrationResult = result;
        }
        try {
            TaskHandle task = scheduler.runAsync(() -> {
                try {
                    if (startDatabase) {
                        synchronized (lifecycle) {
                            getDB().start();
                        }
                    }
                    int applied = SchemaMigrator.migrate(getDB(), plugin, List.of(schemaFilesLocation), logger);
                    synchronized (lifecycle) {
                        ensureOpen();
                        migrated = true;
                    }
                    logInfo("Schema migration complete. Applied " + applied + " migration(s).");
                    result.complete(applied);
                } catch (Exception ex) {
                    result.completeExceptionally(ex);
                    if (!closed) {
                        logSevere("Schema migration FAILED", ex);
                        if (disablePluginOnFailure) disableAfterFailure();
                    }
                }
            });
            task.completion().whenComplete((unused, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
            });
        } catch (RuntimeException | Error ex) {
            result.completeExceptionally(ex);
            logSevere(serviceName + ": failed to schedule migrateAsync()", ex);
            throw ex;
        }
        return result.minimalCompletionStage();
    }

    /** Waits asynchronously for the requested initialization/migration, then runs SQL off tick threads. */
    public <T> CompletionStage<T> queryAsync(SqlWork<T> work) {
        return runWhenReady(work, false);
    }

    public <T> CompletionStage<T> transactionAsync(SqlWork<T> work) {
        return runWhenReady(work, true);
    }

    private <T> CompletionStage<T> runWhenReady(SqlWork<T> work, boolean transaction) {
        Objects.requireNonNull(work, "work");
        CompletionStage<Integer> ready;
        synchronized (lifecycle) {
            ensureOpen();
            if (migrationResult == null) throw new IllegalStateException("Call initializeAsync() or migrateAsyncResult() first");
            ready = migrationResult.minimalCompletionStage();
        }
        return ready.thenCompose(applied -> {
            CompletableFuture<T> result = new CompletableFuture<>();
            try {
                TaskHandle task = scheduler.runAsync(() -> {
                    try {
                        result.complete(transaction ? getDB().transaction(work) : getDB().withConnection(work));
                    } catch (Exception ex) {
                        result.completeExceptionally(ex);
                    }
                });
                task.completion().whenComplete((unused, failure) -> {
                    if (failure != null) result.completeExceptionally(failure);
                });
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
            return result.minimalCompletionStage();
        });
    }

    /** Cancels queued work and closes the pool. Running SQL may already have taken effect. */
    @Override
    public void close() {
        CompletableFuture<Integer> pending;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            migrated = false;
            pending = migrationResult;
        }
        if (pending != null) pending.completeExceptionally(new CancellationException("Database service closed"));
        scheduler.close();
        DatabaseManager manager = databaseManager;
        if (manager != null) manager.close();
    }

    /** A non-blocking readiness check. */
    public boolean isReady() {
        return migrated && !closed;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Database service is closed");
    }

    private void disableAfterFailure() {
        try {
            scheduler.runGlobal(() -> plugin.getServer().getPluginManager().disablePlugin(plugin));
        } catch (RuntimeException ex) {
            logSevere("Could not schedule plugin shutdown after database failure", ex);
        }
    }

    public DatabaseManager getDB() {
        ensureOpen();
        if (databaseManager == null)
            throw new IllegalStateException("getDB() failed. databaseManager isn't initialized.");
        return databaseManager;
    }

    public java.sql.Connection getConnection() {
        try {
            return getDB().getConnection();
        } catch (SQLException e) {
            logSevere("getConnection() failed.", e);
            throw new RuntimeException(e);
        }
    }

    public boolean isRunning() {return databaseManager != null && databaseManager.ping();}

    public boolean isMigrated() {return migrated;}

    private void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        } else {
            plugin.getLogger().info(message);
        }
    }

    private void logSevere(String message, Throwable throwable) {
        if (logger != null) {
            if (throwable != null) {
                logger.severe(message, throwable);
            } else {
                logger.severe(message);
            }
        } else if (throwable != null) {
            plugin.getLogger().log(Level.SEVERE, message, throwable);
        } else {
            plugin.getLogger().severe(message);
        }
    }
}
