package com.fx.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * RabbitMQ configuration.
 *
 * <p>By default, connects to the Docker-based RabbitMQ broker at
 * localhost:5672.  The broker is started via docker-compose.
 *
 * <p>Queues are declared with durable settings for persistence.
 * JPA transactions are managed by Spring Boot's auto-configured {@code JpaTransactionManager}.
 */
@Configuration
@EnableTransactionManagement
public class RabbitConfig {

    // ── Exchange name ──────────────────────────────────────────────────────
    public static final String EXCHANGE_NAME = "fx.payment.exchange";

    // ── Queue names ─────────────────────────────────────────────────────────
    public static final String INBOUND_QUEUE   = "fx.pacs009.inbound";
    public static final String VALID_QUEUE     = "fx.payment.valid";
    public static final String INVALID_QUEUE   = "fx.payment.invalid";

    // ── Routing keys ───────────────────────────────────────────────────────
    public static final String ROUTING_KEY_INBOUND   = "pacs009.inbound";
    public static final String ROUTING_KEY_VALID     = "payment.valid";
    public static final String ROUTING_KEY_INVALID   = "payment.invalid";

    // ── Exchange ───────────────────────────────────────────────────────────
    @Bean
    public TopicExchange fxPaymentExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // ── Queues ─────────────────────────────────────────────────────────────
    @Bean
    public Queue inboundQueue() {
        return QueueBuilder.durable(INBOUND_QUEUE).build();
    }

    @Bean
    public Queue validQueue() {
        return QueueBuilder.durable(VALID_QUEUE).build();
    }

    @Bean
    public Queue invalidQueue() {
        return QueueBuilder.durable(INVALID_QUEUE).build();
    }

    // ── Bindings ───────────────────────────────────────────────────────────
    @Bean
    public Binding inboundBinding(Queue inboundQueue, TopicExchange fxPaymentExchange) {
        return BindingBuilder.bind(inboundQueue).to(fxPaymentExchange).with(ROUTING_KEY_INBOUND);
    }

    @Bean
    public Binding validBinding(Queue validQueue, TopicExchange fxPaymentExchange) {
        return BindingBuilder.bind(validQueue).to(fxPaymentExchange).with(ROUTING_KEY_VALID);
    }

    @Bean
    public Binding invalidBinding(Queue invalidQueue, TopicExchange fxPaymentExchange) {
        return BindingBuilder.bind(invalidQueue).to(fxPaymentExchange).with(ROUTING_KEY_INVALID);
    }

    // ── Message converter ──────────────────────────────────────────────────
    // SimpleMessageConverter handles String/byte[] natively; the listener receives raw XML.
    @Bean
    public MessageConverter messageConverter() {
        return new SimpleMessageConverter();
    }

    // ── RabbitTemplate for sending messages ───────────────────────────────
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setExchange(EXCHANGE_NAME);
        return template;
    }

}