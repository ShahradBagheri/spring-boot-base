package com.notshahrad.base.auth.token.autoconfigure;

import com.notshahrad.spring_boot_base.auth.token.AccessTokenConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(AccessTokenConfiguration.class)
public class BaseTokenAutoConfiguration {
}
