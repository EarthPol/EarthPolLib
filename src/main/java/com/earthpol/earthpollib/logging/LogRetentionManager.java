package com.earthpol.earthpollib.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <h1>LogRetentionManager</h1>
 *
 * <p>
 * A <strong>stateless</strong> utility for enforcing retention on log files. Given a directory and a
 * {@link LogRetentionPolicy}, this class deletes files (non-recursively) whose timestamp is older
 * than a computed cutoff. The timestamp used is the file system's <em>creation time</em> when
 * available; if creation time is unsupported or unreadable, the <em>last-modified time</em> is used
 * as a fallback.
 * </p>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Only files whose names end <strong>exactly</strong> with {@code ".log"} are eligible for deletion.</li>
 *   <li>Directories are ignored. Symlinks are processed as regular files subject to
 *       {@link Files#isRegularFile(Path, LinkOption...) isRegularFile(...)} with {@link LinkOption#NOFOLLOW_LINKS}.</li>
 *   <li>Operations are non-recursive: only the immediate children of the specified directory are considered.</li>
 *   <li>When the policy is {@link LogRetentionPolicy#NEVER}, this class performs no deletions and returns
 *       an empty report.</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *   <li>If the target directory does not exist or is not a directory, an empty {@link RetentionReport}
 *       is returned.</li>
 *   <li>If file attributes cannot be read, the file is treated as "new" (by using {@link Instant#now()})
 *       to avoid accidental deletion.</li>
 *   <li>Failures to delete specific files are recorded in {@link RetentionReport#failedDeletes}.</li>
 *   <li>Listing the directory may fail; such failure is recorded in {@link RetentionReport#failedDeletes}
 *       and the method returns a report with whatever partial information is available.</li>
 * </ul>
 *
 * <h2>Convenience</h2>
 * <p>
 * Overloads are provided to enforce retention directly against an {@code EnhancedLogger} instance,
 * by reading its logs directory via {@code EnhancedLogger#getLogsDirectory()}.
 * </p>
 *
 * <p><strong>Thread-safety:</strong> The class is stateless and thus thread-safe.</p>
 */
@SuppressWarnings("unused")
public final class LogRetentionManager {

    private LogRetentionManager() { /* no instances */ }

    /**
     * Result object returned by retention enforcement methods.
     * Provides counts, size savings, and a list of paths that failed to delete.
     */
    public static final class RetentionReport {
        public final Path directory;
        public final LogRetentionPolicy policy;
        public final Instant cutoff;
        public final int scannedFiles;
        public final int eligibleForDeletion;
        public final int deletedFiles;
        public final long bytesFreed;
        public final List<Path> failedDeletes;

        private RetentionReport(Path directory,
                                LogRetentionPolicy policy,
                                Instant cutoff,
                                int scannedFiles,
                                int eligibleForDeletion,
                                int deletedFiles,
                                long bytesFreed,
                                List<Path> failedDeletes) {
            this.directory = directory;
            this.policy = policy;
            this.cutoff = cutoff;
            this.scannedFiles = scannedFiles;
            this.eligibleForDeletion = eligibleForDeletion;
            this.deletedFiles = deletedFiles;
            this.bytesFreed = bytesFreed;
            this.failedDeletes = failedDeletes;
        }

        @Override
        public String toString() {
            return "RetentionReport{" +
                    "dir=" + directory +
                    ", policy=" + policy +
                    ", cutoff=" + cutoff +
                    ", scanned=" + scannedFiles +
                    ", eligible=" + eligibleForDeletion +
                    ", deleted=" + deletedFiles +
                    ", bytesFreed=" + bytesFreed +
                    ", failedDeletes=" + failedDeletes +
                    '}';
        }
    }

    public static RetentionReport enforce(@NotNull EnhancedLogger logger,
                                          @NotNull LogRetentionPolicy policy) {
        Objects.requireNonNull(logger, "logger");
        Path dir = resolveLogsDir(logger.getLogsDirectory());
        return enforce(dir, policy, Clock.systemDefaultZone());
    }

    public static RetentionReport enforce(@NotNull EnhancedLogger logger,
                                          @NotNull LogRetentionPolicy policy,
                                          @NotNull Clock clock) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(clock, "clock");
        Path dir = resolveLogsDir(logger.getLogsDirectory());
        return enforce(dir, policy, clock);
    }

    private static Path resolveLogsDir(Path dir) {
        return Objects.requireNonNull(dir, "logger.getLogsDirectory() returned null");
    }

    public static RetentionReport enforce(@NotNull Path directory,
                                          @NotNull LogRetentionPolicy policy) {
        return enforce(directory, policy, Clock.systemDefaultZone());
    }

    public static RetentionReport enforce(@NotNull Path directory,
                                          @NotNull LogRetentionPolicy policy,
                                          @NotNull Clock clock) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(clock, "clock");

        if (policy == LogRetentionPolicy.NEVER) {
            return new RetentionReport(
                    directory, policy, null, 0, 0, 0, 0L, List.of()
            );
        }

        Instant cutoff = computeCutoff(policy, clock);
        assert cutoff != null;

        int scanned = 0;
        int eligible = 0;
        int deleted = 0;
        long freed = 0L;
        List<Path> failures = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            return new RetentionReport(
                    directory, policy, cutoff, 0, 0, 0, 0L, List.of()
            );
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) continue;
                if (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) continue;

                scanned++;

                if (!isDeletableLogFile(child)) continue;

                Instant fileInstant = fileCreationOrModifiedInstant(child);
                if (fileInstant.isBefore(cutoff)) {
                    eligible++;

                    long size = 0L;
                    try {
                        size = Files.size(child);
                    } catch (IOException ignore) {
                        // If size can't be read, still attempt delete
                    }

                    try {
                        Files.deleteIfExists(child);
                        deleted++;
                        freed += size;
                    } catch (IOException ex) {
                        failures.add(child);
                    }
                }
            }
        } catch (IOException ex) {
            failures.add(directory);
        }

        return new RetentionReport(directory, policy, cutoff, scanned, eligible, deleted, freed, List.copyOf(failures));
    }

    private static boolean isDeletableLogFile(Path p) {
        if (p == null) return false;
        Path fileName = p.getFileName();
        if (fileName == null) return false;
        String name = fileName.toString();
        return name.endsWith(".log");
    }

    private static @Nullable Instant computeCutoff(@NotNull LogRetentionPolicy policy, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return switch (policy) {
            case WEEKLY  -> now.minusWeeks(1).toInstant();
            case MONTHLY -> now.minusMonths(1).toInstant();
            case NEVER   -> null;
        };
    }

    private static Instant fileCreationOrModifiedInstant(Path p) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            FileTime ct = a.creationTime();
            if (ct != null && ct.toMillis() > 0L) {
                return ct.toInstant();
            }
            return a.lastModifiedTime().toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }
}
