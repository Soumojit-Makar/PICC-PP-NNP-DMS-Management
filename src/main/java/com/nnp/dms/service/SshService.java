/**
 * SshService.java
 *
 * @author Soumojit Makar
 * @date 28-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.config.GitProperties;
import com.nnp.dms.dto.*;
import com.nnp.dms.entity.Environment;
import com.nnp.dms.exception.DeployException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// Thin SSH layer built on JSch.
// Every operation follows the same pattern: open a session with the private key,
// run one exec channel command, capture stdout+stderr and the exit code, then disconnect.
@Service
@Slf4j
public class SshService {

    @Value("${dms.ssh.connect-timeout-ms:10000}")
    private int connectTimeoutMs = 10_000;

    @Value("${deployment.health.port:9099}")
    private int defaultHealthPort = 9099;

    private final GitProperties gitProperties;
    private final ObjectMapper objectMapper;
    private final EnvironmentPlanService environmentPlanService;

    public SshService(GitProperties gitProperties, ObjectMapper objectMapper,
                      EnvironmentPlanService environmentPlanService) {
        this.gitProperties = gitProperties;
        this.objectMapper = objectMapper;
        this.environmentPlanService = environmentPlanService;
    }

    // ---------- VM Status Check ----------

    // Default health check against configured port (see deployment.health.port).
    public VmStatusResponse checkVmStatus(VmStatusCheckRequest req) {
        return checkVmStatus(req, defaultHealthPort);
    }

    // SSH into the VM, call `curl <healthPort>/status`, then normalize the
    // returned JSON into our response model. "unreachable"/"parse_error" signal failures.
    public VmStatusResponse checkVmStatus(VmStatusCheckRequest req, int healthPort) {
        String command = "curl -s http://localhost:" + healthPort + "/status";
        ExecResult result = withSession(
                req.getHost(), req.getPort(), req.getUsername(),
                req.getPrivateKey(), req.getPassphrase(),
                session -> runCommand(session, command, null)
        );

        VmStatusResponse response = new VmStatusResponse();
        response.setHost(req.getHost());
        response.setCheckedAt(LocalDateTime.now(ZoneId.systemDefault()));

        if (result.exitCode != 0 || result.output.isBlank()) {
            response.setOverall("unreachable");
            response.setComponents(List.of());
            return response;
        }

        try {
            HealthCheckResponse health = objectMapper.readValue(result.output, HealthCheckResponse.class);
            response.setOverall(health.getOverall() != null ? health.getOverall() : "unknown");

            // Convert the remote health JSON components into API component DTOs.
            List<ComponentStatusResponse> components = new ArrayList<>();
            if (health.getComponents() != null) {
                for (HealthCheckResponse.HealthComponent hc : health.getComponents()) {
                    ComponentStatusResponse cr = new ComponentStatusResponse();
                    cr.setLabel(hc.getLabel());
                    cr.setContainer(hc.getContainer());
                    cr.setStatus(hc.getStatus());
                    cr.setDetail(hc.getDetail());
                    cr.setCheckedAt(LocalDateTime.now(ZoneId.systemDefault()));
                    components.add(cr);
                }
            }
            response.setComponents(components);
        } catch (Exception e) {
            // The endpoint responded but the body was not valid JSON.
            response.setOverall("parse_error");
            response.setComponents(List.of());
        }

        return response;
    }

    // ---------- DMS Deployment ----------

    // Full deployment flow on the remote VM:
    //   1. Ensure git is installed (auto-install via package manager if missing)
    //   2. Clone the deploy-script repo (auth embedded in the URL)
    //   3. Run deploy-dms.sh with the environment's customer info as env vars
    // Runs through `sudo bash` because the script needs root (docker, etc.).
    public ScriptExecutionResponse deployDMS(DeployRequest req) {
        log.info("Deploying dms on remote host...");
        Environment env = environmentPlanService.resolveEnvironment(req.getEnvId());
        String repoUrl = gitProperties.getUrl();
        String gitUser = gitProperties.getUsername();
        String gitToken = gitProperties.getToken();
        String branch = gitProperties.getBranch() != null ? gitProperties.getBranch() : "main";
        String authUrl = repoUrl != null ? repoUrl : "";
        if (gitToken != null && !gitToken.isBlank() && repoUrl != null && repoUrl.startsWith("https://")) {
            String user = (gitUser != null && !gitUser.isBlank()) ? gitUser : "oauth2";
            authUrl = repoUrl.replace("https://", "https://" + user + ":" + gitToken + "@");
        }
        String sanitizedPath = sanitizePath(req.getFilePath());
        log.info("Sanitized path: " + sanitizedPath);

        // Try to locate git; if absent, install it using whichever package manager exists.
        String gitCheckCommand =
                "command -v git >/dev/null 2>&1 || " +
                        "{ command -v apt-get >/dev/null 2>&1 && sudo -n apt-get update -qq && sudo -n apt-get install -y -qq git; } || " +
                        "{ command -v dnf >/dev/null 2>&1 && sudo -n dnf install -y -q git; } || " +
                        "{ command -v yum >/dev/null 2>&1 && sudo -n yum install -y -q git; } || " +
                        "{ command -v zypper >/dev/null 2>&1 && sudo -n zypper install -y git; } || " +
                        "{ command -v apk >/dev/null 2>&1 && apk add git; } || " +
                        "{ echo 'FATAL: git not found and auto-install failed'; exit 1; }";

        String adminUser = (env != null && env.getEnvCustId() != null) ? env.getEnvCustId() : "";
        String adminEmail = (env != null && env.getEnvEmail() != null) ? env.getEnvEmail() : "";
        String adminPass = (req.getPassword() != null) ? req.getPassword() : "";
        String safeToken = (gitToken != null) ? gitToken : "";

        // Clone + run the deploy script. The path and branch are shell-quoted,
        // and customer/admin credentials are passed as env vars consumed by deploy-dms.sh.
        String innerScript = String.format(
                "mkdir -p %s && cd %s && rm -rf dms-deploy-script && git clone -b %s %s && cd dms-deploy-script && GIT_TOKEN=%s GITLAB_TOKEN=%s ADMIN_USER=%s ADMIN_EMAIL=%s ADMIN_PASS=%s bash deploy-dms.sh",
                shQuote(sanitizedPath),
                shQuote(sanitizedPath),
                shQuote(branch),
                shQuote(authUrl),
                shQuote(safeToken),
                shQuote(safeToken),
                shQuote(adminUser),
                shQuote(adminEmail),
                shQuote(adminPass)
        );
        String command = String.format(
                "%s && sudo bash <<'DMS_SCRIPT'%n%s%nDMS_SCRIPT",
                gitCheckCommand,
                innerScript
        );
        log.info("Sanitized command: " + command);
        ExecResult result = withSession(
                req.getHost(), req.getPort(), req.getUsername(),
                req.getPrivateKey(), req.getPassphrase(),
                session -> {
                    session.setTimeout(0); // disable read timeout for long deploy
                    return runCommand(session, command, null);
                }
        );
        boolean success = result.exitCode == 0;
        log.info("Sanitized result: " + success);
        return new ScriptExecutionResponse(
                success,
                success ? "Deployment completed successfully." : "Deployment failed.",
                result.output.trim(),
                result.exitCode
        );
    }

    // ---------- Generic remote command ----------

    // Run a single arbitrary command on a remote host and return the result.
    public ScriptExecutionResponse runRemoteCommand(String host, int port, String username,
                                                     String privateKey, String passphrase, String command) {
        log.info("Running remote command on {}:{}", host, port);
        ExecResult result = withSession(
                host, port, username, privateKey, passphrase,
                session -> runCommand(session, command, null)
        );
        return new ScriptExecutionResponse(
                result.exitCode == 0,
                result.exitCode == 0 ? "Success" : "Command failed",
                result.output.trim(),
                result.exitCode
        );
    }

    // ---------- Shared connection handling ----------

    private interface SessionWork {
        ExecResult run(Session session) throws Exception;
    }

    // Template method: builds a JSch session authenticated with a private key,
    // runs the supplied SessionWork, grabs the result, and always disconnects.
    // Any failure surfaces as a DeployException.
    private ExecResult withSession(String host, int port, String username,
                                   String privateKey, String passphrase, SessionWork work) {
        JSch jsch = new JSch();
        Session session = null;
        log.info("Connecting to host: " + host);
        try {
            byte[] keyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
            byte[] passphraseBytes = (passphrase != null && !passphrase.isBlank())
                    ? passphrase.getBytes(StandardCharsets.UTF_8)
                    : null;

            jsch.addIdentity(username, keyBytes, null, passphraseBytes);
            session = jsch.getSession(username, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(connectTimeoutMs);
            session.connect(connectTimeoutMs);
            log.info("Connection established.");
            return work.run(session);

        } catch (Exception e) {
            log.error("SSH operation failed against {}:{}", host, port, e);
            throw new DeployException("SSH operation failed: " + e.getMessage(), e);
        } finally {
            // Always clean up the session so we never leak SSH connections.
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    // Simple result holder for an executed command.
    private static class ExecResult {
        final String output;
        final int exitCode;
        ExecResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
    }

    // Execute a command on a connect session, draining stdin (optional), stdout and stderr
    // until the channel closes, then return the merged output and exit code.
    private ExecResult runCommand(Session session, String command, byte[] stdin) throws Exception {
        log.info("Executing command: " + command);
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        channel.setErrStream(errBuffer);

        InputStream in = channel.getInputStream();
        OutputStream out = stdin != null ? channel.getOutputStream() : null;
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

        channel.connect(connectTimeoutMs);
        log.info("Channel is connected.");

        if (stdin != null) {
            out.write(stdin);
            out.flush();
            out.close();
        }

        // Busy-wait loop until the exec channel reports it is closed.
        byte[] buf = new byte[4096];
        while (true) {
            while (in.available() > 0) {
                int i = in.read(buf, 0, buf.length);
                if (i < 0) break;
                outBuffer.write(buf, 0, i);
            }
            if (channel.isClosed()) {
                if (in.available() > 0) continue;
                break;
            }
            Thread.sleep(200);
        }
        log.info("Channel is closed.");
        int exitCode = channel.getExitStatus();
        String stdout = outBuffer.toString(StandardCharsets.UTF_8);
        String stderr = errBuffer.toString(StandardCharsets.UTF_8);
        channel.disconnect();
        log.info("ExitCode: " + exitCode);
        // Merge stderr so a failed command still shows the error in the response.
        String merged = stderr.isEmpty() ? stdout : stdout + "\n--- stderr ---\n" + stderr;
        return new ExecResult(merged, exitCode);
    }

    // Guard against obviously dangerous path input (newlines could break the shell command).
    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new DeployException("deployPath must not be blank");
        }
        if (path.contains("\n") || path.contains("\r")) {
            throw new DeployException("deployPath must not contain newlines");
        }
        return path.trim();
    }

    // POSIX single-quote escaping: wraps a value in quotes, breaking/rejoining on any embedded quote.
    private String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}