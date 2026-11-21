package com.notshahrad.spring_boot_base.common.entity;

import com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = BaseEntityPersistenceIntegrationTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:base_data_jpa_test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class BaseEntityPersistenceIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsInheritedUuidVersionAndAuditFields() {
        TestRecord record = new TestRecord();
        entityManager.persist(record);
        entityManager.flush();
        UUID id = record.getId();
        entityManager.clear();

        TestRecord stored = entityManager.find(TestRecord.class, id);
        assertNotNull(id);
        assertNotNull(stored);
        assertNotNull(stored.getVersion());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertEquals("integration-test", stored.getCreatedBy());
        assertEquals("integration-test", stored.getUpdatedBy());
        assertFalse(stored.isDeleted());
    }

    @Test
    @Transactional
    void persistsSoftDeletionAndRestoration() {
        TestRecord record = new TestRecord();
        entityManager.persist(record);
        entityManager.flush();
        UUID id = record.getId();

        record.softDelete("integration-test");
        entityManager.flush();
        entityManager.clear();

        TestRecord deleted = entityManager.find(TestRecord.class, id);
        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals("integration-test", deleted.getDeletedBy());

        deleted.restore();
        entityManager.flush();
        entityManager.clear();

        TestRecord restored = entityManager.find(TestRecord.class, id);
        assertFalse(restored.isDeleted());
        assertNull(restored.getDeletedAt());
        assertNull(restored.getDeletedBy());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TestRecord.class)
    @Import(JpaAuditingConfiguration.class)
    static class TestApplication {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("integration-test");
        }
    }
}

@Entity
@Table(name = "base_entity_test_record")
class TestRecord extends BaseEntity {
    protected TestRecord() {
    }
}
