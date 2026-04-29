package com.fx.payment.config;

import jakarta.jms.ConnectionFactory;
import jakarta.persistence.EntityManagerFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JMS (ActiveMQ Artemis) configuration.
 *
 * <p>In the default (embedded) profile the broker runs in-process; no external
 * server is required.  Switch to the {@code postgres} profile to connect to the
 * standalone broker started by docker-compose.
 *
 * <p>A {@link JmsTransactionManager} is registered so that {@code @Transactional}
 * methods that publish to JMS participate in JMS-local transactions.  Note: for
 * full XA atomicity across JMS + JDBC in production, use an XA-capable broker
 * and a JTA provider (e.g. Atomikos) or adopt the Outbox pattern.
 */
@Configuration
@EnableJms
@EnableTransactionManagement
public class JmsConfig {

    // ── Queue names ─────────────────────────────────────────────────────────
    public static final String INBOUND_QUEUE   = "fx.pacs009.inbound";
    public static final String VALID_QUEUE     = "fx.payment.valid";
    public static final String INVALID_QUEUE   = "fx.payment.invalid";

    /**
     * JmsTemplate used for sending messages.  The {@code sessionTransacted} flag
     * means sends participate in JMS-local transactions when one is active.
     */
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setSessionTransacted(true);
        template.setDeliveryPersistent(true);
        return template;
    }

    /**
     * Listener container factory.  Uses {@code CLIENT_ACKNOWLEDGE} and JMS
     * transactions so that a message is only removed from the queue after the
     * listener method completes successfully.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("jmsTransactionManager") PlatformTransactionManager transactionManager) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setTransactionManager(transactionManager);
        factory.setSessionTransacted(true);
        factory.setConcurrency("1-5");
        factory.setErrorHandler(t ->
                System.err.println("[JMS ErrorHandler] Uncaught exception in listener: " + t.getMessage()));
        return factory;
    }

    /**
     * JMS-local transaction manager.  Keeps JMS sends within the same local
     * transaction as the listener acknowledgement.
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public PlatformTransactionManager jmsTransactionManager(ConnectionFactory connectionFactory) {
        return new JmsTransactionManager(connectionFactory);
    }
}
