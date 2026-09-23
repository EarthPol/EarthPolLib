package com.earthpol.earthpollib.database;

import com.earthpol.earthpollib.logging.EnhancedLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mariadb.jdbc.MariaDbDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

// TODO: Add Performance presets that tune the HikariConfig based on a given scenario
// They could be made into an enum and passed in via a setter.

/**
 * <code>MariaDB</code> Database connection management object with pre-set defaults.
 * The performance defaults assume local database usage for a single plugin. Adjust using the
 * customize() method if you need to tune performance differently.
 *
 * <li>Uses <code>utf8mb4</code> encoding for the connection. Create your tables accordingly. </li>
 */
@SuppressWarnings("unused")
public final class DatabaseManager implements AutoCloseable {

    private static final String SESSION_VARIABLES = "character_set_client=utf8mb4,character_set_results=utf8mb4";

    private final String username;
    private final String password;
    private final String databaseName;
    private final String ip;
    private final String port;
    private final String poolName;
    private final Plugin owningPlugin;

    private final ReentrantLock lifecycle = new ReentrantLock();
    private final LinkedHashMap<String, String> connectionParameters = defaultConnectionParameters();
    private volatile HikariConfig hikariConfig = null;
    private volatile HikariDataSource dataSource = null;

    private volatile EnhancedLogger logger;

    public DatabaseManager(String username,
                           String password,
                           String databaseName,
                           String ip,
                           String port,
                           Plugin owningPlugin) {

        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
        this.ip = Objects.requireNonNull(ip, "ip");
        this.port = port;
        this.owningPlugin = Objects.requireNonNull(owningPlugin, "owningPlugin");
        this.poolName = owningPlugin.getName() + "_pool";
    }

    @Contract(pure = true)
    private static @NotNull LinkedHashMap<String, String> defaultConnectionParameters() {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("tcpKeepAlive", "true");
        parameters.put("sessionVariables", SESSION_VARIABLES);
        return parameters;
    }

    @Contract(pure = true)
    private static @NotNull String buildJdbcUrl(String host, String port, String db, Map<String, String> parameters) {
        StringBuilder url = new StringBuilder("jdbc:mariadb://")
                .append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(db);

        if (!parameters.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (!first) {
                    url.append("&");
                }
                url.append(entry.getKey());
                if (!entry.getValue().isEmpty()) {
                    url.append("=").append(entry.getValue());
                }
                first = false;
            }
        }

        return url.toString();
    }

    @Contract(pure = true)
    private @NotNull String buildJdbcUrl() {
        return buildJdbcUrl(ip, port, databaseName, connectionParameters);
    }

    public @NotNull String getJdbcUrl() {
        lifecycle.lock();
        try {
            return buildJdbcUrl();
        } finally {
            lifecycle.unlock();
        }
    }

    public @NotNull Map<String, String> getConnectionParameters() {
        lifecycle.lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(connectionParameters));
        } finally {
            lifecycle.unlock();
        }
    }

    public void setConnectionParameter(String key, String value) {
        String parameterKey = requireConnectionParameterKey(key);
        String parameterValue = Objects.requireNonNull(value, "value");
        customizeConnectionParameters(parameters -> parameters.put(parameterKey, parameterValue));
    }

    public void removeConnectionParameter(String key) {
        String parameterKey = requireConnectionParameterKey(key);
        customizeConnectionParameters(parameters -> parameters.remove(parameterKey));
    }

    public void resetConnectionParameters() {
        customizeConnectionParameters(parameters -> {
            parameters.clear();
            parameters.putAll(defaultConnectionParameters());
        });
    }

    public void customizeConnectionParameters(Consumer<Map<String, String>> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        lifecycle.lock();
        try {
            ensureNotStarted("customizeConnectionParameters()");

            LinkedHashMap<String, String> updatedParameters = new LinkedHashMap<>(connectionParameters);
            mutator.accept(updatedParameters);
            validateConnectionParameters(updatedParameters);

            connectionParameters.clear();
            connectionParameters.putAll(updatedParameters);
            applyJdbcUrlToConfiguredDataSource();
            logInfo("JDBC connection parameters customized for " + poolName);
        } catch (RuntimeException ex) {
            logSevere("customizeConnectionParameters() failed for " + poolName, ex);
            throw ex;
        } finally {
            lifecycle.unlock();
        }
    }

    public void configure() {
        lifecycle.lock();
        try {
            if (dataSource != null || hikariConfig != null) return;

            MariaDbDataSource mariads = new MariaDbDataSource();
            mariads.setUrl(buildJdbcUrl());
            mariads.setUser(username);
            mariads.setPassword(password);

            HikariConfig cfg = new HikariConfig();
            cfg.setPoolName(poolName);
            cfg.setDataSource(mariads);

            cfg.setMinimumIdle(4);
            cfg.setMaximumPoolSize(6);
            cfg.setConnectionTimeout(3_000);
            cfg.setIdleTimeout(300_000);
            cfg.setMaxLifetime(1_800_000);

            this.hikariConfig = cfg;
            logInfo("HikariConfig initialized.");
        } catch (SQLException e) {
            logSevere(e.getMessage(),e);
            throw new RuntimeException(e);
        } finally {
            lifecycle.unlock();
        }
    }

    public void customize(Consumer<HikariConfig> mutator) {
        lifecycle.lock();
        try {
            if (dataSource != null) {
                var msg = "customize() called after start; pool already initialized for " + poolName;
                logSevere(msg, null);
                throw new IllegalStateException(msg);
            }
            if (hikariConfig == null) configure();
            mutator.accept(hikariConfig);
            logInfo("HikariConfig customized for " + poolName);
        } catch (RuntimeException ex) {
            logSevere("customize() failed for " + poolName, ex);
            throw ex;
        } finally {
            lifecycle.unlock();
        }
    }

    public void start() {
        logInfo("Attempting connection to JDBC URL: " + buildJdbcUrl());
        lifecycle.lock();
        try {
            if (dataSource != null) return;
            if (hikariConfig == null) configure();
            this.dataSource = new HikariDataSource(this.hikariConfig);
            logInfo("HikariDataSource started for " + poolName + " (" + ip + ":" + port + "/" + databaseName + ")");
        } catch (RuntimeException ex) {
            logSevere("start() failed for " + poolName, ex);
            throw ex;
        } finally {
            lifecycle.unlock();
        }
    }

    public void shutdown() {
        lifecycle.lock();
        try {
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
                logInfo("HikariDataSource closed for " + poolName);
            }
            hikariConfig = null;
        } catch (RuntimeException ex) {
            logSevere("shutdown() failed for " + poolName, ex);
            throw ex;
        } finally {
            lifecycle.unlock();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public boolean ping() {
        try (Connection c = getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            boolean pingResult =  rs.next();
            logInfo("Ping result for " + poolName + ": " + pingResult);
            return pingResult;
        } catch (SQLException e) {
            logSevere("ping() failed: ", e);
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public <T> T withConnection(SqlWork<T> work) throws SQLException {
        Objects.requireNonNull(work, "work");
        try (Connection connection = getConnection()) {
            return work.execute(connection);
        }
    }

    public <T> T transaction(SqlWork<T> work) throws SQLException {
        return SqlTransactions.execute(getDataSource(), work);
    }

    public DataSource getDataSource() {
        HikariDataSource ds = this.dataSource;
        if (ds == null) throw new IllegalStateException("DatabaseManager not started. Call start() first.");
        return ds;
    }

    public HikariConfig getHikariConfig() {
        lifecycle.lock();
        try {
            if (hikariConfig == null) configure();
            return hikariConfig;
        } finally {
            lifecycle.unlock();
        }
    }

    private static void validateConnectionParameters(Map<String, String> parameters) {
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            requireConnectionParameterKey(entry.getKey());
            Objects.requireNonNull(entry.getValue(), "value");
        }
    }

    private static String requireConnectionParameterKey(String key) {
        String parameterKey = Objects.requireNonNull(key, "key");
        if (parameterKey.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        return parameterKey;
    }

    private void ensureNotStarted(String operation) {
        if (dataSource != null) {
            String message = operation + " called after start; pool already initialized for " + poolName;
            logSevere(message, null);
            throw new IllegalStateException(message);
        }
    }

    private void applyJdbcUrlToConfiguredDataSource() {
        if (hikariConfig == null) {
            return;
        }

        DataSource configuredDataSource = hikariConfig.getDataSource();
        if (configuredDataSource instanceof MariaDbDataSource mariaDbDataSource) {
            try {
                mariaDbDataSource.setUrl(buildJdbcUrl());
            } catch (SQLException ex) {
                logSevere("Failed to apply JDBC URL customization for " + poolName, ex);
                throw new RuntimeException(ex);
            }
        }
    }

    private void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        } else {
            owningPlugin.getLogger().info(message);
        }
    }
    private void logSevere(String message, Throwable t) {
        if (logger != null) {
            if (t != null) logger.severe(message, t); else logger.severe(message);
        } else {
            owningPlugin.getLogger().severe(message);
        }
    }

    String getUsername() { return username; }
    String getPassword() { return password; }
    String getDatabaseName() { return databaseName; }
    String getIp() { return ip; }
    String getPort() { return port; }
    public Plugin getOwningPlugin() { return owningPlugin; }
    public EnhancedLogger getLogger() { return logger; }
    public static String getDataSourceClass(){ return "org.mariadb.jdbc.MariaDbDataSource";}

    public void setLogger(EnhancedLogger logger) { this.logger = logger; }
}
