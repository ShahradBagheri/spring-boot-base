package com.notshahrad.base.auth.autoconfigure;

import com.notshahrad.spring_boot_base.auth.AccountLinkApproval;
import com.notshahrad.spring_boot_base.auth.AppUser;
import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.auth.ExternalIdentityRepository;
import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@ConditionalOnProperty(prefix = "base.auth.local", name = "enabled", havingValue = "true")
@AutoConfigurationPackage(basePackageClasses = AppUser.class)
public class BaseAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OidcIdentityService.class)
    OidcIdentityService baseOidcIdentityService(AppUserRepository users, ExternalIdentityRepository identities,
                                              ObjectProvider<AccountLinkApproval> approvals) {
        return new OidcIdentityService(users, identities, approvals);
    }
}
