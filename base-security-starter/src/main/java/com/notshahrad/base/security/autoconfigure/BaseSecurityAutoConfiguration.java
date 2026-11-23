package com.notshahrad.base.security.autoconfigure;

import com.notshahrad.base.security.web.SecurityExceptionHandler;
import com.notshahrad.spring_boot_base.config.SecurityConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(beforeName = {
        "com.notshahrad.base.mcp.McpSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, JsonMapper.class})
public class BaseSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityExceptionHandler.class)
    SecurityExceptionHandler baseSecurityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @EnableWebSecurity
    @EnableMethodSecurity
    static class DefaultSecurity {

        @Bean
        SecurityFilterChain baseSecurityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
            return SecurityConfig.authenticatedApi(http, jsonMapper);
        }

        @Bean
        @ConditionalOnMissingBean(UserDetailsService.class)
        UserDetailsService baseSecurityUsers() {
            return new InMemoryUserDetailsManager();
        }
    }
}
