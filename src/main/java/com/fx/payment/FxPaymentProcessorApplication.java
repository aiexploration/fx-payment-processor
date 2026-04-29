package com.fx.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FX Payment Processor
 *
 * Ingests ISO 20022 pacs.009 (Financial Institution Credit Transfer) messages
 * from a RabbitMQ queue, validates them against XSD, persists to DB with a
 * generated UUID, transforms to a domain Payment object, and routes to the
 * appropriate outbound queue.
 *
 * Queue topology:
 *   fx.pacs009.inbound   ← incoming raw pacs.009 XML
 *   fx.payment.valid     → valid domain-payment XML (downstream FX processing)
 *   fx.payment.invalid   → rejected messages with error detail
 */
@SpringBootApplication
public class FxPaymentProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FxPaymentProcessorApplication.class, args);
    }
}
