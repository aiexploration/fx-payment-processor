package com.fx.payment.service;

import com.fx.payment.entity.PaymentMessage;
import com.fx.payment.entity.PaymentStatus;
import com.fx.payment.exception.PaymentValidationException;
import com.fx.payment.model.domain.DomainPayment;
import com.fx.payment.model.pacs009.Pacs009Document;
import com.fx.payment.orchestration.PaymentOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link PaymentOrchestrationService} using Mockito mocks.
 * No Spring context, no database, no JMS broker – pure unit test.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOrchestrationServiceTest {

    @Mock private Pacs009ValidationService validationService;
    @Mock private PaymentPersistenceService persistenceService;
    @Mock private PaymentTransformationService transformationService;
    @Mock private PaymentRoutingService routingService;

    @InjectMocks
    private PaymentOrchestrationService orchestrationService;

    private static final String RAW_XML = "<Document xmlns='urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08'/>";
    private static final UUID PAYMENT_UUID = UUID.randomUUID();

    private Pacs009Document mockDoc;
    private PaymentMessage savedEntity;
    private DomainPayment domainPayment;

    @BeforeEach
    void setUp() {
        mockDoc     = mock(Pacs009Document.class);
        domainPayment = new DomainPayment();
        domainPayment.setPaymentId(PAYMENT_UUID.toString());

        savedEntity = PaymentMessage.builder()
                .id(PAYMENT_UUID)
                .status(PaymentStatus.VALIDATED)
                .build();
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should call all pipeline steps in order for a valid message")
    void shouldExecuteFullPipelineForValidMessage() {
        given(validationService.validateAndUnmarshal(RAW_XML)).willReturn(mockDoc);
        given(persistenceService.persistValidMessage(mockDoc, RAW_XML)).willReturn(savedEntity);
        given(transformationService.toDomainPayment(mockDoc, savedEntity)).willReturn(domainPayment);
        given(transformationService.toXml(domainPayment)).willReturn("<DomainPayment/>");

        orchestrationService.process(RAW_XML);

        then(validationService).should(times(1)).validateAndUnmarshal(RAW_XML);
        then(persistenceService).should(times(1)).persistValidMessage(mockDoc, RAW_XML);
        then(transformationService).should(times(1)).toDomainPayment(mockDoc, savedEntity);
        then(transformationService).should(times(1)).toXml(domainPayment);
        then(routingService).should(times(1)).routeValid(eq("<DomainPayment/>"), eq(PAYMENT_UUID.toString()));
        then(persistenceService).should(times(1)).updateStatus(PAYMENT_UUID, PaymentStatus.PROCESSED);
    }

    @Test
    @DisplayName("Should not call invalid routing for a valid message")
    void shouldNotRouteInvalidForValidMessage() {
        given(validationService.validateAndUnmarshal(RAW_XML)).willReturn(mockDoc);
        given(persistenceService.persistValidMessage(mockDoc, RAW_XML)).willReturn(savedEntity);
        given(transformationService.toDomainPayment(mockDoc, savedEntity)).willReturn(domainPayment);
        given(transformationService.toXml(domainPayment)).willReturn("<DomainPayment/>");

        orchestrationService.process(RAW_XML);

        then(routingService).should(never()).routeInvalid(any(), any());
    }

    // ── Validation failure ────────────────────────────────────────────────

    @Test
    @DisplayName("Should route to invalid queue when XSD validation fails")
    void shouldRouteInvalidOnValidationFailure() {
        String errorMsg = "XSD validation failed: missing TxId";
        given(validationService.validateAndUnmarshal(RAW_XML))
                .willThrow(new PaymentValidationException(errorMsg, RAW_XML));

        orchestrationService.process(RAW_XML);

        then(persistenceService).should(times(1)).persistInvalidMessage(eq(RAW_XML), contains("missing TxId"));
        then(routingService).should(times(1)).routeInvalid(eq(RAW_XML), contains("missing TxId"));
    }

    @Test
    @DisplayName("Should not persist valid or route valid when validation fails")
    void shouldNotPersistValidOnValidationFailure() {
        given(validationService.validateAndUnmarshal(RAW_XML))
                .willThrow(new PaymentValidationException("error", RAW_XML));

        orchestrationService.process(RAW_XML);

        then(persistenceService).should(never()).persistValidMessage(any(), any());
        then(routingService).should(never()).routeValid(any(), any());
    }

    // ── Unexpected error ──────────────────────────────────────────────────

    @Test
    @DisplayName("Should route to invalid queue on unexpected exception")
    void shouldHandleUnexpectedException() {
        given(validationService.validateAndUnmarshal(RAW_XML))
                .willThrow(new RuntimeException("Unexpected DB failure"));

        orchestrationService.process(RAW_XML);

        then(persistenceService).should(times(1)).persistInvalidMessage(eq(RAW_XML), anyString());
        then(routingService).should(times(1)).routeInvalid(eq(RAW_XML), anyString());
    }

    @Test
    @DisplayName("Should not propagate exceptions to the caller (listener)")
    void shouldNotPropagateExceptions() {
        given(validationService.validateAndUnmarshal(any()))
                .willThrow(new RuntimeException("Critical failure"));

        // Should not throw
        orchestrationService.process(RAW_XML);
    }
}
