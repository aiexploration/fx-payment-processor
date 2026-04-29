package com.fx.payment.util;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import jakarta.jms.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standalone utility to send a pacs.009 XML file to the inbound JMS queue.
 *
 * Usage (while the app is running with embedded broker):
 * <pre>
 *   mvn exec:java \
 *     -Dexec.mainClass="com.fx.payment.util.TestMessageSender" \
 *     -Dexec.args="src/test/resources/messages/valid-pacs009.xml"
 * </pre>
 *
 * In embedded mode SwiftPay exposes the embedded broker at tcp://localhost:61616
 * while the JVM is running.
 */
public class TestMessageSender {

    private static final String BROKER_URL   = System.getProperty("brokerUrl",  "tcp://localhost:61616");
    private static final String BROKER_USER  = System.getProperty("brokerUser", "artemis");
    private static final String BROKER_PASS  = System.getProperty("brokerPass", "artemis");
    private static final String QUEUE_NAME   = "fx.pacs009.inbound";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TestMessageSender <path-to-xml-file>");
            System.exit(1);
        }

        String xmlFilePath = args[0];
        String rawXml;
        try {
            rawXml = Files.readString(Paths.get(xmlFilePath));
        } catch (IOException e) {
            System.err.println("Cannot read file: " + xmlFilePath);
            System.exit(1);
            return;
        }

        try (ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(BROKER_URL, BROKER_USER, BROKER_PASS);
             Connection conn = cf.createConnection();
             Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            conn.start();
            Destination dest = session.createQueue(QUEUE_NAME);
            MessageProducer producer = session.createProducer(dest);
            TextMessage msg = session.createTextMessage(rawXml);
            producer.send(msg);

            System.out.println("✓ Message sent to " + QUEUE_NAME);
            System.out.println("  Broker : " + BROKER_URL);
            System.out.println("  File   : " + xmlFilePath);
            System.out.println("  Size   : " + rawXml.length() + " chars");
        }
    }
}
