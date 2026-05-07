package com.fx.payment.service;

import com.fx.payment.model.domain.DomainPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes routed payment XML to the local file system after successful queue delivery.
 *
 * <p>Valid messages  → {@code <base-dir>/valid/yyyyMMdd_HHmmssSSS_<id>.xml}
 * <p>Invalid messages → {@code <base-dir>/invalid/yyyyMMdd_HHmmssSSS_<id>.xml}
 *
 * <p>File storage failures are logged but never rethrown — the queue send is
 * the authoritative action; disk output is supplementary.
 */
@Service
@Slf4j
public class PaymentFileStorageService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

    private static final Pattern E2E_ID_PATTERN =
            Pattern.compile("<(?:[^:>]+:)?EndToEndId>([^<]+)</(?:[^:>]+:)?EndToEndId>");
    private static final Pattern UETR_PATTERN =
            Pattern.compile("<(?:[^:>]+:)?UETR>([^<]+)</(?:[^:>]+:)?UETR>");

    private final Path validDir;
    private final Path invalidDir;

    public PaymentFileStorageService(
            @Value("${fx.payment.output.base-dir:target/output}") String baseDir) {
        this.validDir   = Paths.get(baseDir, "valid");
        this.invalidDir = Paths.get(baseDir, "invalid");
    }

    /**
     * Persists the domain payment XML for a successfully validated and routed message.
     * Identifier preference: endToEndId → uetr → paymentId.
     */
    public void saveValid(String xml, DomainPayment domain) {
        String id = firstNonBlank(domain.getEndToEndId(), domain.getUetr(), domain.getPaymentId());
        write(validDir, buildFilename(id), xml);
    }

    /**
     * Persists the raw pacs.009 XML for a rejected message.
     * Identifier is extracted from the XML text; falls back to a random UUID when absent.
     */
    public void saveInvalid(String rawXml) {
        String id = firstNonBlank(extract(rawXml, E2E_ID_PATTERN),
                                  extract(rawXml, UETR_PATTERN),
                                  UUID.randomUUID().toString());
        write(invalidDir, buildFilename(id), rawXml);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void write(Path dir, String filename, String content) {
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.debug("Wrote payment file: {}", target.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write payment file to {}/{}: {}", dir, filename, e.getMessage());
        }
    }

    private String buildFilename(String id) {
        String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
        return ts + "_" + sanitize(id) + ".xml";
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String extract(String xml, Pattern pattern) {
        if (xml == null) return null;
        Matcher m = pattern.matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return UUID.randomUUID().toString();
    }
}
