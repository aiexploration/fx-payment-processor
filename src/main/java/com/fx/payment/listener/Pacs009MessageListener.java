package com.fx.payment.listener;

import com.fx.payment.config.RabbitConfig;
import com.fx.payment.orchestration.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ listener that consumes raw pacs.009 XML messages from
 * {@value RabbitConfig#INBOUND_QUEUE} and delegates to the orchestration service.
 *
 * <p>The listener container is configured with transaction support
 * (see {@link com.fx.payment.config.RabbitConfig}), so the message is
 * acknowledged only after this method returns successfully.  Any uncaught
 * exception causes a redelivery up to the broker's configured retry limit,
 * after which the message is dead-lettered.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Pacs009MessageListener {

    private final PaymentOrchestrationService orchestrationService;

    @RabbitListener(queues = RabbitConfig.INBOUND_QUEUE)
    public void onMessage(String rawXml) {
        log.info("Received message on '{}' (len={})", RabbitConfig.INBOUND_QUEUE, rawXml.length());
        orchestrationService.process(rawXml);
    }
}
