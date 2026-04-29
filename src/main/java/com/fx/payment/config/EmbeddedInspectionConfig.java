package com.fx.payment.config;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Opens local inspection ports for the embedded H2 database and embedded
 * Artemis broker while the SwiftPay JVM is running.
 */
@Configuration
@Profile("!postgres")
public class EmbeddedInspectionConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "fx.inspection.h2", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Server h2TcpServer(@Value("${fx.inspection.h2.tcp-port:9092}") String tcpPort) throws SQLException {
        return Server.createTcpServer(
                "-tcp",
                "-tcpAllowOthers",
                "-tcpPort", tcpPort);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "fx.inspection.h2.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Server h2WebServer(@Value("${fx.inspection.h2.web-port:8082}") String webPort) throws SQLException {
        return Server.createWebServer(
                "-web",
                "-webAllowOthers",
                "-webPort", webPort);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fx.inspection.artemis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ArtemisConfigurationCustomizer artemisTcpAcceptor(
            @Value("${fx.inspection.artemis.host:127.0.0.1}") String host,
            @Value("${fx.inspection.artemis.port:61616}") int port) {

        return configuration -> {
            configuration.setSecurityEnabled(false);
            configuration.setJMXManagementEnabled(true);

            Map<String, Object> params = new HashMap<>();
            params.put("host", host);
            params.put("port", port);
            params.put("protocols", "CORE");

            configuration.addAcceptorConfiguration(
                    new org.apache.activemq.artemis.api.core.TransportConfiguration(
                            "org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory",
                            params,
                            "inspection-netty"));
        };
    }
}
