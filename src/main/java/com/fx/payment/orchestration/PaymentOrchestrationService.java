package com.fx.payment.orchestration;

import com.fx.payment.entity.PaymentMessage;
import com.fx.payment.entity.PaymentStatus;
import com.fx.payment.exception.PaymentValidationException;
import com.fx.payment.model.domain.DomainPayment;
import com.fx.payment.model.pacs009.Pacs009Document;
import com.fx.payment.service.Pacs009ValidationService;
import com.fx.payment.service.PaymentPersistenceService;
import com.fx.payment.service.PaymentRoutingService;
import com.fx.payment.service.PaymentTransformationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the end-to-end processing pipeline for a single pacs.009 message:
 *
 * <pre>
 *  1. Validate XML against pacs.009.001.08 XSD
 *  2. Persist raw XML + key fields to DB (JDBC @Transactional)
 *  3. Transform to DomainPayment
 *  4. Route domain XML to fx.payment.valid queue
 *  5. Update DB status → PROCESSED
 * </pre>
 *
 * On validation failure:
 * <pre>
 *  1. Persist raw XML with status INVALID
 *  2. Route raw XML to fx.payment.invalid queue
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOrchestrationService {

    private final Pacs009ValidationService validationService;
    private final PaymentPersistenceService persistenceService;
    private final PaymentTransformationService transformationService;
    private final PaymentRoutingService routingService;

    /**
     * Main entry point – called by the JMS listener for every inbound message.
     *
     * @param rawXml the raw pacs.009 XML string received from the queue
     */
    public void process(String rawXml) {
        log.info("Processing inbound pacs.009 message (len={})", rawXml.length());

        try {
            // ── Step 1: Validate & unmarshal ─────────────────────────────
            Pacs009Document doc = validationService.validateAndUnmarshal(rawXml);

            // ── Step 2: Persist (JDBC transaction) ───────────────────────
            PaymentMessage saved = persistenceService.persistValidMessage(doc, rawXml);

            // ── Step 3: Transform ─────────────────────────────────────────
            DomainPayment domain = transformationService.toDomainPayment(doc, saved);
            String domainXml = transformationService.toXml(domain);

            // ── Step 4: Route to valid queue ──────────────────────────────
            routingService.routeValid(domainXml, saved.getId().toString());

            // ── Step 5: Update status ─────────────────────────────────────
            persistenceService.updateStatus(saved.getId(), PaymentStatus.PROCESSED);

            log.info("Payment processed successfully. paymentId={}", saved.getId());

        } catch (PaymentValidationException e) {
            log.warn("Payment validation failed: {}", e.getMessage());
            handleInvalid(rawXml, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing payment", e);
            handleInvalid(rawXml, "Processing error: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void handleInvalid(String rawXml, String errorMsg) {
        try {
            persistenceService.persistInvalidMessage(rawXml, errorMsg);
            routingService.routeInvalid(rawXml, errorMsg);
        } catch (Exception ex) {
            log.error("Failed to handle invalid message – potential message loss!", ex);
        }
    }
}
