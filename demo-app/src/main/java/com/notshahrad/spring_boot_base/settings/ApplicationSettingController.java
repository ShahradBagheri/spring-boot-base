package com.notshahrad.spring_boot_base.settings;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class ApplicationSettingController {

    private final ApplicationSettingService service;

    public ApplicationSettingController(ApplicationSettingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationSettingResponse> create(
            @Valid @RequestBody CreateApplicationSettingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{key}")
    public ApplicationSettingResponse find(@PathVariable String key) {
        return service.find(key);
    }

    @PutMapping("/{key}")
    public ApplicationSettingResponse update(@PathVariable String key,
            @Valid @RequestBody UpdateApplicationSettingRequest request) {
        return service.update(key, request);
    }
}
