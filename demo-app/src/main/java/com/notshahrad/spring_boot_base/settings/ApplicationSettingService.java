package com.notshahrad.spring_boot_base.settings;

import com.notshahrad.spring_boot_base.common.exception.BusinessException;
import com.notshahrad.spring_boot_base.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationSettingService {

    private final ApplicationSettingRepository repository;

    public ApplicationSettingService(ApplicationSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ApplicationSettingResponse create(CreateApplicationSettingRequest request) {
        if (repository.existsByKey(request.key())) {
            throw new BusinessException("Setting key already exists");
        }

        ApplicationSetting setting = repository.save(new ApplicationSetting(request.key(), request.value()));
        return toResponse(setting);
    }

    @Transactional(readOnly = true)
    public ApplicationSettingResponse find(String key) {
        return toResponse(findActive(key));
    }

    @Transactional
    public ApplicationSettingResponse update(String key, UpdateApplicationSettingRequest request) {
        ApplicationSetting setting = findActive(key);
        setting.updateValue(request.value());
        return toResponse(setting);
    }

    private ApplicationSetting findActive(String key) {
        return repository.findByKeyAndDeletedFalse(key)
                .orElseThrow(() -> new ResourceNotFoundException("ApplicationSetting", key));
    }

    private static ApplicationSettingResponse toResponse(ApplicationSetting setting) {
        return new ApplicationSettingResponse(setting.getId(), setting.getKey(), setting.getValue());
    }
}
