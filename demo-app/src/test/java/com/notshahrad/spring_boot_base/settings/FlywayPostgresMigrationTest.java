package com.notshahrad.spring_boot_base.settings;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayPostgresMigrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_JDBC_URL", matches = "jdbc:postgresql:.+")
    void cleanPostgresSchemaMigratesOnceAndMatchesEntityColumns() throws Exception {
        String url = System.getenv("FLYWAY_TEST_JDBC_URL");
        String user = System.getenv("FLYWAY_TEST_USERNAME");
        String password = System.getenv("FLYWAY_TEST_PASSWORD");
        String schema = "flyway_test_" + UUID.randomUUID().toString().replace("-", "");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .baselineOnMigrate(false)
                .load();

        try {
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);

            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "select column_name from information_schema.columns "
                                + "where table_schema = ? and table_name = 'application_settings'")) {
                    statement.setString(1, schema);
                    Set<String> columns = new HashSet<>();
                    try (ResultSet results = statement.executeQuery()) {
                        while (results.next()) {
                            columns.add(results.getString(1));
                        }
                    }
                    assertEquals(Set.of(
                            "id", "setting_key", "setting_value", "created_at", "created_by",
                            "updated_at", "updated_by", "version", "deleted", "deleted_at", "deleted_by"
                    ), columns);
                }

                try (Statement statement = connection.createStatement();
                     ResultSet results = statement.executeQuery(
                             "select count(*) from " + schema
                                     + ".flyway_schema_history where version = '1' and success = true")) {
                    results.next();
                    assertEquals(1, results.getInt(1));
                }
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }
}
