/**
 * WebSocketConfig.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.config;

import com.nnp.dms.service.TerminalHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalHandler terminalHandler;

    public WebSocketConfig(TerminalHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

// Registers the interactive SSH terminal WebSocket endpoint.
    // The deploymentId is read from the URL template variable ({deploymentId}).
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalHandler, "/terminal/{deploymentId}")
                .setAllowedOrigins("*");
    }
}
