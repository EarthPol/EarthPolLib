package com.earthpol.earthpollib.logging;

/**
 * Policy specifying how far back to retain log files.
 */
public enum LogRetentionPolicy {
    /**
     * Retain approximately one week of logs:
     * files older than {@code now() - 1 week} are eligible for deletion.
     */
    WEEKLY,
    /**
     * Retain approximately one calendar month of logs:
     * files older than {@code now() - 1 month} are eligible for deletion.
     */
    MONTHLY,
    /**
     * Do not delete any files (no-op).
     */
    NEVER
}
