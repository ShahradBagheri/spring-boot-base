package com.notshahrad.spring_boot_base.settings;

import com.notshahrad.spring_boot_base.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.Objects;

@Getter
@Entity
@Table(name = "application_settings")
public class ApplicationSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 2048)
    private String value;

    protected ApplicationSetting() {
    }

    public ApplicationSetting(String key, String value) {
        this.key = requireText(key, "key", 100);
        this.value = requireText(value, "value", 2048);
    }

    public void updateValue(String value) {
        this.value = requireText(value, "value", 2048);
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most " + maxLength + " characters");
        }
        return value;
    }
}
