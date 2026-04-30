package com.fx.payment.config;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.sql.SQLException;

/**
 * Opens local inspection ports for the embedded H2 database when running
 * outside the postgres profile (local dev / unit tests).
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
}
