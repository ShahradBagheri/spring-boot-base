package com.notshahrad.spring_boot_base.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
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

    private static final int ACTOR_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(updatable = false, length = ACTOR_MAX_LENGTH)
    private String createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(length = ACTOR_MAX_LENGTH)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private boolean deleted = false;

    private Instant deletedAt;

    @Column(length = ACTOR_MAX_LENGTH)
    private String deletedBy;

    protected BaseEntity() {
    }

    public void softDelete(String deletedBy) {
        Objects.requireNonNull(deletedBy, "deletedBy must not be null");

        if (deletedBy.isBlank()) {
            throw new IllegalArgumentException("deletedBy must not be blank");
        }

        if (deletedBy.codePointCount(0, deletedBy.length()) > ACTOR_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "deletedBy must not be longer than " + ACTOR_MAX_LENGTH + " characters");
        }

        if (deleted) {
            return;
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
