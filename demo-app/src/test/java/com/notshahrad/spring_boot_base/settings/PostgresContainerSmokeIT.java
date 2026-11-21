package com.notshahrad.spring_boot_base.settings;

import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresContainerSmokeIT extends AbstractPostgresIntegrationTest {

    @Test
    void startsDisposablePostgresAndAcceptsJdbcConnections() throws Exception {
        assertTrue(POSTGRES.isRunning());

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("select 1")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }
}
