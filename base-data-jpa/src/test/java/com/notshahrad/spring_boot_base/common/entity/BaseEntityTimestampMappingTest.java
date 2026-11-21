package com.notshahrad.spring_boot_base.common.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BaseEntityTimestampMappingTest {

    @Test
    void createdAtUsesNonUpdatableCreationAuditMapping() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("createdAt");

        assertEquals(Instant.class, field.getType());
        assertNotNull(field.getAnnotation(CreatedDate.class));

        Column column = field.getAnnotation(Column.class);
        assertNotNull(column);
        assertFalse(column.nullable());
        assertFalse(column.updatable());
    }

    @Test
    void updatedAtUsesModificationAuditMapping() throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField("updatedAt");

        assertEquals(Instant.class, field.getType());
        assertNotNull(field.getAnnotation(LastModifiedDate.class));

        Column column = field.getAnnotation(Column.class);
        assertNotNull(column);
        assertFalse(column.nullable());
    }
}
