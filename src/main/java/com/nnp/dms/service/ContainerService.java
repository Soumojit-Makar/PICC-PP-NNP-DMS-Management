/**
 * ContainerService.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.dto.ScriptExecutionResponse;
import com.nnp.dms.entity.DeploymentAction;
import com.nnp.dms.entity.DeploymentContainer;
import com.nnp.dms.exception.DeployException;
import com.nnp.dms.repository.ActionRepository;
import com.nnp.dms.repository.ContainerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

// Container lifecycle operations over SSH (list/restart/exec/remove) plus the
// deployment action audit trail (nnp_dms_deployment_actions).
@Service
@Slf4j
public class ContainerService {

    private final SshService sshService;
    private final ContainerRepository containerRepo;
    private final ActionRepository actionRepo;
    private final SshKeyCacheService sshKeyCache;

    public ContainerService(SshService sshService, ContainerRepository containerRepo,
                            ActionRepository actionRepo, SshKeyCacheService sshKeyCache) {
        this.sshService = sshService;
        this.containerRepo = containerRepo;
        this.actionRepo = actionRepo;
        this.sshKeyCache = sshKeyCache;
    }

    // Prefer the key supplied in the request (also refreshes the cache with it);
    // otherwise fall back to the cached key. Fails fast if neither exists.
    private Credentials resolveCredentials(String deploymentId, String privateKey, String passphrase) {
        if (privateKey != null && !privateKey.isBlank()) {
            sshKeyCache.put(deploymentId, privateKey, passphrase);
            return new Credentials(privateKey, passphrase);
        }
        Optional<SshKeyCacheService.CachedKey> cached = sshKeyCache.get(deploymentId);
        if (cached.isPresent()) {
            return new Credentials(cached.get().privateKey(), cached.get().passphrase());
        }
        throw new DeployException("SSH key expired, please re-enter the key.");
    }

    // SSH `sudo docker ps`, wipe the stored container rows for this deployment,
    // and persist the fresh list.
    @Transactional
    public List<DeploymentContainer> refreshContainers(String deploymentId, String host, int port,
                                                        String username, String privateKey, String passphrase) {
        String command = "sudo docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}'";
        Credentials creds = resolveCredentials(deploymentId, privateKey, passphrase);
        ScriptExecutionResponse res = sshService.runRemoteCommand(host, port, username,
                creds.privateKey(), creds.passphrase(), command);
        if (!res.isSuccess()) {
            log.warn("Failed to list containers for deployment {}: {} | exitCode={} | output={}",
                    deploymentId, res.getMessage(), res.getExitCode(), res.getRemoteOutput());
            return List.of();
        }

        containerRepo.deleteByDeploymentId(deploymentId);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        // Each output line is: name<TAB>image<TAB>status.
        String[] lines = res.getRemoteOutput().split("\n");
        for (String line : lines) {
            if (!line.isBlank()) {
                String[] parts = line.trim().split("\t", 3);
                String name = parts[0];
                String appName = parts.length > 1 ? parts[1] : null;
                String status = parts.length > 2 ? parts[2] : null;
                containerRepo.save(new DeploymentContainer(deploymentId, name, appName, status, now));
            }
        }
        log.info("Refreshed {} containers for deployment {}", lines.length, deploymentId);
        return containerRepo.findByDeploymentId(deploymentId);
    }

    // SSH `sudo docker restart <name>` and record it as an action.
    public ScriptExecutionResponse restartContainer(String deploymentId, String containerName,
                                                     String host, int port, String username,
                                                     String privateKey, String passphrase) {
        String command = "sudo docker restart " + containerName;
        Credentials creds = resolveCredentials(deploymentId, privateKey, passphrase);
        ScriptExecutionResponse res = sshService.runRemoteCommand(host, port, username,
                creds.privateKey(), creds.passphrase(), command);
        actionRepo.save(new DeploymentAction(deploymentId, "RESTART", containerName, command,
                res.getRemoteOutput(), res.getExitCode(), LocalDateTime.now(ZoneId.systemDefault())));
        return res;
    }

    // Run an arbitrary command on the VM, recording it as an action.
    public ScriptExecutionResponse execCommand(String deploymentId, String command,
                                                String host, int port, String username,
                                                String privateKey, String passphrase) {
        Credentials creds = resolveCredentials(deploymentId, privateKey, passphrase);
        ScriptExecutionResponse res = sshService.runRemoteCommand(host, port, username,
                creds.privateKey(), creds.passphrase(), command);
        actionRepo.save(new DeploymentAction(deploymentId, "EXEC", null, command,
                res.getRemoteOutput(), res.getExitCode(), LocalDateTime.now(ZoneId.systemDefault())));
        return res;
    }

    // SSH `sudo docker rm -f <name>`, record the action, and drop the container DB row.
    @Transactional
    public ScriptExecutionResponse removeContainer(String deploymentId, String containerName,
                                                    String host, int port, String username,
                                                    String privateKey, String passphrase) {
        String command = "sudo docker rm -f " + containerName;
        Credentials creds = resolveCredentials(deploymentId, privateKey, passphrase);
        ScriptExecutionResponse res = sshService.runRemoteCommand(host, port, username,
                creds.privateKey(), creds.passphrase(), command);
        actionRepo.save(new DeploymentAction(deploymentId, "REMOVE", containerName, command,
                res.getRemoteOutput(), res.getExitCode(), LocalDateTime.now(ZoneId.systemDefault())));
        containerRepo.findByDeploymentId(deploymentId).stream()
                .filter(c -> c.getContainerName().equals(containerName))
                .findFirst().ifPresent(containerRepo::delete);
        return res;
    }

    public List<DeploymentContainer> getContainers(String deploymentId) {
        return containerRepo.findByDeploymentId(deploymentId);
    }

    public List<DeploymentAction> getActions(String deploymentId) {
        return actionRepo.findByDeploymentIdOrderByPerformedAtDesc(deploymentId);
    }

    private record Credentials(String privateKey, String passphrase) {}
}
