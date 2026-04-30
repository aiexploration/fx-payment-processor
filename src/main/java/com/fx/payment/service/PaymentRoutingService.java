package com.fx.payment.service;

import com.fx.payment.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Routes processed payment messages to the appropriate RabbitMQ queues.
 *
 * <ul>
 *   <li>{@code fx.payment.valid}   – converted domain payment XML</li>
 *   <li>{@code fx.payment.invalid} – original raw pacs.009 XML with error detail</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRoutingService {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publishes the converted domain payment XML to the valid queue.
     *
     * @param domainXml serialised {@code DomainPayment} XML string
     * @param paymentId internal UUID for logging correlation
     */
    public void routeValid(String domainXml, String paymentId) {
        rabbitTemplate.send(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY_VALID,
                MessageBuilder.withBody(domainXml.getBytes())
                        .setContentType(MessageProperties.CONTENT_TYPE_XML)
                        .build());
        log.info("Routed VALID payment to '{}'. paymentId={}",
                RabbitConfig.VALID_QUEUE, paymentId);
    }

    /**
     * Publishes the original raw pacs.009 XML to the invalid queue.
     *
     * @param rawXml          the original XML that failed validation
     * @param validationError human-readable error description
     */
    public void routeInvalid(String rawXml, String validationError) {
        rabbitTemplate.send(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY_INVALID,
                MessageBuilder.withBody(rawXml.getBytes())
                        .setContentType(MessageProperties.CONTENT_TYPE_XML)
                        .setHeader("ValidationError", truncate(validationError, 500))
                        .setHeader("MessageType", "pacs.009.001.08")
                        .build());
        log.warn("Routed INVALID message to '{}'. reason={}",
                RabbitConfig.INVALID_QUEUE, truncate(validationError, 200));
    }

    private String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : s;
    }
}
