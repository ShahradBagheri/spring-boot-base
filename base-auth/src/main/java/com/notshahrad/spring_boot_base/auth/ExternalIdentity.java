package com.notshahrad.spring_boot_base.auth;

import com.notshahrad.spring_boot_base.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(name = "external_identities", uniqueConstraints = @UniqueConstraint(
        name = "uq_external_identity_issuer_subject", columnNames = {"issuer", "subject"}))
public class ExternalIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 255)
    private String issuer;

    @Column(nullable = false, length = 255)
    private String subject;

    protected ExternalIdentity() {
    }

    public ExternalIdentity(AppUser user, String issuer, String subject) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.issuer = requireIdentityPart(issuer, "issuer");
        this.subject = requireIdentityPart(subject, "subject");
    }

    public AppUser getUser() {
        return user;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    private static String requireIdentityPart(String part, String name) {
        Objects.requireNonNull(part, name + " must not be null");
        if (part.isBlank() || part.length() > 255) {
            throw new IllegalArgumentException(name + " must contain 1–255 characters");
        }
        return part;
    }
}
