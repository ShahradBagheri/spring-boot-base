package com.notshahrad.spring_boot_base.settings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationSettingPersistenceTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAndUpdatesASettingWithInheritedAuditFields() {
        ApplicationSetting setting = new ApplicationSetting("ui.theme", "light");
        entityManager.persist(setting);
        entityManager.flush();

        UUID id = setting.getId();
        entityManager.clear();

        ApplicationSetting stored = entityManager.find(ApplicationSetting.class, id);
        assertEquals("ui.theme", stored.getKey());
        assertEquals("light", stored.getValue());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertNotNull(stored.getVersion());

        stored.updateValue("dark");
        entityManager.flush();
        entityManager.clear();

        ApplicationSetting updated = entityManager.find(ApplicationSetting.class, id);
        assertEquals("dark", updated.getValue());
        assertEquals("ui.theme", updated.getKey());
    }
}
