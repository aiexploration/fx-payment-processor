package com.fx.payment.service;

import com.fx.payment.entity.PaymentMessage;
import com.fx.payment.model.domain.DomainPayment;
import com.fx.payment.model.pacs009.BranchAndFinancialInstitutionIdentification;
import com.fx.payment.model.pacs009.CreditTransferTransaction;
import com.fx.payment.model.pacs009.Pacs009Document;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Transforms a validated pacs.009 document into a {@link DomainPayment} and
 * serialises it to XML for publishing on the valid queue.
 *
 * <p>The domain model is a canonical internal representation, decoupled from the
 * ISO 20022 pacs.009 schema version.  Downstream systems consume
 * {@code domain-payment.xsd} rather than the ISO schema.
 */
@Service
@Slf4j
public class PaymentTransformationService {

    private final JAXBContext domainJaxbContext;

    public PaymentTransformationService(
            @Qualifier("domainJaxbContext") JAXBContext domainJaxbContext) {
        this.domainJaxbContext = domainJaxbContext;
    }

    /**
     * Maps a validated pacs.009 document to a {@link DomainPayment}.
     *
     * @param doc     the unmarshalled pacs.009 document
     * @param saved   the persisted entity (supplies the generated UUID)
     * @return the populated domain payment object
     */
    public DomainPayment toDomainPayment(Pacs009Document doc, PaymentMessage saved) {
        CreditTransferTransaction tx = doc.getFiToFICstmrCdtTrf()
                .getCdtTrfTxInf().get(0);

        DomainPayment domain = new DomainPayment();

        // ── Identity ──────────────────────────────────────────────────────
        domain.setPaymentId(saved.getId().toString());
        domain.setOriginalMessageId(doc.getFiToFICstmrCdtTrf().getGrpHdr().getMsgId());
        domain.setTransactionId(tx.getPmtId().getTxId());
        domain.setEndToEndId(tx.getPmtId().getEndToEndId());
        domain.setUetr(tx.getPmtId().getUetr());

        // ── Settlement ────────────────────────────────────────────────────
        domain.setSettlementAmount(tx.getIntrBkSttlmAmt().getValue());
        domain.setSettlementCurrency(tx.getIntrBkSttlmAmt().getCcy());
        domain.setSettlementDate(tx.getIntrBkSttlmDt());
        domain.setSettlementMethod(doc.getFiToFICstmrCdtTrf()
                .getGrpHdr().getSttlmInf().getSttlmMtd());
        domain.setExchangeRate(tx.getXchgRate());

        // ── Debtor ────────────────────────────────────────────────────────
        if (tx.getDbtr() != null && tx.getDbtr().getFinInstnId() != null) {
            domain.setDebtorBic(tx.getDbtr().getFinInstnId().getBicfi());
            domain.setDebtorName(tx.getDbtr().getFinInstnId().getNm());
        }
        if (tx.getDbtrAcct() != null && tx.getDbtrAcct().getId() != null) {
            domain.setDebtorIban(tx.getDbtrAcct().getId().getIban());
        }
        domain.setDebtorAgentBic(safeBic(tx.getDbtrAgt()));

        // ── Creditor ──────────────────────────────────────────────────────
        if (tx.getCdtr() != null && tx.getCdtr().getFinInstnId() != null) {
            domain.setCreditorBic(tx.getCdtr().getFinInstnId().getBicfi());
            domain.setCreditorName(tx.getCdtr().getFinInstnId().getNm());
        }
        if (tx.getCdtrAcct() != null && tx.getCdtrAcct().getId() != null) {
            domain.setCreditorIban(tx.getCdtrAcct().getId().getIban());
        }
        domain.setCreditorAgentBic(safeBic(tx.getCdtrAgt()));

        // ── Charge / purpose ─────────────────────────────────────────────
        domain.setChargeBearer(tx.getChrgBr());
        if (tx.getPurp() != null) domain.setPurposeCode(tx.getPurp().getCd());
        if (tx.getRmtInf() != null) domain.setRemittanceInfo(tx.getRmtInf().getUstrd());

        // ── Processing metadata ───────────────────────────────────────────
        domain.setProcessingTimestamp(
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        domain.setPaymentStatus("PROCESSED");

        log.info("Transformed pacs.009 → DomainPayment. paymentId={} settlement={}{}",
                domain.getPaymentId(), domain.getSettlementAmount(), domain.getSettlementCurrency());
        return domain;
    }

    /**
     * Serialises a {@link DomainPayment} to an XML string for queue publishing.
     */
    public String toXml(DomainPayment domain) {
        try {
            Marshaller marshaller = domainJaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            marshaller.marshal(domain, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to marshal DomainPayment to XML", e);
        }
    }

    private String safeBic(BranchAndFinancialInstitutionIdentification b) {
        if (b == null || b.getFinInstnId() == null) return null;
        return b.getFinInstnId().getBicfi();
    }
}
