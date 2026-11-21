package com.notshahrad.spring_boot_base.auth;

import com.notshahrad.spring_boot_base.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "display_name", length = 200)
    private String displayName;

    protected AppUser() {
    }

    public AppUser(String email, boolean emailVerified, String displayName) {
        refreshProfile(email, emailVerified, displayName);
    }

    public void refreshProfile(String email, boolean verified, String displayName) {
        if (email != null && !email.isBlank()) {
            if (email.length() > 320) {
                throw new IllegalArgumentException("Email exceeds 320 characters");
            }
            if (verified || this.email == null) {
                this.email = email;
                this.emailVerified = verified;
            }
        }
        if (displayName != null && !displayName.isBlank()) {
            if (displayName.length() > 200) {
                throw new IllegalArgumentException("Display name exceeds 200 characters");
            }
            this.displayName = displayName;
        }
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getDisplayName() {
        return displayName;
    }
}
