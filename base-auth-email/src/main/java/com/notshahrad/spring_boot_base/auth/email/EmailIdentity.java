package com.notshahrad.spring_boot_base.auth.email;

import java.time.Instant;
import java.util.UUID;

public record EmailIdentity(UUID userId, LoginEmail address, Instant verifiedAt) {

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
