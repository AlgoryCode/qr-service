package com.ael.algoryqrservice.qrservice.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies Spring Boot {@code spring.jpa.*} settings to manually declared entity manager factories.
 */
@Component
public class JpaEmfPropertiesFactory {

    private final JpaProperties jpaProperties;
    private final HibernateProperties hibernateProperties;

    public JpaEmfPropertiesFactory(JpaProperties jpaProperties, HibernateProperties hibernateProperties) {
        this.jpaProperties = jpaProperties;
        this.hibernateProperties = hibernateProperties;
    }

    public Map<String, Object> build() {
        Map<String, Object> properties = new HashMap<>(jpaProperties.getProperties());
        properties.putAll(hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(),
                new HibernateSettings()
        ));
        properties.putIfAbsent(
                "hibernate.physical_naming_strategy",
                CamelCaseToUnderscoresNamingStrategy.class.getName()
        );
        return properties;
    }
}
