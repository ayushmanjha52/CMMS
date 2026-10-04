package com.plantdesk.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableJpaRepositories(basePackages = "com.plantdesk", repositoryBaseClass = TenantAwareJpaRepository.class)
public class JpaConfig {

    // Replaces Spring Boot's default JpaTransactionManager (it backs off when one exists).
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareJpaTransactionManager(emf);
    }

    // For flows that must choose the tenant first and open the transaction second
    // (login, refresh, PM generation per tenant).
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
