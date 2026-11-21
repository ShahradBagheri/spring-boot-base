package com.notshahrad.spring_boot_base.settings;

import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationIT extends AbstractPostgresIntegrationTest {

    @Test
    void migratesAllVersionsValidatesAndDoesNotReapply() throws Exception {
        String schema = "flyway_it_" + UUID.randomUUID().toString().replace("-", "");

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();

        try {
            assertEquals(9, flyway.migrate().migrationsExecuted);
            assertTrue(flyway.validateWithResult().validationSuccessful);
            assertEquals(0, flyway.migrate().migrationsExecuted);

            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                try (var statement = connection.prepareStatement(
                        "select column_name from information_schema.columns "
                                + "where table_schema = ? and table_name = 'application_settings'")) {
                    statement.setString(1, schema);

                    Set<String> columns = new HashSet<>();
                    try (var result = statement.executeQuery()) {
                        while (result.next()) {
                            columns.add(result.getString(1));
                        }
                    }

                    assertEquals(Set.of(
                            "id", "setting_key", "setting_value", "created_at", "created_by",
                            "updated_at", "updated_by", "version", "deleted", "deleted_at", "deleted_by"
                    ), columns);
                }

                try (var statement = connection.prepareStatement(
                        "select table_name from information_schema.tables "
                                + "where table_schema = ? and table_name in ('app_users', 'external_identities', 'refresh_sessions', 'refresh_credentials', 'email_login_identities', 'email_passwords', 'email_auth_challenges', 'phone_login_identities', 'phone_otp_challenges', 'guest_drafts')")) {
                    statement.setString(1, schema);
                    Set<String> tables = new HashSet<>();
                    try (var result = statement.executeQuery()) {
                        while (result.next()) {
                            tables.add(result.getString(1));
                        }
                    }
                    assertEquals(Set.of("app_users", "external_identities", "refresh_sessions", "refresh_credentials", "email_login_identities", "email_passwords", "email_auth_challenges", "phone_login_identities", "phone_otp_challenges", "guest_drafts"), tables);
                }

                try (var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "select count(*) from " + schema
                                     + ".flyway_schema_history where version in ('1', '2', '3', '4', '5', '6', '7', '8', '9') and success = true")) {
                    assertTrue(result.next());
                    assertEquals(9, result.getInt(1));
                }
            }
        } finally {
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }
}
