package com.notshahrad.base.auth.email.autoconfigure;

import com.notshahrad.spring_boot_base.auth.email.EmailIdentityRepository;
import com.notshahrad.spring_boot_base.auth.email.EmailPasswordService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "base.auth.email", name = "enabled", havingValue = "true")
@Import({EmailIdentityRepository.class, EmailPasswordService.class})
public class BaseEmailAuthAutoConfiguration {
}
