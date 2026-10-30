package com.peter.nbagamemaster.rag;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RagDocumentIngestionJpaConfigurationTest {

    @Test
    void simpleStoreDoesNotRegisterPersistentRepositories() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class))
                .withUserConfiguration(RegistryConfiguration.class)
                .withPropertyValues("app.vector-store.type=simple", "spring.jpa.hibernate.ddl-auto=create-drop")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeanNamesForType(RagDocumentIngestionJpaRepository.class).length);
                    assertEquals(0, context.getBeanNamesForType(RagDocumentIngestionRepository.class).length);
                });
    }

    @Test
    void simpleStoreCanStartWithDataSourceAutoConfigurationExcluded() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HibernateJpaAutoConfiguration.class,
                        JpaRepositoriesAutoConfiguration.class))
                .withUserConfiguration(RegistryConfiguration.class)
                .withPropertyValues("app.vector-store.type=simple")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.getBeanProvider(EntityManagerFactory.class).iterator().hasNext());
                    assertEquals(0, context.getBeanNamesForType(RagDocumentIngestionJpaRepository.class).length);
                    assertEquals(0, context.getBeanNamesForType(RagDocumentIngestionRepository.class).length);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = RagDocumentIngestionEntity.class)
    @Import(RagDocumentIngestionRepository.class)
    static class RegistryConfiguration {
    }
}
