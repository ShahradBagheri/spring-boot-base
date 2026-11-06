package com.notshahrad.spring_boot_base.common.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    private static class TestEntity extends BaseEntity {
    }

    @Test
    void shouldNotBeDeletedByDefault() {
        TestEntity entity = new TestEntity();

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
}