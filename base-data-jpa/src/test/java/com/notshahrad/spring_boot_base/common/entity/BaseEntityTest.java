package com.notshahrad.spring_boot_base.common.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    private static class TestEntity extends BaseEntity {
    }

    private static class OtherEntity extends BaseEntity {
    }

    @Test
    void distinctTransientEntitiesAreNotEqual() {
        TestEntity first = new TestEntity();
        TestEntity second = new TestEntity();

        assertEquals(first, first);
        assertNotEquals(first, second);
        assertNotEquals(second, first);
        assertNotEquals(first, null);
        assertNotEquals(first, new Object());
    }

    @Test
    void differentEntityTypesWithSameIdentifierAreNotEqual() {
        UUID id = UUID.randomUUID();
        TestEntity first = new TestEntity();
        OtherEntity second = new OtherEntity();

        ReflectionTestUtils.setField(first, "id", id);
        ReflectionTestUtils.setField(second, "id", id);

        assertNotEquals(first, second);
        assertNotEquals(second, first);
    }

    @Test
    void transientEntityHashCodeIsStableAcrossMutableFields() {
        TestEntity entity = new TestEntity();
        Set<TestEntity> entities = new HashSet<>();
        int originalHashCode = entity.hashCode();

        entities.add(entity);
        entity.softDelete("test-user");

        assertEquals(originalHashCode, entity.hashCode());
        assertTrue(entities.contains(entity));

        entity.restore();

        assertEquals(originalHashCode, entity.hashCode());
        assertTrue(entities.contains(entity));
    }

    @Test
    void shouldStartActiveAndUnaudited() {
        TestEntity entity = new TestEntity();

        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getCreatedBy());
        assertNull(entity.getUpdatedAt());
        assertNull(entity.getUpdatedBy());
        assertNull(entity.getVersion());
        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }

    @Test
    void shouldSoftDeleteEntity() {
        TestEntity entity = new TestEntity();
        Instant beforeDeletion = Instant.now();

        entity.softDelete("user-123");

        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());
        assertEquals("user-123", entity.getDeletedBy());
        assertFalse(entity.getDeletedAt().isBefore(beforeDeletion));
    }

    @Test
    void shouldNotOverwriteDeletionInformationWhenDeletedAgain() {
        TestEntity entity = new TestEntity();

        entity.softDelete("user-123");

        Instant originalDeletedAt = entity.getDeletedAt();
        String originalDeletedBy = entity.getDeletedBy();

        entity.softDelete("user-456");

        assertEquals(originalDeletedAt, entity.getDeletedAt());
        assertEquals(originalDeletedBy, entity.getDeletedBy());
    }

    @Test
    void shouldRestoreDeletedEntity() {
        TestEntity entity = new TestEntity();

        entity.softDelete("user-123");
        entity.restore();

        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }

    @Test
    void shouldDoNothingWhenRestoringActiveEntity() {
        TestEntity entity = new TestEntity();

        assertDoesNotThrow(entity::restore);

        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }

    @Test
    void shouldRejectNullDeletedBy() {
        TestEntity entity = new TestEntity();

        assertThrows(
                NullPointerException.class,
                () -> entity.softDelete(null)
        );
    }

    @Test
    void shouldRejectBlankDeletedBy() {
        TestEntity entity = new TestEntity();

        assertThrows(
                IllegalArgumentException.class,
                () -> entity.softDelete(" ")
        );
    }

    @Test
    void shouldRejectBlankDeletedByVariants() {
        TestEntity entity = new TestEntity();

        assertThrows(IllegalArgumentException.class, () -> entity.softDelete(""));
        assertThrows(IllegalArgumentException.class, () -> entity.softDelete("\t\n"));
    }

    @Test
    void shouldValidateDeletedByEvenWhenAlreadyDeleted() {
        TestEntity entity = new TestEntity();

        entity.softDelete("user-123");

        assertThrows(NullPointerException.class, () -> entity.softDelete(null));
        assertThrows(IllegalArgumentException.class, () -> entity.softDelete(" "));

        assertTrue(entity.isDeleted());
        assertEquals("user-123", entity.getDeletedBy());
    }

    @Test
    void shouldKeepDeletionStateConsistent() {
        TestEntity entity = new TestEntity();

        entity.softDelete("user-123");
        assertTrue(entity.isDeleted());
        assertNotNull(entity.getDeletedAt());
        assertNotNull(entity.getDeletedBy());

        entity.restore();
        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());
        assertNull(entity.getDeletedBy());
    }

    @Test
    void shouldRecordNewDeletionAfterRestore() {
        TestEntity entity = new TestEntity();

        entity.softDelete("user-123");
        Instant firstDeletion = entity.getDeletedAt();

        entity.restore();
        entity.softDelete("user-456");

        assertEquals("user-456", entity.getDeletedBy());
        assertTrue(entity.isDeleted());
        assertFalse(entity.getDeletedAt().isBefore(firstDeletion));
    }

    @Test
    void shouldStoreDeletedByVerbatim() {
        TestEntity entity = new TestEntity();

        entity.softDelete(" user-123 ");

        assertEquals(" user-123 ", entity.getDeletedBy());
    }

    @Test
    void shouldAcceptDeletedByOfMaximumLength() {
        TestEntity entity = new TestEntity();
        String actor = "a".repeat(100);

        entity.softDelete(actor);

        assertEquals(actor, entity.getDeletedBy());
    }

    @Test
    void shouldCountCharactersRatherThanUtf16Units() {
        TestEntity entity = new TestEntity();
        String actor = "\uD83D\uDC4D".repeat(100);

        entity.softDelete(actor);

        assertEquals(actor, entity.getDeletedBy());
    }

    @Test
    void shouldRejectDeletedByLongerThanColumnAllows() {
        TestEntity entity = new TestEntity();

        assertThrows(
                IllegalArgumentException.class,
                () -> entity.softDelete("a".repeat(101))
        );

        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedBy());
    }

    @Test
    void shouldRejectOverlongDeletedByEvenWhenAlreadyDeleted() {
        TestEntity entity = new TestEntity();
        entity.softDelete("user-123");

        assertThrows(
                IllegalArgumentException.class,
                () -> entity.softDelete("a".repeat(101))
        );

        assertEquals("user-123", entity.getDeletedBy());
    }
}
