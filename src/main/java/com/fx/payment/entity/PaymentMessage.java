package com.fx.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted record for every pacs.009 message received by the processor.
 *
 * <p>A UUID primary key is generated at persistence time; this UUID is also
 * used as the {@code paymentId} in the outbound domain payment XML.
 *
 * <p>For valid messages the key pacs.009 fields are de-normalised into
 * individual columns to support query / reconciliation; the full raw XML is
 * always stored for auditability.
 */
@Entity
@Table(name = "payment_message",
        indexes = {
                @Index(name = "idx_msg_id",  columnList = "message_id"),
                @Index(name = "idx_tx_id",   columnList = "transaction_id"),
                @Index(name = "idx_uetr",    columnList = "uetr"),
                @Index(name = "idx_status",  columnList = "status")
        })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "rawXml")
public class PaymentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ── pacs.009 identifiers ──────────────────────────────────────────────
    @Column(name = "message_id", length = 35)
    private String messageId;

    @Column(name = "transaction_id", length = 35)
    private String transactionId;

    @Column(name = "end_to_end_id", length = 35)
    private String endToEndId;

    @Column(name = "uetr", length = 36)
    private String uetr;

    // ── settlement ────────────────────────────────────────────────────────
    @Column(name = "settlement_amount", precision = 18, scale = 5)
    private BigDecimal settlementAmount;

    @Column(name = "settlement_currency", length = 3)
    private String settlementCurrency;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "exchange_rate", precision = 18, scale = 10)
    private BigDecimal exchangeRate;

    // ── parties ───────────────────────────────────────────────────────────
    @Column(name = "debtor_bic", length = 11)
    private String debtorBic;

    @Column(name = "creditor_bic", length = 11)
    private String creditorBic;

    // ── lifecycle ─────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "validation_errors", length = 2000)
    private String validationErrors;

    // ── raw payload (always stored for audit) ─────────────────────────────
    @Column(name = "raw_xml", nullable = false, columnDefinition = "TEXT")
    private String rawXml;

    // ── audit timestamps ─────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
