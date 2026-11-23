package com.notshahrad.base.auth.oauth2.autoconfigure;

import com.notshahrad.spring_boot_base.auth.oauth2.OidcLoginConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = {
        "com.notshahrad.base.security.autoconfigure.BaseSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(OidcLoginConfiguration.class)
public class BaseOidcAutoConfiguration {
}
