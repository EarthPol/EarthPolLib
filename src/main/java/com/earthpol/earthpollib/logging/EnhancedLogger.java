package com.earthpol.earthpollib.logging;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <h1>EnhancedLogger</h1>
 *
 * <p>
 * A lightweight, thread-safe logger that mirrors messages to the server console
 * (via {@link Plugin#getLogger()}) and to a per-plugin log file located at:
 * <br><code>&lt;dataFolder&gt;/logs/&lt;namePrefix&gt;-YYYY-MM-DD.log</code>
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Dual output:</strong> console + file.</li>
 *   <li><strong>Thread-safe ordering:</strong> file writes are serialized with a {@link ReentrantLock}.</li>
 *   <li><strong>Daily rotation:</strong> file name includes date; rotates automatically at midnight by local time.</li>
 *   <li><strong>Flush control:</strong> configurable line-batch flushing; always flushes on {@link Level#SEVERE}.</li>
 *   <li><strong>Debug gate:</strong> disable/enable emission of {@code FINE}/{@code FINER}/{@code FINEST} to both channels.</li>
 *   <li><strong>Stack trace formatting:</strong> uses JDK formatting; optional per-throwable frame capping for file output.</li>
 *   <li><strong>Prefix-free file lines:</strong> file lines omit plugin/channel prefixes for readability;
 *       console formatting remains under JUL/Bukkit control.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class EnhancedLogger {

    private static final DateTimeFormatter DATE_FN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Logger consoleLogger;
    private final Plugin plugin;
    private final String pluginName;
    private final String logicalName;

    private volatile boolean consoleLoggingEnabled = true;
    private volatile boolean fileLoggingEnabled = true;
    private volatile boolean debugEnabled;

    private volatile boolean frameCapEnabled = false;
    private volatile int frameCapLimit = 32;

    private final Lock ioLock = new ReentrantLock();

    private final Path logsDir;
    private volatile PrintWriter fileWriter;
    private String currentDate;
    private int flushEveryNLines = 1;
    private int linesSinceFlush = 0;

    private LogRetentionTask logRetentionTask;

    private EnhancedLogger(Plugin plugin, String logicalName, boolean debugEnabled) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.consoleLogger = plugin.getLogger();
        this.pluginName = plugin.getName();
        this.logicalName = logicalName;
        this.logsDir = plugin.getDataFolder().toPath().resolve("logs");
        this.currentDate = DATE_FN.format(Instant.now());
        this.debugEnabled = debugEnabled;

        this.logRetentionTask = new LogRetentionTask(
                LogRetentionPolicy.MONTHLY,
                1,
                TimeUnit.DAYS,
                this
        );
    }

    public static EnhancedLogger create(Plugin plugin, String namePrefix, boolean debugEnabled) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(namePrefix, "namePrefix");

        String safe = namePrefix.replaceAll("[^a-zA-Z0-9._-]", "_");
        EnhancedLogger el = new EnhancedLogger(plugin, safe, debugEnabled);
        el.initDirs();
        el.setDebugEnabled(debugEnabled);
        return el;
    }

    public static EnhancedLogger create(Plugin plugin, String namePrefix) {
        return create(plugin, namePrefix, false);
    }

    private void initDirs() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            consoleLogger.log(Level.SEVERE, "Failed to prepare log directories for " + pluginName + ": " + e.getMessage(), e);
            fileLoggingEnabled = false;
        }
    }

    public EnhancedLogger enableConsoleLogger(boolean enabled) { this.consoleLoggingEnabled = enabled; return this; }

    public EnhancedLogger enableFile(boolean enabled) { this.fileLoggingEnabled = enabled; return this; }

    public EnhancedLogger setFlushEveryNLines(int n) { this.flushEveryNLines = Math.max(1, n); return this; }

    public EnhancedLogger setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        try {
            consoleLogger.setLevel(enabled ? Level.FINE : Level.INFO);
        } catch (SecurityException ignored) { /* fail soft */ }
        return this;
    }

    public EnhancedLogger setFrameCapEnabled(boolean enabled) { this.frameCapEnabled = enabled; return this; }

    public EnhancedLogger setFrameCapLimit(int maxFrames) { this.frameCapLimit = Math.max(1, maxFrames); return this; }

    public void setLogRetentionTask(LogRetentionTask logRetentionTask) {
        this.logRetentionTask = logRetentionTask;
    }
    public LogRetentionTask getLogRetentionTask() { return logRetentionTask; }

    public boolean isConsoleLoggingEnabled() { return consoleLoggingEnabled; }
    public boolean isFileLoggingEnabled() { return fileLoggingEnabled; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isFrameCapEnabled() { return frameCapEnabled; }
    public int getFrameCapLimit() { return frameCapLimit; }
    public String getPluginName() { return pluginName; }
    public Plugin getPlugin() { return plugin; }
    public String getLogicalName() { return logicalName; }
    public Path getLogsDirectory() { return logsDir; }

    public void debug(String msg) { log(Level.FINE, msg); }

    public void debug(String msg, Throwable t) { log(Level.FINE, msg, t); }

    public void info(String msg)  { log(Level.INFO, msg); }

    public void warn(String msg)  { log(Level.WARNING, msg); }

    public void severe(String msg) { log(Level.SEVERE, msg); }

    public void severe(String msg, Throwable t) { log(Level.SEVERE, msg, t); }

    public void log(Level level, String message) { log(level, message, null); }

    public void log(Level level, String message, Throwable t) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(message, "message");

        if (!debugEnabled && isDebugLevel(level)) return;

        if (consoleLoggingEnabled) {
            if (t == null) consoleLogger.log(level, message);
            else consoleLogger.log(level, message, t);
        }

        if (!fileLoggingEnabled) return;

        String line = formatLine(level, message, t);

        ioLock.lock();
        try {
            String today = DATE_FN.format(Instant.now());
            if (!today.equals(currentDate)) {
                rotateTo(today);
            }
            ensureWriter();
            if (fileWriter != null) {
                fileWriter.write(line);
                if (++linesSinceFlush >= flushEveryNLines || level.intValue() >= Level.SEVERE.intValue()) {
                    fileWriter.flush();
                    linesSinceFlush = 0;
                }
            }
        } catch (IOException e) {
            fileLoggingEnabled = false;
            consoleLogger.log(Level.SEVERE, "Disabling file logging for " + pluginName + "/" + logicalName
                    + " due to IO error: " + e.getMessage(), e);
        } finally {
            ioLock.unlock();
        }
    }

    private String formatLine(Level level, String message, Throwable t) {
        String ts = TS.format(Instant.now());
        String threadName = Thread.currentThread().getName();

        StringBuilder sb = new StringBuilder(256);
        sb.append(ts)
                .append(" [").append(threadName).append(']')
                .append(" [").append(level.getName()).append("] ")
                .append(message)
                .append(System.lineSeparator());

        if (t != null) {
            if (!frameCapEnabled) {
                sb.append(printStackTraceToString(t));
            } else {
                sb.append(printCappedStackTrace(t, frameCapLimit));
            }
        }
        return sb.toString();
    }

    private static String printStackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter(1024);
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String printCappedStackTrace(Throwable t, int cap) {
        StringBuilder sb = new StringBuilder(1024);
        appendThrowable(sb, t, cap, "");
        return sb.toString();
    }

    private static void appendThrowable(StringBuilder sb, Throwable t, int cap, String prefix) {
        final String nl = System.lineSeparator();
        sb.append(prefix).append(t).append(nl);

        StackTraceElement[] frames = t.getStackTrace();
        int shown = Math.min(frames.length, cap);
        for (int i = 0; i < shown; i++) {
            sb.append("\tat ").append(frames[i]).append(nl);
        }
        if (frames.length > cap) {
            sb.append("\t... ").append(frames.length - cap).append(" more").append(nl);
        }

        for (Throwable suppressed : t.getSuppressed()) {
            appendThrowable(sb, suppressed, cap, "Suppressed: ");
        }

        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            appendThrowable(sb, cause, cap, "Caused by: ");
        }
    }

    private Path buildPathForDate(String yyyyMmDd) {
        String filename = logicalName + "-" + yyyyMmDd + ".log";
        return logsDir.resolve(filename);
    }

    private void ensureWriter() throws IOException {
        if (fileWriter != null) return;
        Path path = buildPathForDate(currentDate);
        Writer w = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        );
        fileWriter = new PrintWriter(w);
    }

    private void rotateTo(String newDate) throws IOException {
        if (fileWriter != null) {
            fileWriter.flush();
            fileWriter.close();
            fileWriter = null;
        }
        Files.createDirectories(logsDir);
        currentDate = newDate;
        linesSinceFlush = 0;
    }

    public void forceFlush() {
        ioLock.lock();
        try {
            if (fileWriter != null) fileWriter.flush();
        } finally {
            ioLock.unlock();
        }
    }

    public void close() {
        ioLock.lock();
        try {
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
                fileWriter = null;
            }
        } finally {
            ioLock.unlock();
        }
    }

    private static boolean isDebugLevel(Level level) {
        return level.intValue() <= Level.FINE.intValue();
    }
}
