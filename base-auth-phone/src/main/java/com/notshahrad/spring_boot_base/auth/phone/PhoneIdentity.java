package com.notshahrad.spring_boot_base.auth.phone;

import java.time.Instant;
import java.util.UUID;

public record PhoneIdentity(UUID userId, PhoneNumber phoneNumber, Instant verifiedAt) {
}
