package com.fx.payment.util;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standalone utility to send a pacs.009 XML file to the inbound RabbitMQ queue.
 *
 * Usage (while the app is running with Docker RabbitMQ):
 * <pre>
 *   mvn exec:java \
 *     -Dexec.mainClass="com.fx.payment.util.TestMessageSender" \
 *     -Dexec.args="src/test/resources/messages/valid-pacs009.xml"
 * </pre>
 *
 * For Docker mode the broker URL is localhost:5672.
 * Use the Spring Boot actuator /send endpoint for interactive testing.
 */
@Slf4j
public class TestMessageSender {

    private static final String BROKER_HOST = System.getProperty("brokerHost", "localhost");
    private static final int BROKER_PORT = Integer.getInteger("brokerPort", 5672);
    private static final String BROKER_USER = System.getProperty("brokerUser", "guest");
    private static final String BROKER_PASS = System.getProperty("brokerPass", "guest");
    private static final String EXCHANGE_NAME = "fx.payment.exchange";
    private static final String ROUTING_KEY = "pacs009.inbound";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            log.error("Usage: TestMessageSender <path-to-xml-file>");
            System.exit(1);
        }

        String xmlFilePath = args[0];
        String rawXml;
        try {
            rawXml = Files.readString(Paths.get(xmlFilePath));
        } catch (IOException e) {
            log.error("Cannot read file: {}", xmlFilePath, e);
            System.exit(1);
            return;
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER_HOST);
        factory.setPort(BROKER_PORT);
        factory.setUsername(BROKER_USER);
        factory.setPassword(BROKER_PASS);

        try (Connection conn = factory.newConnection();
             Channel channel = conn.createChannel()) {

            // Declare exchange (must match the one in RabbitConfig)
            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);

            // Publish message
            channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, rawXml.getBytes());
            log.info("Sent message to exchange='{}' routingKey='{}'", EXCHANGE_NAME, ROUTING_KEY);
            log.info("  Broker : {}:{}", BROKER_HOST, BROKER_PORT);
            log.info("  File   : {}", xmlFilePath);
            log.info("  Size   : {} chars", rawXml.length());
        }
    }
}
