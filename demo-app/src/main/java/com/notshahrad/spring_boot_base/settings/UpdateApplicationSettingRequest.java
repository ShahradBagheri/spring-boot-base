package com.notshahrad.spring_boot_base.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateApplicationSettingRequest(
        @NotBlank @Size(max = 2048) String value
) {
}
