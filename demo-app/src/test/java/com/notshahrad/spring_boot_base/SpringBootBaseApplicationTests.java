package com.notshahrad.spring_boot_base;

import com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration;
import com.notshahrad.spring_boot_base.common.entity.BaseEntity;
import com.notshahrad.spring_boot_base.common.exception.BaseException;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SpringBootBaseApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuditorAware<String> auditorAware;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    void webSocketEndpointIsDisabledByDefault() {
        assertTrue(applicationContext.getBeansOfType(
                com.notshahrad.base.websocket.BaseWebSocketConfiguration.class).isEmpty());
        assertTrue(applicationContext.getBeansOfType(
                org.springframework.web.socket.messaging.SubProtocolWebSocketHandler.class).isEmpty());
    }

    @Test
    void consumesBaseCore() {
        assertTrue(BaseException.class.isAssignableFrom(
                com.notshahrad.spring_boot_base.common.exception.BusinessException.class));
    }

    @Test
    void consumesBaseDataJpa() {
        assertTrue(BaseEntity.class.isAnnotationPresent(MappedSuperclass.class));
        assertNotNull(applicationContext.getBean(JpaAuditingConfiguration.class));
        assertTrue(applicationContext.containsBean("jpaAuditingHandler"));
        assertEquals("system", auditorAware.getCurrentAuditor().orElseThrow());
    }
}
