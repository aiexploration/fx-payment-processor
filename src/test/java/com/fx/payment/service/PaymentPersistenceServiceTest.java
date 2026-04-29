package com.fx.payment.service;

import com.fx.payment.config.JaxbConfig;
import com.fx.payment.entity.PaymentMessage;
import com.fx.payment.entity.PaymentStatus;
import com.fx.payment.model.pacs009.Pacs009Document;
import com.fx.payment.repository.PaymentMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests {@link PaymentPersistenceService} using PostgreSQL.
 *
 * {@code @DataJpaTest} starts only the JPA slice (no JMS, no full context).
 */
@DataJpaTest
@Import({PaymentPersistenceService.class, Pacs009ValidationService.class, JaxbConfig.class})
@ActiveProfiles("test")
class PaymentPersistenceServiceTest {

    @Autowired private PaymentPersistenceService persistenceService;
    @Autowired private PaymentMessageRepository repository;
    @Autowired private Pacs009ValidationService validationService;

    @Test
    @DisplayName("Should persist a valid message and generate a UUID")
    void shouldPersistValidMessageWithUuid() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");
        Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);

        PaymentMessage saved = persistenceService.persistValidMessage(doc, rawXml);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("Should persist with status VALIDATED for valid message")
    void shouldPersistWithValidatedStatus() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");
        Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);

        PaymentMessage saved = persistenceService.persistValidMessage(doc, rawXml);

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
    }

    @Test
    @DisplayName("Should persist all key fields from pacs.009")
    void shouldPersistKeyFields() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");
        Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);

        PaymentMessage saved = persistenceService.persistValidMessage(doc, rawXml);

        assertThat(saved.getMessageId()).isEqualTo("FX-MSG-20240415-001");
        assertThat(saved.getTransactionId()).isEqualTo("TXN-20240415-001");
        assertThat(saved.getEndToEndId()).isEqualTo("E2E-20240415-001");
        assertThat(saved.getUetr()).isEqualTo("a1b2c3d4-e5f6-4789-ab01-cd2345ef6789");
        assertThat(saved.getSettlementCurrency()).isEqualTo("USD");
        assertThat(saved.getDebtorBic()).isEqualTo("BARCGB22");
        assertThat(saved.getCreditorBic()).isEqualTo("JPMSGB2L");
    }

    @Test
    @DisplayName("Should store raw XML verbatim")
    void shouldStoreRawXml() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");
        Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);

        PaymentMessage saved = persistenceService.persistValidMessage(doc, rawXml);

        Optional<PaymentMessage> reloaded = repository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getRawXml()).isEqualTo(rawXml);
    }

    @Test
    @DisplayName("Should persist invalid message with INVALID status and error message")
    void shouldPersistInvalidMessage() {
        String rawXml = "<bad/>";
        String error = "XSD validation failed: missing TxId";

        PaymentMessage saved = persistenceService.persistInvalidMessage(rawXml, error);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.INVALID);
        assertThat(saved.getValidationErrors()).contains("missing TxId");
    }

    @Test
    @DisplayName("Should be findable by message ID after persistence")
    void shouldBeFindableByMessageId() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");
        Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);

        persistenceService.persistValidMessage(doc, rawXml);

        Optional<PaymentMessage> found = repository.findByMessageId("FX-MSG-20240415-001");
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("Should update status from VALIDATED to PROCESSED")
    void shouldUpdateStatus() throws Exception {
        String rawXml = loadXml("messages/valid-pacs009.xml");
        Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);
        PaymentMessage saved = persistenceService.persistValidMessage(doc, rawXml);

        persistenceService.updateStatus(saved.getId(), PaymentStatus.PROCESSED);

        PaymentMessage updated = repository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
    }

    @Test
    @DisplayName("Should truncate validation errors exceeding 2000 chars")
    void shouldTruncateLongValidationErrors() {
        String longError = "E".repeat(3000);

        PaymentMessage saved = persistenceService.persistInvalidMessage("<x/>", longError);

        assertThat(saved.getValidationErrors()).hasSizeLessThanOrEqualTo(2000);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private String loadXml(String path) throws Exception {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
