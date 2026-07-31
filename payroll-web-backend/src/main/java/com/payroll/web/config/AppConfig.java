package com.payroll.web.config;

import com.payroll.core.security.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }
}
