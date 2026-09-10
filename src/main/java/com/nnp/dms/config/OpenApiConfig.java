package com.nnp.dms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 / Swagger UI Configuration for PICC-PP-NNP-DMS-Management.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api/dms}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PICC-PP-NNP-DMS-Management REST API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                REST & WebSocket API for orchestrating asynchronous DMS deployments, \
                                remote VM lifecycle checks, Docker container management, and interactive terminal sessions.
                                
                                ### Key Features:
                                * **Asynchronous Deployment**: Submits deployment tasks to dedicated thread pools returning immediate `202 Accepted` tracking tokens.
                                * **Zero-Persistence SSH Key Security**: Private keys cached only in volatile memory with configurable TTL.
                                * **Remote Container Management**: Inspect, restart, and remove Docker containers on target deployment nodes via SSH.
                                * **Interactive Web Terminal**: WebSocket-powered interactive SSH session with VT100 / xterm support.
                                * **Automated Health Monitoring**: Background cron-based scheduled health reconciliation.
                                """)
                        .contact(new Contact()
                                .name("Nubo Native Platform Team")
                                .email("contribution@nubons.com")
                                .url("https://github.com/Nubo-Native-Platform/PICC-PP-NNP-DMS-Management"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url(contextPath).description("Default Server / Context Path"),
                        new Server().url("http://localhost:" + serverPort + contextPath).description("Local Development Server")
                ))
                .tags(List.of(
                        new Tag().name("DMS Deployment").description("Endpoints for submitting, tracking, and managing DMS VM deployments"),
                        new Tag().name("Container Operations").description("Endpoints for inspecting and managing remote Docker containers"),
                        new Tag().name("Health & Diagnostics").description("Remote VM status checks and component health monitoring")
                ));
    }
}
