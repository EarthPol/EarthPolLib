package com.earthpol.earthpollib.logging;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * <h1>LogRetentionTask</h1>
 *
 * <p>
 * Periodically enforces a {@link LogRetentionPolicy} against the directory
 * used by an {@link EnhancedLogger}. This uses Paper's async scheduler
 * ({@link Bukkit#getAsyncScheduler()}) to run
 * {@link LogRetentionManager#enforce(EnhancedLogger, LogRetentionPolicy)}
 * on a fixed schedule.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Runs asynchronously at a fixed rate.</li>
 *   <li>Deletes only files ending with {@code ".log"} (see {@link LogRetentionManager}).</li>
 *   <li>Keeps a bounded in-memory buffer of recent reports (most recent last).</li>
 *   <li>Logs a concise summary after each run using the provided {@link EnhancedLogger}.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class LogRetentionTask implements AutoCloseable {

    private static final int DEFAULT_REPORT_BUFFER = 20;

    private final LogRetentionPolicy logRetentionPolicy;
    private final long taskInterval;
    private final TimeUnit taskIntervalUnit;
    private final EnhancedLogger enhancedLogger;
    private final Plugin plugin;

    private volatile ScheduledTask handle;

    private final Deque<LogRetentionManager.RetentionReport> reportBuffer = new ArrayDeque<>(DEFAULT_REPORT_BUFFER);
    private final int reportBufferCapacity;

    public LogRetentionTask(
            LogRetentionPolicy logRetentionPolicy,
            long taskInterval,
            TimeUnit taskIntervalUnit,
            EnhancedLogger enhancedLogger
    ) {
        this(logRetentionPolicy, taskInterval, taskIntervalUnit, enhancedLogger, DEFAULT_REPORT_BUFFER);
    }

    public LogRetentionTask(
            LogRetentionPolicy logRetentionPolicy,
            long taskInterval,
            TimeUnit taskIntervalUnit,
            EnhancedLogger enhancedLogger,
            int reportBufferCapacity
    ) {
        this.logRetentionPolicy = Objects.requireNonNull(logRetentionPolicy, "logRetentionPolicy");
        this.taskInterval = taskInterval;
        this.taskIntervalUnit = Objects.requireNonNull(taskIntervalUnit, "taskIntervalUnit");
        this.enhancedLogger = Objects.requireNonNull(enhancedLogger, "enhancedLogger");
        this.reportBufferCapacity = Math.max(1, reportBufferCapacity);
        this.plugin = Objects.requireNonNull(enhancedLogger.getPlugin(), "enhancedLogger.getPlugin() returned null");
    }

    public void start() {
        if (handle != null) return;

        Consumer<ScheduledTask> runner = (scheduledTask) -> {
            try {
                runOnce();
            } catch (Throwable t) {
                enhancedLogger.severe("Unhandled exception during log retention task", t);
            }
        };

        handle = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                runner,
                taskInterval,
                taskInterval,
                taskIntervalUnit
        );
    }

    public void startNow() {
        runOnce();
        start();
    }

    public void stop() {
        ScheduledTask h = handle;
        if (h != null) {
            try {
                h.cancel();
            } finally {
                handle = null;
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    public void runOnce() {
        LogRetentionManager.RetentionReport report =
                LogRetentionManager.enforce(enhancedLogger, logRetentionPolicy);

        appendReport(report);

        if (report.deletedFiles > 0 || !report.failedDeletes.isEmpty()) {
            enhancedLogger.info(String.format(
                    "Log retention: policy=%s cutoff=%s deleted=%d eligible=%d scanned=%d freed=%dB failures=%d",
                    report.policy,
                    report.cutoff,
                    report.deletedFiles,
                    report.eligibleForDeletion,
                    report.scannedFiles,
                    report.bytesFreed,
                    report.failedDeletes.size()
            ));
        } else {
            enhancedLogger.debug(String.format(
                    "Log retention: policy=%s cutoff=%s (no files deleted)",
                    report.policy, report.cutoff
            ));
        }
    }

    public List<LogRetentionManager.RetentionReport> getRetentionReports() {
        return new ArrayList<>(reportBuffer);
    }

    public boolean isRunning() {
        return handle != null;
    }

    private void appendReport(LogRetentionManager.RetentionReport report) {
        if (reportBuffer.size() >= reportBufferCapacity) {
            reportBuffer.pollFirst();
        }
        reportBuffer.addLast(report);
    }
}
