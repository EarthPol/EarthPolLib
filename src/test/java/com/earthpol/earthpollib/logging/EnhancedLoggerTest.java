package com.earthpol.earthpollib.logging;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhancedLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void loggerWritesToDailyLogFile() throws Exception {
        Plugin plugin = plugin(tempDir.resolve("plugin-data"));
        EnhancedLogger logger = EnhancedLogger.create(plugin, "general", false);

        try {
            logger.enableConsoleLogger(false);
            logger.info("hello world");
            logger.forceFlush();

            Path logFile = Files.list(logger.getLogsDirectory())
                    .findFirst()
                    .orElseThrow();
            String content = Files.readString(logFile);

            assertTrue(content.contains("[INFO] hello world"));
            assertTrue(logFile.getFileName().toString().startsWith("general-"));
        } finally {
            logger.close();
        }
    }

    @Test
    void debugGateSuppressesFineMessagesWhenDisabled() throws Exception {
        Plugin plugin = plugin(tempDir.resolve("plugin-data-debug"));
        EnhancedLogger logger = EnhancedLogger.create(plugin, "debug", false);

        try {
            logger.enableConsoleLogger(false);
            logger.debug("hidden");
            logger.info("shown");
            logger.forceFlush();

            Path logFile = Files.list(logger.getLogsDirectory())
                    .findFirst()
                    .orElseThrow();
            String content = Files.readString(logFile);

            assertFalse(content.contains("hidden"));
            assertTrue(content.contains("shown"));
        } finally {
            logger.close();
        }
    }

    @Test
    void retentionManagerDeletesOldLogsOnly() throws Exception {
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);

        Path oldLog = logsDir.resolve("old.log");
        Path newLog = logsDir.resolve("new.log");
        Path otherFile = logsDir.resolve("notes.txt");
        Files.writeString(oldLog, "old");
        Files.writeString(newLog, "new");
        Files.writeString(otherFile, "notes");

        Instant now = Instant.parse("2026-04-17T12:00:00Z");
        Instant oldCreatedAt = Instant.parse("2026-03-01T00:00:00Z");
        Instant newCreatedAt = Instant.parse("2026-04-16T00:00:00Z");

        Assumptions.assumeTrue(setCreationTime(oldLog, oldCreatedAt));
        Assumptions.assumeTrue(setCreationTime(newLog, newCreatedAt));

        LogRetentionManager.RetentionReport report = LogRetentionManager.enforce(
                logsDir,
                LogRetentionPolicy.MONTHLY,
                java.time.Clock.fixed(now, ZoneId.of("UTC"))
        );

        assertEquals(3, report.scannedFiles);
        assertEquals(1, report.deletedFiles);
        assertFalse(Files.exists(oldLog));
        assertTrue(Files.exists(newLog));
        assertTrue(Files.exists(otherFile));
    }

    private static Plugin plugin(Path dataFolder) {
        Logger logger = Logger.getLogger("EarthPolLibTest-" + dataFolder.getFileName());
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> logger;
                    case "getName" -> "TestPlugin";
                    case "getDataFolder" -> dataFolder.toFile();
                    case "toString" -> "Plugin[TestPlugin]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                }
        );
    }

    private static boolean setCreationTime(Path path, Instant instant) {
        try {
            FileTime expected = FileTime.from(instant);
            Files.setAttribute(path, "basic:creationTime", expected);
            FileTime actual = (FileTime) Files.getAttribute(path, "basic:creationTime");
            return actual.toInstant().equals(expected.toInstant());
        } catch (Exception ex) {
            return false;
        }
    }
}
