package com.fx.payment.service;

import com.fx.payment.config.RabbitConfig;
import com.fx.payment.model.domain.DomainPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Routes processed payment messages to the appropriate RabbitMQ queues.
 *
 * <ul>
 *   <li>{@code fx.payment.valid}   – valid domain payment XML</li>
 *   <li>{@code fx.payment.invalid} – rejected raw XML with error detail</li>
 * </ul>
 *
 * <p>The {@link RabbitTemplate} is configured with transaction support,
 * so sends participate in any active local transaction.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRoutingService {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentTransformationService transformationService;

    /**
     * Serialises the domain payment to XML and publishes it to the valid queue.
     *
     * @param domain the transformed domain payment object
     */
    public void routeValid(DomainPayment domain) {
        String xml = transformationService.toXml(domain);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY_VALID, xml);
        log.info("Routed VALID payment to '{}'. paymentId={}",
                RabbitConfig.VALID_QUEUE, domain.getPaymentId());
    }

    /**
     * Publishes an invalid / rejected message to the dead-letter queue,
     * including the validation error as a message property.
     *
     * @param rawXml         the original XML that failed validation
     * @param validationError human-readable error description
     */
    public void routeInvalid(String rawXml, String validationError) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY_INVALID, rawXml,
                message -> {
                    message.getMessageProperties().setHeader("ValidationError", truncate(validationError, 500));
                    message.getMessageProperties().setHeader("MessageType", "pacs.009.001.08");
                    return message;
                });
        log.warn("Routed INVALID message to '{}'. reason={}",
                RabbitConfig.INVALID_QUEUE, truncate(validationError, 200));
    }

    private String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max) + "…" : s;
    }
}
