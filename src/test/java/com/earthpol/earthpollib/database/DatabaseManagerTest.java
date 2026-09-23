package com.earthpol.earthpollib.database;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    private static final String DEFAULT_JDBC_URL = "jdbc:mariadb://127.0.0.1:3306/settlements"
            + "?tcpKeepAlive=true"
            + "&sessionVariables="
            + "character_set_client=utf8mb4,"
            + "character_set_results=utf8mb4";

    @Test
    void getHikariConfigUsesCurrentMariaDbAndPoolDefaults() {
        DatabaseManager manager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                plugin("DatabaseDefaults")
        );

        HikariConfig config = manager.getHikariConfig();
        MariaDbDataSource dataSource = assertInstanceOf(MariaDbDataSource.class, config.getDataSource());

        assertEquals("DatabaseDefaults_pool", config.getPoolName());
        assertEquals(4, config.getMinimumIdle());
        assertEquals(6, config.getMaximumPoolSize());
        assertEquals(3_000, config.getConnectionTimeout());
        assertEquals(300_000, config.getIdleTimeout());
        assertEquals(1_800_000, config.getMaxLifetime());
        assertEquals(DEFAULT_JDBC_URL, manager.getJdbcUrl());
        assertEquals(
                Map.of(
                        "tcpKeepAlive", "true",
                        "sessionVariables", "character_set_client=utf8mb4,character_set_results=utf8mb4"
                ),
                manager.getConnectionParameters()
        );
        assertEquals("earthpol", dataSource.getUser());
    }

    @Test
    void customizeMutatesHikariConfigBeforeStart() {
        DatabaseManager manager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                plugin("DatabaseCustomize")
        );

        manager.customize(config -> {
            config.setMinimumIdle(2);
            config.setMaximumPoolSize(8);
        });

        HikariConfig config = manager.getHikariConfig();
        assertEquals(2, config.getMinimumIdle());
        assertEquals(8, config.getMaximumPoolSize());
    }

    @Test
    void connectionParametersCanBeAddedReplacedRemovedAndReset() {
        DatabaseManager manager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                plugin("DatabaseParameters")
        );

        manager.setConnectionParameter("socketTimeout", "5000");
        assertEquals(DEFAULT_JDBC_URL + "&socketTimeout=5000", manager.getJdbcUrl());

        manager.setConnectionParameter("tcpKeepAlive", "false");
        assertEquals(
                "jdbc:mariadb://127.0.0.1:3306/settlements"
                        + "?tcpKeepAlive=false"
                        + "&sessionVariables=character_set_client=utf8mb4,character_set_results=utf8mb4"
                        + "&socketTimeout=5000",
                manager.getJdbcUrl()
        );

        manager.removeConnectionParameter("sessionVariables");
        assertEquals(
                "jdbc:mariadb://127.0.0.1:3306/settlements"
                        + "?tcpKeepAlive=false"
                        + "&socketTimeout=5000",
                manager.getJdbcUrl()
        );

        manager.resetConnectionParameters();
        assertEquals(DEFAULT_JDBC_URL, manager.getJdbcUrl());
    }

    @Test
    void customizeConnectionParametersUpdatesConfiguredDataSourceBeforeStart() {
        DatabaseManager manager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                plugin("DatabaseConfiguredParameters")
        );

        HikariConfig config = manager.getHikariConfig();
        MariaDbDataSource dataSource = assertInstanceOf(MariaDbDataSource.class, config.getDataSource());

        manager.customizeConnectionParameters(parameters -> parameters.put("socketTimeout", "5000"));

        assertEquals(DEFAULT_JDBC_URL + "&socketTimeout=5000", manager.getJdbcUrl());
        assertTrue(dataSource.getUrl().contains("socketTimeout=5000"));
    }

    @Test
    void connectionParameterSnapshotIsImmutable() {
        DatabaseManager manager = new DatabaseManager(
                "earthpol",
                "secret",
                "settlements",
                "127.0.0.1",
                "3306",
                plugin("DatabaseSnapshot")
        );

        assertThrows(UnsupportedOperationException.class,
                () -> manager.getConnectionParameters().put("socketTimeout", "5000"));
        assertEquals(DEFAULT_JDBC_URL, manager.getJdbcUrl());
    }

    private static Plugin plugin(String name) {
        Logger logger = Logger.getLogger("EarthPolLibTest-" + name);
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> logger;
                    case "getName" -> name;
                    case "toString" -> "Plugin[" + name + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                }
        );
    }
}
