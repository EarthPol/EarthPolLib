package com.earthpol.earthpollib.database.migration;

import com.earthpol.earthpollib.database.DatabaseManager;
import com.earthpol.earthpollib.logging.EnhancedLogger;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.earthpol.earthpollib.string.StringUtil.sanitize;

/**
 * Lightweight, plugin-friendly SQL migrator that discovers bundled SQL files
 * under one or more classpath locations and records applied checksums per plugin.
 */
@SuppressWarnings("unused")
public final class SchemaMigrator {

    private static final int LOCK_TIMEOUT_SECONDS = 30;

    public record Migration(String id, String resourcePath, String checksumSha256) {}

    private record AppliedMigration(String checksumSha256, boolean success) {}

    public static int migrate(DatabaseManager db,
                              Plugin plugin,
                              List<String> locations,
                              @Nullable EnhancedLogger logger) {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(locations, "locations");

        List<String> normalizedLocations = normalizeLocations(locations);
        if (normalizedLocations.isEmpty()) {
            throw new IllegalArgumentException("No valid schema migration locations provided.");
        }

        List<Migration> migrations = discoverMigrations(plugin, normalizedLocations);
        String historyTable = "schema_" + sanitize(plugin.getName()) + "_migrations";
        String legacyFlywayHistoryTable = "flyway_" + sanitize(plugin.getName()) + "_history";
        String advisoryLockName = "schema_migrator_" + sanitize(plugin.getName());

        logInfo(plugin, logger, "Running schema migrations for " + plugin.getName() + "...");

        try (Connection connection = db.getConnection()) {
            ensureHistoryTable(connection, historyTable);
            acquireLock(connection, advisoryLockName);

            try {
                importFlywayHistoryIfNeeded(connection, historyTable, legacyFlywayHistoryTable, migrations, plugin, logger);
                Map<String, AppliedMigration> applied = loadAppliedMigrations(connection, historyTable);
                ensureNoFailedMigrations(applied, historyTable);

                int appliedCount = 0;
                for (Migration migration : migrations) {
                    AppliedMigration existing = applied.get(migration.id());
                    if (existing != null) {
                        if (!existing.checksumSha256().equals(migration.checksumSha256())) {
                            throw new IllegalStateException(
                                    "Checksum mismatch for applied migration " + migration.resourcePath()
                                            + ". Existing checksum=" + existing.checksumSha256()
                                            + ", bundled checksum=" + migration.checksumSha256()
                            );
                        }
                        continue;
                    }

                    logInfo(plugin, logger, "Applying schema migration " + migration.resourcePath());
                    insertPendingMigration(connection, historyTable, migration);
                    applyStatements(connection, migration, plugin, logger);
                    markMigrationSuccessful(connection, historyTable, migration.id());
                    applied.put(migration.id(), new AppliedMigration(migration.checksumSha256(), true));
                    appliedCount++;
                }

                logInfo(plugin, logger, "Schema migrator ran " + appliedCount + " migration(s). locations="
                        + String.join(", ", normalizedLocations) + " historyTable=" + historyTable);
                return appliedCount;
            } finally {
                releaseLock(connection, advisoryLockName, plugin, logger);
            }
        } catch (SQLException ex) {
            logSevere(plugin, logger, "Schema migration failed", ex);
            throw new IllegalStateException("Schema migration failed", ex);
        }
    }

    public static int migrateWithDefaultLocation(DatabaseManager db,
                                                 @NotNull Plugin plugin,
                                                 @Nullable EnhancedLogger logger) {
        String defaultLocation = "db/migration/"
                + plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return migrate(db, plugin, List.of(defaultLocation), logger);
    }

    static List<Migration> discoverMigrations(Plugin plugin, List<String> locations) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(locations, "locations");

        List<String> normalizedLocations = normalizeLocations(locations);
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        LinkedHashMap<String, Migration> migrations = new LinkedHashMap<>();

        for (String location : normalizedLocations) {
            List<String> resourcePaths = new ArrayList<>(discoverResourcePaths(plugin, classLoader, location));
            if (resourcePaths.isEmpty()) {
                throw new IllegalStateException("No SQL migration files found in classpath location: " + location);
            }

            Collections.sort(resourcePaths);
            for (String resourcePath : resourcePaths) {
                String sql = readResource(classLoader, resourcePath);
                Migration migration = new Migration(resourcePath, resourcePath, sha256Hex(sql));
                if (migrations.putIfAbsent(migration.id(), migration) != null) {
                    throw new IllegalStateException("Duplicate schema migration discovered: " + migration.id());
                }
            }
        }

        return List.copyOf(migrations.values());
    }

    static List<String> splitStatements(String sql) {
        Objects.requireNonNull(sql, "sql");

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBackticks = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                    current.append(ch);
                }
                continue;
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    current.append(' ');
                    i++;
                }
                continue;
            }

            if (inSingleQuote) {
                current.append(ch);
                if (ch == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    if (next == '\'') {
                        current.append(next);
                        i++;
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }

            if (inDoubleQuote) {
                current.append(ch);
                if (ch == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (ch == '"') {
                    if (next == '"') {
                        current.append(next);
                        i++;
                    } else {
                        inDoubleQuote = false;
                    }
                }
                continue;
            }

            if (inBackticks) {
                current.append(ch);
                if (ch == '`') {
                    if (next == '`') {
                        current.append(next);
                        i++;
                    } else {
                        inBackticks = false;
                    }
                }
                continue;
            }

            if (ch == '-' && next == '-' && (i + 2 >= sql.length() || Character.isWhitespace(sql.charAt(i + 2)))) {
                inLineComment = true;
                i++;
                continue;
            }

            if (ch == '#') {
                inLineComment = true;
                continue;
            }

            if (ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }

            if (ch == '\'') {
                inSingleQuote = true;
                current.append(ch);
                continue;
            }

            if (ch == '"') {
                inDoubleQuote = true;
                current.append(ch);
                continue;
            }

            if (ch == '`') {
                inBackticks = true;
                current.append(ch);
                continue;
            }

            if (ch == ';') {
                addStatement(statements, current);
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        addStatement(statements, current);
        return List.copyOf(statements);
    }

    private static List<String> normalizeLocations(List<String> locations) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String location : locations) {
            if (location == null) {
                continue;
            }

            String trimmed = location.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("classpath:")) {
                trimmed = trimmed.substring("classpath:".length());
            }

            trimmed = trimmed.replace('\\', '/');
            while (trimmed.startsWith("/")) {
                trimmed = trimmed.substring(1);
            }
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }

            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        return List.copyOf(normalized);
    }

    private static List<String> discoverResourcePaths(Plugin plugin, ClassLoader classLoader, String location) {
        LinkedHashSet<String> resourcePaths = new LinkedHashSet<>();
        resourcePaths.addAll(discoverFromClassLoader(classLoader, location));

        if (resourcePaths.isEmpty()) {
            resourcePaths.addAll(discoverFromCodeSource(plugin, location));
        }

        return List.copyOf(resourcePaths);
    }

    private static List<String> discoverFromClassLoader(ClassLoader classLoader, String location) {
        LinkedHashSet<String> resourcePaths = new LinkedHashSet<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(location);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                resourcePaths.addAll(discoverFromUrl(url, location));
            }

            if (resourcePaths.isEmpty()) {
                Enumeration<URL> directoryResources = classLoader.getResources(location + "/");
                while (directoryResources.hasMoreElements()) {
                    URL url = directoryResources.nextElement();
                    resourcePaths.addAll(discoverFromUrl(url, location));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed discovering schema resources under " + location, ex);
        }

        return List.copyOf(resourcePaths);
    }

    private static List<String> discoverFromCodeSource(Plugin plugin, String location) {
        try {
            URI codeSource = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            Path root = Paths.get(codeSource);
            if (Files.isDirectory(root)) {
                Path locationPath = root.resolve(location);
                if (!Files.exists(locationPath)) {
                    return List.of();
                }

                try (Stream<Path> stream = Files.walk(locationPath)) {
                    return stream
                            .filter(Files::isRegularFile)
                            .map(path -> toResourcePath(locationPath, location, path))
                            .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".sql"))
                            .sorted()
                            .toList();
                }
            }

            try (JarFile jarFile = new JarFile(root.toFile())) {
                return jarFile.stream()
                        .map(JarEntry::getName)
                        .filter(name -> !name.endsWith("/"))
                        .filter(name -> name.startsWith(location + "/"))
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".sql"))
                        .sorted()
                        .toList();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed discovering schema resources from plugin code source for " + location, ex);
        }
    }

    private static List<String> discoverFromUrl(URL url, String location) {
        try {
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                Path locationPath = Paths.get(url.toURI());
                if (Files.isDirectory(locationPath)) {
                    try (Stream<Path> stream = Files.walk(locationPath)) {
                        return stream
                                .filter(Files::isRegularFile)
                                .map(path -> toResourcePath(locationPath, location, path))
                                .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".sql"))
                                .toList();
                    }
                }
                if (Files.isRegularFile(locationPath) && locationPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql")) {
                    return List.of(location);
                }
                return List.of();
            }

            if ("jar".equalsIgnoreCase(url.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                String entryPrefix = connection.getEntryName();
                if (entryPrefix == null || entryPrefix.isBlank()) {
                    entryPrefix = location;
                }
                if (!entryPrefix.endsWith("/")) {
                    entryPrefix = entryPrefix + "/";
                }
                final String finalEntryPrefix = entryPrefix;

                try (JarFile jarFile = connection.getJarFile()) {
                    return jarFile.stream()
                            .map(JarEntry::getName)
                            .filter(name -> !name.endsWith("/"))
                            .filter(name -> name.startsWith(finalEntryPrefix))
                            .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".sql"))
                            .toList();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed scanning schema resources at " + url, ex);
        }

        return List.of();
    }

    private static String toResourcePath(Path root, String location, Path resource) {
        Path relative = root.relativize(resource);
        String suffix = relative.toString().replace('\\', '/');
        return suffix.isEmpty() ? location : location + "/" + suffix;
    }

    private static void ensureHistoryTable(Connection connection, String historyTable) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      migration_id VARCHAR(255) PRIMARY KEY,
                      resource_path VARCHAR(512) NOT NULL,
                      checksum_sha256 CHAR(64) NOT NULL,
                      success TINYINT(1) NOT NULL DEFAULT 0,
                      applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """.formatted(historyTable));
        }
    }

    private static void acquireLock(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new IllegalStateException("Timed out acquiring schema migration lock: " + lockName);
                }
            }
        }
    }

    private static void releaseLock(Connection connection,
                                    String lockName,
                                    Plugin plugin,
                                    @Nullable EnhancedLogger logger) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery();
        } catch (SQLException ex) {
            logSevere(plugin, logger, "Failed releasing schema migration lock " + lockName, ex);
        }
    }

    private static void importFlywayHistoryIfNeeded(Connection connection,
                                                    String historyTable,
                                                    String legacyFlywayHistoryTable,
                                                    List<Migration> migrations,
                                                    Plugin plugin,
                                                    @Nullable EnhancedLogger logger) throws SQLException {
        if (!tableExists(connection, legacyFlywayHistoryTable) || historyTableHasRows(connection, historyTable)) {
            return;
        }

        ensureNoFailedFlywayMigrations(connection, legacyFlywayHistoryTable);

        List<String> legacyScripts = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT script FROM " + legacyFlywayHistoryTable + " WHERE success = TRUE ORDER BY installed_rank")) {
            while (resultSet.next()) {
                legacyScripts.add(resultSet.getString(1));
            }
        }

        if (legacyScripts.isEmpty()) {
            return;
        }

        Map<String, Migration> byResourcePath = migrations.stream()
                .collect(Collectors.toMap(Migration::resourcePath, migration -> migration));
        Map<String, Migration> byFileName = buildMigrationFileNameIndex(migrations);

        int imported = 0;
        for (String script : legacyScripts) {
            Migration migration = byResourcePath.get(script);
            if (migration == null) {
                migration = byFileName.get(script);
            }
            if (migration == null) {
                continue;
            }

            insertImportedMigration(connection, historyTable, migration);
            imported++;
        }

        if (imported > 0) {
            logInfo(plugin, logger,
                    "Imported " + imported + " successful Flyway migration record(s) from " + legacyFlywayHistoryTable
                            + " into " + historyTable + ".");
        }
    }

    private static Map<String, Migration> buildMigrationFileNameIndex(List<Migration> migrations) {
        Map<String, Migration> byFileName = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();

        for (Migration migration : migrations) {
            String fileName = migration.resourcePath().substring(migration.resourcePath().lastIndexOf('/') + 1);
            Migration existing = byFileName.putIfAbsent(fileName, migration);
            if (existing != null) {
                duplicates.add(fileName);
            }
        }

        duplicates.forEach(byFileName::remove);
        return byFileName;
    }

    private static void ensureNoFailedFlywayMigrations(Connection connection, String legacyFlywayHistoryTable) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT script FROM " + legacyFlywayHistoryTable + " WHERE success = FALSE")) {
            List<String> failed = new ArrayList<>();
            while (resultSet.next()) {
                failed.add(resultSet.getString(1));
            }
            if (!failed.isEmpty()) {
                throw new IllegalStateException(
                        "Detected failed Flyway migrations in " + legacyFlywayHistoryTable + ": " + String.join(", ", failed)
                );
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean historyTableHasRows(Connection connection, String historyTable) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1 FROM " + historyTable + " LIMIT 1")) {
            return resultSet.next();
        }
    }

    private static Map<String, AppliedMigration> loadAppliedMigrations(Connection connection, String historyTable) throws SQLException {
        Map<String, AppliedMigration> applied = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT migration_id, checksum_sha256, success FROM " + historyTable + " ORDER BY applied_at, migration_id")) {
            while (resultSet.next()) {
                applied.put(
                        resultSet.getString("migration_id"),
                        new AppliedMigration(resultSet.getString("checksum_sha256"), resultSet.getBoolean("success"))
                );
            }
        }
        return applied;
    }

    private static void ensureNoFailedMigrations(Map<String, AppliedMigration> applied, String historyTable) {
        List<String> failed = applied.entrySet().stream()
                .filter(entry -> !entry.getValue().success())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!failed.isEmpty()) {
            throw new IllegalStateException(
                    "Detected failed schema migrations in " + historyTable + ": " + String.join(", ", failed)
                            + ". Repair the schema and delete the failed migration record(s) before retrying."
            );
        }
    }

    private static void insertPendingMigration(Connection connection, String historyTable, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s (migration_id, resource_path, checksum_sha256, success)
                VALUES (?, ?, ?, 0)
                """.formatted(historyTable))) {
            statement.setString(1, migration.id());
            statement.setString(2, migration.resourcePath());
            statement.setString(3, migration.checksumSha256());
            statement.executeUpdate();
        }
    }

    private static void insertImportedMigration(Connection connection, String historyTable, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s (migration_id, resource_path, checksum_sha256, success)
                VALUES (?, ?, ?, 1)
                """.formatted(historyTable))) {
            statement.setString(1, migration.id());
            statement.setString(2, migration.resourcePath());
            statement.setString(3, migration.checksumSha256());
            statement.executeUpdate();
        }
    }

    private static void markMigrationSuccessful(Connection connection, String historyTable, String migrationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE %s
                SET success = 1, applied_at = CURRENT_TIMESTAMP
                WHERE migration_id = ?
                """.formatted(historyTable))) {
            statement.setString(1, migrationId);
            statement.executeUpdate();
        }
    }

    private static void applyStatements(Connection connection,
                                        Migration migration,
                                        Plugin plugin,
                                        @Nullable EnhancedLogger logger) throws SQLException {
        String sql = readResource(plugin.getClass().getClassLoader(), migration.resourcePath());
        List<String> statements = splitStatements(sql);
        int statementIndex = 0;

        for (String statementText : statements) {
            statementIndex++;
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementText);
            } catch (SQLException ex) {
                logSevere(plugin, logger,
                        "Migration " + migration.resourcePath() + " failed on statement #" + statementIndex
                                + "\n" + statementText,
                        ex);
                throw ex;
            }
        }
    }

    private static String readResource(ClassLoader classLoader, String resourcePath) {
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed reading migration resource " + resourcePath, ex);
        }
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed computing migration checksum", ex);
        }
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }

    private static void logInfo(Plugin plugin, @Nullable EnhancedLogger logger, String message) {
        if (logger != null) {
            logger.info(message);
        } else {
            plugin.getLogger().info(message);
        }
    }

    private static void logSevere(Plugin plugin, @Nullable EnhancedLogger logger, String message, Throwable throwable) {
        if (logger != null) {
            logger.severe(message, throwable);
        } else {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, message, throwable);
        }
    }

    private SchemaMigrator() {}
}
