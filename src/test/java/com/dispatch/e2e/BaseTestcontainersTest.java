package com.dispatch.e2e;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base test class establishing genuine PostgreSQL and RabbitMQ Testcontainers
 * for end-to-end integration verification.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseTestcontainersTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dispatch_e2e")
            .withUsername("dispatch")
            .withPassword("dispatch")
            .withStartupTimeout(java.time.Duration.ofMinutes(2));

    @Container
    protected static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }
}
