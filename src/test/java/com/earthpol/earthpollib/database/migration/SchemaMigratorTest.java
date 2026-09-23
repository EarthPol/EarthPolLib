package com.earthpol.earthpollib.database.migration;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaMigratorTest {

    @Test
    void discoverMigrationsFindsBundledSqlFilesInOrder() {
        Plugin plugin = plugin("SchemaMigratorTest");

        List<SchemaMigrator.Migration> migrations = SchemaMigrator.discoverMigrations(
                plugin,
                List.of("db/migration/test")
        );

        assertEquals(
                List.of(
                        "db/migration/test/V001__create_people.sql",
                        "db/migration/test/V002__insert_people.sql"
                ),
                migrations.stream().map(SchemaMigrator.Migration::resourcePath).toList()
        );
    }

    @Test
    void discoverMigrationsRejectsMissingLocation() {
        Plugin plugin = plugin("SchemaMigratorMissing");

        assertThrows(
                IllegalStateException.class,
                () -> SchemaMigrator.discoverMigrations(plugin, List.of("db/migration/missing"))
        );
    }

    @Test
    void splitStatementsIgnoresCommentsAndQuotedSemicolons() {
        String sql = """
                -- create a table
                CREATE TABLE test_people (name VARCHAR(64));
                INSERT INTO test_people(name, note) VALUES ('alpha;beta', "gamma;delta");
                /* ignored; comment */
                UPDATE test_people
                SET name = 'semi'';quoted'
                WHERE name = 'alpha;beta';
                # trailing comment
                """;

        assertEquals(
                List.of(
                        "CREATE TABLE test_people (name VARCHAR(64))",
                        "INSERT INTO test_people(name, note) VALUES ('alpha;beta', \"gamma;delta\")",
                        "UPDATE test_people\nSET name = 'semi'';quoted'\nWHERE name = 'alpha;beta'"
                ),
                SchemaMigrator.splitStatements(sql)
        );
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
