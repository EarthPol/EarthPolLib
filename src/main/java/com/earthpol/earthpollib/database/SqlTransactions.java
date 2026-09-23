package com.earthpol.earthpollib.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class SqlTransactions {
    private SqlTransactions() {}

    /**
     * Owns a borrowed connection, commits successful work, and rolls back failed work.
     * The callback must not close the connection or change transaction boundaries.
     * Statements that implicitly commit (such as MariaDB DDL) cannot be rolled back by this helper.
     */
    public static <T> T execute(DataSource source, SqlWork<T> work) throws SQLException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(work, "work");
        try (Connection connection = source.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean finished = false;
            Throwable failure = null;
            try {
                T result = work.execute(connection);
                connection.commit();
                finished = true;
                return result;
            } catch (SQLException | RuntimeException | Error ex) {
                failure = ex;
                try {
                    connection.rollback();
                    finished = true;
                } catch (SQLException rollbackFailure) {
                    ex.addSuppressed(rollbackFailure);
                }
                throw ex;
            } finally {
                // Enabling auto-commit after a failed rollback could commit unresolved work.
                if (finished && autoCommit) {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException resetFailure) {
                        if (failure != null) failure.addSuppressed(resetFailure);
                        else throw resetFailure;
                    }
                }
            }
        }
    }
}
