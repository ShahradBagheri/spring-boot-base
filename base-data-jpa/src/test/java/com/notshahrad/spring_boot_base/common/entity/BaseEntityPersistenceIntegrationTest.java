package com.notshahrad.spring_boot_base.common.entity;

import com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
    void retainsHashSetMembershipAfterPersistingEntity() {
        TestRecord record = new TestRecord();
        Set<TestRecord> records = new HashSet<>();
        int originalHashCode = record.hashCode();

        records.add(record);
        entityManager.persist(record);
        entityManager.flush();

        assertNotNull(record.getId());
        assertEquals(originalHashCode, record.hashCode());
        assertTrue(records.contains(record));
    }

    @Test
    @Transactional
    void comparesDetachedAndManagedInstancesByIdentifier() {
        TestRecord record = new TestRecord();
        entityManager.persist(record);
        entityManager.flush();
        UUID id = record.getId();
        entityManager.clear();

        TestRecord loaded = entityManager.find(TestRecord.class, id);

        assertNotSame(record, loaded);
        assertEquals(record, loaded);
        assertEquals(loaded, record);
        assertEquals(record.hashCode(), loaded.hashCode());
    }

    @Test
    @Transactional
    void comparesHibernateProxyAndEntitySymmetrically() {
        TestRecord record = new TestRecord();
        entityManager.persist(record);
        entityManager.flush();
        UUID id = record.getId();
        entityManager.clear();

        TestRecord proxy = entityManager.getReference(TestRecord.class, id);

        assertInstanceOf(HibernateProxy.class, proxy);
        assertEquals(record, proxy);
        assertEquals(proxy, record);
        assertEquals(record.hashCode(), proxy.hashCode());

        Set<TestRecord> storedEntities = new HashSet<>();
        storedEntities.add(record);
        assertTrue(storedEntities.contains(proxy));

        Set<TestRecord> storedProxies = new HashSet<>();
        storedProxies.add(proxy);
        assertTrue(storedProxies.contains(record));
    }

    @Test
    void mapsUuidIdentifierFromBaseEntity() {
        var entityType = entityManager.getMetamodel().entity(TestRecord.class);
        var identifier = entityType.getId(UUID.class);

        assertEquals("id", identifier.getName());
        assertEquals(UUID.class, identifier.getJavaType());
        assertEquals(BaseEntity.class, identifier.getJavaMember().getDeclaringClass());
    }

    @Test
    @Transactional
    void generatesDistinctIdentifiersAndReloadsPersistedEntities() {
        TestRecord first = new TestRecord();
        TestRecord second = new TestRecord();

        assertNull(first.getId());
        assertNull(second.getId());

        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();

        UUID firstId = first.getId();
        UUID secondId = second.getId();

        assertNotNull(firstId);
        assertNotNull(secondId);
        assertNotEquals(firstId, secondId);

        entityManager.clear();

        assertNotNull(entityManager.find(TestRecord.class, firstId));
        assertNotNull(entityManager.find(TestRecord.class, secondId));
    }

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
