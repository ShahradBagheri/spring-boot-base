package com.notshahrad.spring_boot_base.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApplicationSettingRequest(
        @NotBlank @Size(max = 100) String key,
        @NotBlank @Size(max = 2048) String value
) {
}
