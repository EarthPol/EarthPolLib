package com.earthpol.earthpollib.database;

import com.earthpol.earthpollib.database.migration.SchemaMigrator;
import com.earthpol.earthpollib.logging.EnhancedLogger;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
public final class DatabaseService {

    private DatabaseManager databaseManager = null;
    private volatile boolean migrated = false;
    private final boolean disablePluginOnFailure;
    private final AtomicBoolean migrationScheduledOrRunning = new AtomicBoolean(false);

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
        this.serviceName = (serviceName == null) ? plugin.getName() + "-Database" : serviceName;
        this.disablePluginOnFailure = disablePluginOnFailure;
        this.schemaFilesLocation = Objects.requireNonNull(schemaFilesLocation, "schemaFilesLocation");
    }

    public void start() {
        try {
            getDB().start();
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
        if (migrated) {
            logWarn(serviceName + ": migrateAsync() was called, but schema migrations were already completed.");
            return;
        }
        if (!migrationScheduledOrRunning.compareAndSet(false, true)) {
            logWarn(serviceName + ": migrateAsync() was called, but a migration is already scheduled or running.");
            return;
        }

        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    int applied = SchemaMigrator.migrate(
                            getDB(),
                            plugin,
                            List.of(schemaFilesLocation),
                            logger
                    );
                    logInfo("Schema migration complete. Applied " + applied + " migration(s).");
                    migrated = true;
                } catch (Exception ex) {
                    logSevere("Schema migration FAILED", ex);
                    if (disablePluginOnFailure) {
                        logInfo("Shutting down plugin.");
                        plugin.getServer().getPluginManager().disablePlugin(plugin);
                    }
                } finally {
                    migrationScheduledOrRunning.set(false);
                }
            });
        } catch (RuntimeException ex) {
            migrationScheduledOrRunning.set(false);
            logSevere(serviceName + ": failed to schedule migrateAsync()", ex);
            throw ex;
        }
    }

    public DatabaseManager getDB() {
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

    private void logWarn(String message) {
        if (logger != null) {
            logger.warn(message);
        } else {
            plugin.getLogger().warning(message);
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
