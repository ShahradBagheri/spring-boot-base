package com.notshahrad.spring_boot_base.auth;

import java.security.Principal;
import java.util.UUID;

public interface LocalUserPrincipal extends Principal {

    UUID getLocalUserId();
}
