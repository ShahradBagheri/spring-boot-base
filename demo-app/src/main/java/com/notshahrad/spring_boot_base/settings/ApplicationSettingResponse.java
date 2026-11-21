package com.notshahrad.spring_boot_base.settings;

import java.util.UUID;

public record ApplicationSettingResponse(UUID id, String key, String value) {
}
