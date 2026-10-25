package com.ledgerguard.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for tests that need both MongoDB and a real Kafka broker. Both containers are
 * started once as JVM-wide singletons and left running; Testcontainers' Ryuk sidecar reaps them
 * when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class NotificationIntegrationTestBase {

    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    static {
        MONGO.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> MONGO.getConnectionString() + "/ledgerguard_notifications");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
