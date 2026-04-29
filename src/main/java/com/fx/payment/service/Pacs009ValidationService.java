package com.fx.payment.service;

import com.fx.payment.exception.PaymentValidationException;
import com.fx.payment.model.pacs009.Pacs009Document;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a pacs.009 XML string against its XSD schema and, if valid,
 * unmarshals it into a {@link Pacs009Document}.
 *
 * <p>The {@link Schema} is loaded once at application start (via
 * {@link #initSchema()}) and reused; only the {@link Validator} (not
 * thread-safe) is created per call.
 */
@Service
@Slf4j
public class Pacs009ValidationService {

    private static final String XSD_PATH = "xsd/pacs.009.001.08.xsd";

    private final JAXBContext jaxbContext;

    private Schema schema;

    public Pacs009ValidationService(
            @Qualifier("pacs009JaxbContext") JAXBContext jaxbContext) {
        this.jaxbContext = jaxbContext;
    }

    /**
     * Validates {@code rawXml} against the pacs.009.001.08 XSD.
     *
     * @param rawXml raw XML string received from the queue
     * @return unmarshalled {@link Pacs009Document}
     * @throws PaymentValidationException if validation fails
     */
    public Pacs009Document validateAndUnmarshal(String rawXml) {
        log.debug("Starting XSD validation of pacs.009 message");

        // ── 1. XSD validation ─────────────────────────────────────────────
        List<String> errors = new ArrayList<>();
        try {
            Validator validator = getSchema().newValidator();
            validator.setErrorHandler(new CollectingErrorHandler(errors));
            validator.validate(new StreamSource(new StringReader(rawXml)));
        } catch (SAXException | IOException e) {
            errors.add(e.getMessage());
        }

        if (!errors.isEmpty()) {
            String errorMsg = "XSD validation failed: " + String.join("; ", errors);
            log.warn("Pacs.009 message failed validation. Errors: {}", errors);
            throw new PaymentValidationException(errorMsg, rawXml);
        }

        // ── 2. JAXB unmarshal ─────────────────────────────────────────────
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Pacs009Document doc = (Pacs009Document) unmarshaller
                    .unmarshal(new StringReader(rawXml));
            log.debug("Successfully unmarshalled pacs.009 message: {}",
                    doc.getFiToFICstmrCdtTrf().getGrpHdr().getMsgId());
            return doc;
        } catch (JAXBException e) {
            throw new PaymentValidationException(
                    "Failed to unmarshal valid XML – this should not happen: " + e.getMessage(),
                    rawXml, e);
        }
    }

    private Schema getSchema() {
        if (schema == null) {
            schema = initSchema();
        }
        return schema;
    }

    private Schema initSchema() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory.newSchema(new ClassPathResource(XSD_PATH).getURL());
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Cannot load pacs.009 XSD schema from " + XSD_PATH, e);
        }
    }

    // ── Inner helper ──────────────────────────────────────────────────────
    private static class CollectingErrorHandler implements org.xml.sax.ErrorHandler {
        private final List<String> errors;
        CollectingErrorHandler(List<String> errors) { this.errors = errors; }

        @Override public void warning(org.xml.sax.SAXParseException e) {
            errors.add("WARN: " + e.getMessage());
        }
        @Override public void error(org.xml.sax.SAXParseException e) {
            errors.add("ERROR line " + e.getLineNumber() + ": " + e.getMessage());
        }
        @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
            errors.add("FATAL: " + e.getMessage());
            throw e;
        }
    }
}
