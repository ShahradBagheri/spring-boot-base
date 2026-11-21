package com.notshahrad.spring_boot_base.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/example")
public class SecurityExampleController {

    @GetMapping("/me")
    public Map<String, String> currentUser(Principal principal) {
        return Map.of("username", principal.getName());
    }

    @GetMapping("/admin")
    public Map<String, String> admin() {
        return Map.of("message", "Admin access granted");
    }
}
