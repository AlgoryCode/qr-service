package com.ael.algoryqrservice.qrservice.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.ael.algoryqrservice.qrservice",
                "com.ael.algoryqrservice.merchantservice.infrastructure.persistence.repository",
                "com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository",
                "com.ael.algoryqrservice.customerservice.infrastructure.persistence.repository",
                "com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository",
                "com.ael.algoryqrservice.authservice.infrastructure.persistence.repository",
                "com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository",
                "com.ael.algoryqrservice.paymentservice.infrastructure.persistence.repository"
        },
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class QrDatabaseConfiguration {

    @Primary
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            JpaEmfPropertiesFactory jpaEmfPropertiesFactory
    ) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        factory.setPackagesToScan(
                "com.ael.algoryqrservice.qrservice.model",
                "com.ael.algoryqrservice.qrservice.product.model",
                "com.ael.algoryqrservice.qrservice.qr.model",
                "com.ael.algoryqrservice.qrservice.store.model",
                "com.ael.algoryqrservice.qrservice.print.model",
                "com.ael.algoryqrservice.qrservice.integration.ubereats.model",
                "com.ael.algoryqrservice.qrservice.integration.ubereatsmenu.model",
                "com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model",
                "com.ael.algoryqrservice.merchantservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.customerservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.authservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity",
                "com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity"
        );
        factory.setPersistenceUnitName("default");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(jpaEmfPropertiesFactory.build());
        return factory;
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
