package com.fx.payment.integration;

import com.fx.payment.config.RabbitConfig;
import com.fx.payment.entity.PaymentMessage;
import com.fx.payment.entity.PaymentStatus;
import com.fx.payment.repository.PaymentMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * End-to-end integration tests. Requires the Docker Compose stack to be running:
 *   docker-compose up -d
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentProcessingIntegrationTest {

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private PaymentMessageRepository repository;

    @BeforeEach
    void resetTestState() {
        drainQueue(RabbitConfig.INBOUND_QUEUE);
        drainQueue(RabbitConfig.VALID_QUEUE);
        drainQueue(RabbitConfig.INVALID_QUEUE);
        repository.deleteAll();
    }

    @Test
    @DisplayName("Valid pacs.009 (USD/GBP) should be stored with PROCESSED status")
    void validUsdGbpMessageShouldBeStoredAsProcessed() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var record = repository.findByTransactionId("TXN-20240415-001");
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(record.get().getSettlementCurrency()).isEqualTo("USD");
            assertThat(record.get().getDebtorBic()).isEqualTo("BARCGB22");
            assertThat(record.get().getId()).isNotNull();
        });
    }

    @Test
    @DisplayName("Valid pacs.009 (EUR/JPY) should be stored with PROCESSED status")
    void validEurJpyMessageShouldBeStoredAsProcessed() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009-eurjpy.xml");

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var record = repository.findByTransactionId("TXN-20240415-002");
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(record.get().getSettlementCurrency()).isEqualTo("JPY");
        });
    }

    @Test
    @DisplayName("Valid message should produce a domain payment XML on the valid queue")
    void validMessageShouldProduceDomainPaymentOnValidQueue() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009-eurjpy.xml");

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var record = repository.findByTransactionId("TXN-20240415-002");
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
        });

        var msg = rabbitTemplate.receive(RabbitConfig.VALID_QUEUE, 2000);
        String domainXml = msg == null ? null : new String(msg.getBody(), StandardCharsets.UTF_8);
        assertThat(domainXml).isNotNull().contains("DomainPayment").contains("TXN-20240415-002").contains("JPY");
    }

    @Test
    @DisplayName("Manual test: put pacs.009 message and print output domain XML")
    void manualValidPacsMessageShouldPrintOutputDomainXml() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var record = repository.findByTransactionId("TXN-20240415-001");
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
        });

        var msg = rabbitTemplate.receive(RabbitConfig.VALID_QUEUE, 2000);
        String domainXml = msg == null ? null : new String(msg.getBody(), StandardCharsets.UTF_8);
        assertThat(domainXml).contains("DomainPayment").contains("TXN-20240415-001");

        System.out.println();
        System.out.println("===== fx.payment.valid output begin =====");
        System.out.println(domainXml);
        System.out.println("===== fx.payment.valid output end =====");
        System.out.println();
    }

    @Test
    @DisplayName("Invalid pacs.009 (missing TxId) should be stored with INVALID status")
    void invalidMissingTxIdShouldBeStoredAsInvalid() throws Exception {
        String rawXml = loadXml("messages/invalid-pacs009-missing-txid.xml");
        long countBefore = repository.findByStatus(PaymentStatus.INVALID).size();

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<PaymentMessage> invalids = repository.findByStatus(PaymentStatus.INVALID);
            assertThat(invalids.size()).isGreaterThan((int) countBefore);
            assertThat(invalids.get(invalids.size() - 1).getValidationErrors()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Invalid pacs.009 (bad currency code) should be stored with INVALID status")
    void invalidBadCurrencyCodeShouldBeStoredAsInvalid() throws Exception {
        String rawXml = loadXml("messages/invalid-pacs009-bad-currency.xml");
        long countBefore = repository.findByStatus(PaymentStatus.INVALID).size();

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(repository.findByStatus(PaymentStatus.INVALID).size()).isGreaterThan((int) countBefore)
        );
    }

    @Test
    @DisplayName("UUID in DB should match UUID embedded in domain XML on valid queue")
    void uuidShouldBeConsistentBetweenDbAndDomainXml() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");

        sendXml(rawXml);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var record = repository.findByTransactionId("TXN-20240415-001");
            assertThat(record).isPresent();
            assertThat(record.get().getStatus()).isEqualTo(PaymentStatus.PROCESSED);
        });

        String persistedUuid = repository.findByTransactionId("TXN-20240415-001")
                .orElseThrow().getId().toString();

        var msg = rabbitTemplate.receive(RabbitConfig.VALID_QUEUE, 2000);
        String domainXml = msg == null ? null : new String(msg.getBody(), StandardCharsets.UTF_8);
        assertThat(domainXml).contains(persistedUuid);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void sendXml(String rawXml) {
        rabbitTemplate.send(RabbitConfig.EXCHANGE_NAME, RabbitConfig.ROUTING_KEY_INBOUND,
                MessageBuilder.withBody(rawXml.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_XML)
                        .build());
    }

    private String loadXml(String path) throws Exception {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("Resource not found: " + path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void drainQueue(String queueName) {
        while (rabbitTemplate.receive(queueName, 100) != null) {
            // drain messages left by a previous test
        }
    }
}
