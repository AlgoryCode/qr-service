package com.ael.algoryqrservice.fulfillmentservice.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.ael.algoryqrservice.qrservice.config.JpaEmfPropertiesFactory;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository",
        entityManagerFactoryRef = "fulfillmentEntityManagerFactory",
        transactionManagerRef = "fulfillmentTransactionManager"
)
public class FulfillmentDatabaseConfiguration {

    @Bean(name = "fulfillmentEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean fulfillmentEntityManagerFactory(
            DataSource dataSource,
            JpaEmfPropertiesFactory jpaEmfPropertiesFactory
    ) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceProviderClass(HibernatePersistenceProvider.class);
        factory.setPackagesToScan("com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity");
        factory.setPersistenceUnitName("fulfillment");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(jpaEmfPropertiesFactory.build());
        return factory;
    }

    @Bean(name = "fulfillmentTransactionManager")
    public PlatformTransactionManager fulfillmentTransactionManager(
            @Qualifier("fulfillmentEntityManagerFactory") EntityManagerFactory fulfillmentEntityManagerFactory
    ) {
        return new JpaTransactionManager(fulfillmentEntityManagerFactory);
    }
}
