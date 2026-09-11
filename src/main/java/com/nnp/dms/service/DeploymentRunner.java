/**
 * DeploymentRunner.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.dto.DeployRequest;
import com.nnp.dms.entity.DeploymentEntity;
import com.nnp.dms.repository.DeploymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

// Async worker that actually performs the DMS deployment on the remote VM.
// Called by DeploymentService.submitDeployment() on the "deploymentExecutor" pool.
@Service
@Slf4j
public class DeploymentRunner {

    private final DeploymentRepository repository;
    private final SshService sshService;
    private final SshKeyCacheService sshKeyCache;
    private final EnvActivityLogService activityLogService;
    private final EnvironmentPlanService environmentPlanService;

    public DeploymentRunner(DeploymentRepository repository, SshService sshService,
                            SshKeyCacheService sshKeyCache, EnvActivityLogService activityLogService,
                            EnvironmentPlanService environmentPlanService) {
        this.repository = repository;
        this.sshService = sshService;
        this.sshKeyCache = sshKeyCache;
        this.activityLogService = activityLogService;
        this.environmentPlanService = environmentPlanService;
    }

    // Executes on a background thread (deploymentExecutor). Transitions the deployment
    // PENDING -> RUNNING -> DEPLOYED (success) or FAILED, tracking start/heartbeat/completion times.
    @Async("deploymentExecutor")
    public void runDeployment(String id, DeployRequest req) {
        Optional<DeploymentEntity> opt = repository.findById(id);
        if (opt.isEmpty()) {
            log.warn("Deployment {} not found, skipping", id);
            return;
        }

        // Mark as running before doing any remote work.
        DeploymentEntity entity = opt.get();
        entity.setStatus("RUNNING");
        entity.setStartedAt(LocalDateTime.now(ZoneId.systemDefault()));
        entity.setHeartbeatAt(LocalDateTime.now(ZoneId.systemDefault()));
        repository.save(entity);
        logActivity(entity, "DMS deployment started for environment " + entity.getEnvId(),
                "RUNNING", "DMS_DEPLOY_RUNNING");

        // The user's private key lives only in the in-memory cache; if it has expired
        // before the async worker runs, the deployment cannot proceed.
        var cachedKey = sshKeyCache.get(id);
        if (cachedKey.isEmpty()) {
            log.warn("SSH key expired for deployment {}, skipping", id);
            entity.setStatus("FAILED");
            entity.setMessage("SSH key expired, please re-enter the key.");
            entity.setCompletedAt(LocalDateTime.now(ZoneId.systemDefault()));
            repository.save(entity);
            logActivity(entity, "DMS deployment failed: SSH key expired", "FAILED", "DMS_DEPLOY_FAILED");
            return;
        }
        req.setPrivateKey(cachedKey.get().privateKey());
        req.setPassphrase(cachedKey.get().passphrase());

        try {
            // SSH clone + deploy-dms.sh on the remote VM (long-running).
            var response = sshService.deployDMS(req);
            entity.setStatus(response.isSuccess() ? "DEPLOYED" : "FAILED");
            entity.setMessage(response.getMessage());
            entity.setExitCode(response.getExitCode());
            entity.setHeartbeatAt(LocalDateTime.now(ZoneId.systemDefault()));
            if (!response.isSuccess()) {
                entity.setCompletedAt(LocalDateTime.now(ZoneId.systemDefault()));
            }
            repository.save(entity);
            logActivity(entity,
                    response.isSuccess()
                            ? "DMS deployment completed successfully for environment " + entity.getEnvId()
                            : "DMS deployment failed for environment " + entity.getEnvId(),
                    entity.getStatus(), response.isSuccess() ? "DMS_DEPLOY_COMPLETED" : "DMS_DEPLOY_FAILED");
        } catch (Exception e) {
            // Network/script error -> fail the deployment explicitly.
            log.error("Deployment {} failed with exception", id, e);
            entity.setStatus("FAILED");
            entity.setMessage(e.getMessage());
            entity.setCompletedAt(LocalDateTime.now(ZoneId.systemDefault()));
            repository.save(entity);
            logActivity(entity, "DMS deployment failed for environment " + entity.getEnvId() + ": " + e.getMessage(),
                    "FAILED", "DMS_DEPLOY_FAILED");
        }
    }

    // Best-effort activity logging; never let a logging error break the deployment flow.
    private void logActivity(DeploymentEntity entity, String desc, String status, String note) {
        try {
            String userId = environmentPlanService.resolveEnvironment(entity.getEnvId()).getEnvCustId();
            activityLogService.logActivity(entity.getEnvId(), desc, status, note, userId);
        } catch (Exception e) {
            log.error("Failed to log activity for deployment {}", entity.getId(), e);
        }
    }
}
