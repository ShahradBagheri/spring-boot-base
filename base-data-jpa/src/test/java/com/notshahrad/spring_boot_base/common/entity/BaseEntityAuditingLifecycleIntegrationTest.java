package com.notshahrad.spring_boot_base.common.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = BaseEntityAuditingLifecycleIntegrationTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:base_entity_auditing_lifecycle;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class BaseEntityAuditingLifecycleIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AtomicReference<Instant> auditingTime;

    @Test
    @Transactional
    void createsAndAdvancesAuditTimestampsWithoutChangingCreationTime() {
        Instant creationTime = Instant.parse("2026-01-02T03:04:05Z");
        Instant modificationTime = Instant.parse("2026-01-03T04:05:06Z");

        auditingTime.set(creationTime);

        TestRecord record = new TestRecord();
        assertNull(record.getCreatedAt());
        assertNull(record.getUpdatedAt());

        entityManager.persist(record);
        entityManager.flush();

        UUID id = record.getId();
        entityManager.clear();

        TestRecord created = entityManager.find(TestRecord.class, id);
        assertEquals(creationTime, created.getCreatedAt());
        assertEquals(creationTime, created.getUpdatedAt());

        auditingTime.set(modificationTime);
        created.softDelete("integration-test");
        entityManager.flush();
        entityManager.clear();

        TestRecord updated = entityManager.find(TestRecord.class, id);
        assertEquals(creationTime, updated.getCreatedAt());
        assertEquals(modificationTime, updated.getUpdatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(updated.getCreatedAt()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TestRecord.class)
    @EnableJpaAuditing(
            auditorAwareRef = "auditorAware",
            dateTimeProviderRef = "auditingDateTimeProvider"
    )
    static class TestApplication {

        @Bean
        AtomicReference<Instant> auditingTime() {
            return new AtomicReference<>(Instant.parse("2026-01-02T03:04:05Z"));
        }

        @Bean
        DateTimeProvider auditingDateTimeProvider(AtomicReference<Instant> auditingTime) {
            return () -> Optional.of(auditingTime.get());
        }

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("integration-test");
        }
    }
}
