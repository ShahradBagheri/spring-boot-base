package com.notshahrad.spring_boot_base.settings;

import com.notshahrad.spring_boot_base.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApplicationSettingPostgresIT extends AbstractPostgresIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void persistsInheritedMappingsAndUpdatesOnMigratedPostgresSchema() {
        ApplicationSetting setting = new ApplicationSetting("feature.flag", "off");
        entityManager.persist(setting);
        entityManager.flush();

        UUID id = setting.getId();
        entityManager.clear();

        ApplicationSetting created = entityManager.find(ApplicationSetting.class, id);
        var createdAt = created.getCreatedAt();
        var originalVersion = created.getVersion();

        assertNotNull(id);
        assertNotNull(createdAt);
        assertNotNull(created.getUpdatedAt());
        assertEquals("system", created.getCreatedBy());
        assertEquals("system", created.getUpdatedBy());
        assertFalse(created.isDeleted());

        created.updateValue("on");
        entityManager.flush();
        entityManager.clear();

        ApplicationSetting updated = entityManager.find(ApplicationSetting.class, id);

        assertEquals("on", updated.getValue());
        assertEquals(createdAt, updated.getCreatedAt());
        assertTrue(updated.getVersion() > originalVersion);

        updated.softDelete("integration-test");
        entityManager.flush();
        entityManager.clear();

        ApplicationSetting deleted = entityManager.find(ApplicationSetting.class, id);

        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals("integration-test", deleted.getDeletedBy());
    }

    @Test
    @Transactional
    void enforcesUniqueSettingKeysOnPostgres() {
        entityManager.persist(new ApplicationSetting("unique.setting", "first"));
        entityManager.flush();

        entityManager.persist(new ApplicationSetting("unique.setting", "second"));

        assertThrows(PersistenceException.class, () -> entityManager.flush());
    }

    @Test
    void springBootAppliesFlywayMigrationsBeforeHibernateValidation() {
        Integer successfulVersionOne = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history "
                        + "where version = '1' and success = true",
                Integer.class
        );

        assertEquals(1, successfulVersionOne);
    }
}
