package com.earthpol.earthpollib.database;

import java.sql.Connection;
import java.sql.SQLException;

/** SQL work using a borrowed connection. Close statements/results and return materialized data. */
@FunctionalInterface
public interface SqlWork<T> {
    T execute(Connection connection) throws SQLException;
}
