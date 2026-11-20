package com.notshahrad.spring_boot_base.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private boolean deleted = false;

    private Instant deletedAt;

    @Column(length = 100)
    private String deletedBy;

    protected BaseEntity() {
    }

    public void softDelete(String deletedBy) {
        if (deleted) {
            return;
        }

        Objects.requireNonNull(deletedBy, "deletedBy must not be null");

        if (deletedBy.isBlank()) {
            throw new IllegalArgumentException("deletedBy must not be blank");
        }

        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public void restore() {
        if (!deleted) {
            return;
        }

        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
