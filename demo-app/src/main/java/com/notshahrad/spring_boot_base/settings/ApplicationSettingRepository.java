package com.notshahrad.spring_boot_base.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, UUID> {

    Optional<ApplicationSetting> findByKeyAndDeletedFalse(String key);

    boolean existsByKey(String key);
}
