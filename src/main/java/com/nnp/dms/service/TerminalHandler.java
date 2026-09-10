/**
 * TerminalHandler.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.entity.DeploymentEntity;
import com.nnp.dms.repository.DeploymentRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// WebSocket handler exposing an interactive SSH terminal in the browser.
// Protocol:
//   - connect to /terminal/{deploymentId}
//   - first message: optional {privateKey, passphrase} (re)auth; if omitted, cached key is used
//   - subsequent messages: JSON control messages ({type:"resize"}) or raw bytes piped to SSH stdin
//   - SSH stdout is pushed back to the browser as {type:"stdout", data:...}
@Component
public class TerminalHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TerminalHandler.class);

    private final DeploymentRepository deploymentRepo;
    private final ObjectMapper objectMapper;
    private final SshKeyCacheService sshKeyCache;
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    @Value("${dms.ssh.connect-timeout-ms:10000}")
    private int connectTimeoutMs = 10000;

    public TerminalHandler(DeploymentRepository deploymentRepo, ObjectMapper objectMapper,
                           SshKeyCacheService sshKeyCache) {
        this.deploymentRepo = deploymentRepo;
        this.objectMapper = objectMapper;
        this.sshKeyCache = sshKeyCache;
    }

@Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        // One WebSocket connection per deployment: derive the deployment id from the URL path.
        String deploymentId = extractDeploymentId(wsSession);
        if (deploymentId == null) {
            closeSession(wsSession, "Missing deploymentId");
            return;
        }
        sessions.put(wsSession.getId(), new TerminalSession(wsSession, deploymentId));
        log.info("WebSocket terminal opened for deployment {}", deploymentId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        TerminalSession ts = sessions.get(wsSession.getId());
        if (ts == null) return;

        String payload = message.getPayload();

        // First message: authentication with private key
        if (!ts.authenticated) {
            try {
                authenticate(ts, payload);
            } catch (Exception e) {
                log.error("Terminal auth failed: {}", e.getMessage());
                sendMessage(wsSession, "{\"type\":\"error\",\"data\":\"Authentication failed: " + e.getMessage() + "\"}");
                closeSession(wsSession, "Auth failed");
            }
            return;
        }

        // Subsequent messages: control messages (e.g. resize) or stdin to SSH shell.
        // JSON control messages are handled; everything else is forwarded as terminal input.
        if (payload.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                if (root.has("type") && "resize".equals(root.get("type").asText())) {
                    int cols = root.path("cols").asInt(80);
                    int rows = root.path("rows").asInt(24);
                    if (ts.channel != null && ts.channel.isConnected()) {
                        ts.channel.setPtySize(cols, rows, cols * 8, rows * 16);
                    }
                    return;
                }
            } catch (Exception ignored) {
                // Not a valid control JSON message, proceed to write to SSH stdin
            }
        }

        // Raw input -> SSH shell stdin.
        if (ts.outputStream != null) {
            try {
                ts.outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                ts.outputStream.flush();
            } catch (Exception e) {
                log.error("Failed to write to SSH shell: {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        // Clean up the associated SSH session when the browser disconnects.
        TerminalSession ts = sessions.remove(wsSession.getId());
        if (ts != null) {
            disconnect(ts);
            log.info("WebSocket terminal closed for deployment {}", ts.deploymentId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.error("WebSocket transport error: {}", exception.getMessage());
        TerminalSession ts = sessions.remove(wsSession.getId());
        if (ts != null) disconnect(ts);
    }

    // Establish an SSH ChannelShell (interactive PTY) using the supplied or cached key,
    // then start a daemon thread that streams remote output back over the WebSocket.
    private void authenticate(TerminalSession ts, String payload) throws Exception {
        String deploymentId = ts.deploymentId;
        Optional<DeploymentEntity> opt = deploymentRepo.findById(deploymentId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Deployment not found: " + deploymentId);
        }
        DeploymentEntity dep = opt.get();

         // to (re-)enter the SSH key, or send any message to use the cached key.
        JsonNode root = objectMapper.readTree(payload);
        String privateKey = root.has("privateKey") && !root.get("privateKey").isNull()
                && !root.get("privateKey").asText().isBlank()
                ? root.get("privateKey").asText() : null;
        String passphrase = root.has("passphrase") && !root.get("passphrase").isNull()
                ? root.get("passphrase").asText() : null;

        // No key in the message -> fall back to the cached key; if none, reject.
        String cachedKey = null;
        String cachedPassphrase = null;
        if (privateKey == null) {
            var cached = sshKeyCache.get(deploymentId);
            if (cached.isEmpty()) {
                throw new IllegalArgumentException("SSH key expired, please re-enter the key.");
            }
            cachedKey = cached.get().privateKey();
            cachedPassphrase = cached.get().passphrase();
        } else {
            sshKeyCache.put(deploymentId, privateKey, passphrase);
        }
        privateKey = privateKey != null ? privateKey : cachedKey;
        passphrase = passphrase != null ? passphrase : cachedPassphrase;

        // Build the JSch session + PTY shell on the deployment's target VM.
        JSch jsch = new JSch();
        byte[] keyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
        byte[] passBytes = (passphrase != null && !passphrase.isEmpty())
                ? passphrase.getBytes(StandardCharsets.UTF_8) : null;
        jsch.addIdentity(dep.getUsername(), keyBytes, null, passBytes);

        Session session = jsch.getSession(dep.getUsername(), dep.getHost(), dep.getPort());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(connectTimeoutMs);

        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        channel.setPty(true);
        channel.setPtyType("xterm-256color");
        channel.setPtySize(80, 24, 640, 480);

        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();

        channel.connect(connectTimeoutMs);

        ts.session = session;
        ts.channel = channel;
        ts.inputStream = in;
        ts.outputStream = out;
        ts.authenticated = true;

        sendMessage(ts.wsSession, "{\"type\":\"info\",\"data\":\"Connected to " + dep.getHost() + "\"}");

        // Start reading stdout in a background thread (daemon so it dies with the JVM).
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                while (!channel.isClosed()) {
                    while (in.available() > 0) {
                        int i = in.read(buf, 0, buf.length);
                        if (i < 0) break;
                        String data = new String(buf, 0, i, StandardCharsets.UTF_8);
                        String msg = "{\"type\":\"stdout\",\"data\":" + objectMapper.writeValueAsString(data) + "}";
                        sendMessage(ts.wsSession, msg);
                    }
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                log.debug("SSH reader stopped: {}", e.getMessage());
            }
            // Signal the browser that the remote connection ended, then close the socket.
            sendMessage(ts.wsSession, "{\"type\":\"stdout\",\"data\":\"\\r\\n[connection closed]\\r\\n\"}");
            closeSession(ts.wsSession, "SSH disconnected");
        }, "terminal-reader-" + deploymentId);
        reader.setDaemon(true);
        reader.start();

        // Window resize handler (receives JSON { type: "resize", cols: N, rows: N })
        ts.readerThread = reader;
    }

    // Close all SSH resources associated with a terminal session.
    private void disconnect(TerminalSession ts) {
        if (ts.readerThread != null && ts.readerThread.isAlive()) {
            ts.readerThread.interrupt();
        }
        try { if (ts.outputStream != null) ts.outputStream.close(); } catch (Exception ignored) {}
        try { if (ts.inputStream != null) ts.inputStream.close(); } catch (Exception ignored) {}
        try { if (ts.channel != null) ts.channel.disconnect(); } catch (Exception ignored) {}
        try { if (ts.session != null) ts.session.disconnect(); } catch (Exception ignored) {}
    }

    // Pull the deployment id from the last path segment of the WebSocket URL.
    private String extractDeploymentId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    private void sendMessage(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(msg));
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket message: {}", e.getMessage());
        }
    }

    private void closeSession(WebSocketSession session, String reason) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
            }
        } catch (Exception ignored) {}
    }

    // Per-connection state: associates the WebSocket with an SSH session once authenticated.
    private static class TerminalSession {
        final WebSocketSession wsSession;
        final String deploymentId;
        boolean authenticated;
        Session session;
        ChannelShell channel;
        InputStream inputStream;
        OutputStream outputStream;
        Thread readerThread;

        TerminalSession(WebSocketSession wsSession, String deploymentId) {
            this.wsSession = wsSession;
            this.deploymentId = deploymentId;
        }
    }
}
