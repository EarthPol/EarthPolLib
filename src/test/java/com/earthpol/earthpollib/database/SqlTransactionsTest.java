package com.earthpol.earthpollib.database;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlTransactionsTest {
    @Test
    void commitsSuccessfulWorkAndReturnsTheConnection() throws SQLException {
        Jdbc jdbc = new Jdbc();
        int result = SqlTransactions.execute(jdbc.source, connection -> 42);
        assertEquals(42, result);
        assertEquals(List.of("auto:false", "commit", "auto:true", "close"), jdbc.calls);
    }

    @Test
    void rollsBackSqlAndRuntimeFailures() {
        for (Exception failure : List.of(new SQLException("query failed"), new IllegalArgumentException("invalid state"))) {
            Jdbc jdbc = new Jdbc();
            Exception actual = assertThrows(Exception.class, () -> SqlTransactions.execute(jdbc.source, connection -> {
                if (failure instanceof SQLException sql) throw sql;
                throw (RuntimeException) failure;
            }));
            assertSame(failure, actual);
            assertEquals(List.of("auto:false", "rollback", "auto:true", "close"), jdbc.calls);
        }
    }

    @Test
    void commitFailureRollsBackAndNeverReturnsSuccess() {
        Jdbc jdbc = new Jdbc();
        jdbc.commitFailure = new SQLException("commit uncertain");
        assertSame(jdbc.commitFailure, assertThrows(SQLException.class,
                () -> SqlTransactions.execute(jdbc.source, connection -> 42)));
        assertEquals(List.of("auto:false", "commit", "rollback", "auto:true", "close"), jdbc.calls);
    }

    @Test
    void failedRollbackDoesNotReenableAutoCommitAndPreservesCleanupErrors() {
        Jdbc jdbc = new Jdbc();
        jdbc.rollbackFailure = new SQLException("rollback failed");
        jdbc.closeFailure = new SQLException("close failed");
        SQLException failure = new SQLException("work failed");
        SQLException actual = assertThrows(SQLException.class,
                () -> SqlTransactions.execute(jdbc.source, connection -> { throw failure; }));
        assertSame(failure, actual);
        assertArrayEquals(new Throwable[]{jdbc.rollbackFailure, jdbc.closeFailure}, actual.getSuppressed());
        assertEquals(List.of("auto:false", "rollback", "close"), jdbc.calls);
    }

    @Test
    void respectsPoolAutoCommitDefaultAndClosesAfterSetupFailure() throws SQLException {
        Jdbc manual = new Jdbc();
        manual.autoCommit = false;
        SqlTransactions.execute(manual.source, connection -> null);
        assertEquals(List.of("auto:false", "commit", "close"), manual.calls);
        Jdbc broken = new Jdbc();
        broken.setupFailure = new SQLException("cannot begin");
        assertSame(broken.setupFailure, assertThrows(SQLException.class,
                () -> SqlTransactions.execute(broken.source, connection -> fail("Work must not start"))));
        assertEquals(List.of("auto:false", "close"), broken.calls);
    }

    private static final class Jdbc {
        final List<String> calls = new ArrayList<>();
        boolean autoCommit = true;
        SQLException commitFailure;
        SQLException rollbackFailure;
        SQLException closeFailure;
        SQLException setupFailure;
        final Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, args) -> switch (m.getName()) {
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> {
                        calls.add("auto:" + args[0]);
                        if (setupFailure != null) throw setupFailure;
                        yield null;
                    }
                    case "commit" -> { calls.add("commit"); if (commitFailure != null) throw commitFailure; yield null; }
                    case "rollback" -> { calls.add("rollback"); if (rollbackFailure != null) throw rollbackFailure; yield null; }
                    case "close" -> { calls.add("close"); if (closeFailure != null) throw closeFailure; yield null; }
                    default -> null;
                });
        final DataSource source = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (p, m, args) -> m.getName().equals("getConnection") ? connection : null);
    }
}
