/**
 * DeploymentScheduler.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.dto.ComponentStatusResponse;
import com.nnp.dms.dto.VmStatusCheckRequest;
import com.nnp.dms.dto.VmStatusResponse;
import com.nnp.dms.entity.DeploymentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// Background health checker. Runs on a cron (every 5 minutes) and re-checks
// deployments still in DEPLOYED state, moving them to SUCCESS / DEGRADED / FAILED
// based on the remote health endpoint, and stores per-component statuses.
@Component
@Slf4j
public class DeploymentScheduler {

    private final DeploymentService deploymentService;
    private final SshService sshService;
    private final SshKeyCacheService sshKeyCache;
    private final EnvActivityLogService activityLogService;
    private final EnvironmentPlanService environmentPlanService;

    @Value("${deployment.health.port:9099}")
    private int healthPort;

    public DeploymentScheduler(DeploymentService deploymentService, SshService sshService,
                               SshKeyCacheService sshKeyCache, EnvActivityLogService activityLogService,
                               EnvironmentPlanService environmentPlanService) {
        this.deploymentService = deploymentService;
        this.sshService = sshService;
        this.sshKeyCache = sshKeyCache;
        this.activityLogService = activityLogService;
        this.environmentPlanService = environmentPlanService;
    }

    // Cron: default every 5 minutes on minute 0 (configurable via deployment.scheduler.cron).
    // Only DEPLOYED deployments are re-checked; terminal states are left alone.
    @Scheduled(cron = "${deployment.scheduler.cron:0 */5 * * * *}")
    public void checkDeployments() {
        List<DeploymentEntity> deployments = deploymentService.findByStatusIn(
                List.of("DEPLOYED"));
        log.info("Scheduler checking {} DEPLOYED deployments", deployments.size());

        for (DeploymentEntity dep : deployments) {
            try {
                // Isolate each check so one failure does not abort the whole batch.
                checkDeployment(dep);
            } catch (Exception e) {
                log.error("Scheduler check failed for deployment {}: {}", dep.getId(), e.getMessage(), e);
            }
        }
    }

    // Ship one deployment through a health check and update status/components/audit accordingly.
    private void checkDeployment(DeploymentEntity dep) {
        var cachedKey = sshKeyCache.get(dep.getId());
        if (cachedKey.isEmpty()) {
            // No key cached (or expired) -> we cannot even reach the VM; record the need to re-enter the key.
            log.warn("SSH key expired for deployment {}, skipping health check", dep.getId());
            dep.setMessage("SSH key expired, please re-enter the key.");
            deploymentService.save(dep);
            return;
        }

        // Reuse the cached SSH key to call the remote health endpoint.
        VmStatusCheckRequest req = new VmStatusCheckRequest();
        req.setHost(dep.getHost());
        req.setPort(dep.getPort());
        req.setUsername(dep.getUsername());
        req.setPrivateKey(cachedKey.get().privateKey());
        req.setPassphrase(cachedKey.get().passphrase());

        VmStatusResponse status = sshService.checkVmStatus(req, healthPort);
        String overall = status.getOverall();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        // Unreachable or unparseable health data -> the system is assumed down.
        if ("unreachable".equals(overall) || "parse_error".equals(overall)) {
            dep.setStatus("FAILED");
            dep.setMessage("Health check failed: " + overall);
            dep.setHeartbeatAt(now);
            dep.setCompletedAt(now);
            deploymentService.save(dep);
            logActivity(dep, "DMS health check failed: " + overall, "FAILED");
            log.info("Scheduler marked {} as FAILED (health check {})", dep.getId(), overall);
            return;
        }

        // Persist the per-component status snapshot for the API response.
        List<ComponentStatusResponse> componentResponses = status.getComponents();
        if (componentResponses != null && !componentResponses.isEmpty()) {
            for (ComponentStatusResponse cr : componentResponses) {
                cr.setCheckedAt(now);
            }
            deploymentService.saveComponents(dep.getId(), componentResponses);
        }

        // healthy -> SUCCESS; degraded -> DEGRADED (some components down); anything else -> FAILED.
        switch (overall) {
            case "healthy":
                dep.setStatus("SUCCESS");
                dep.setMessage("All components healthy.");
                break;
            case "degraded":
                dep.setStatus("DEGRADED");
                dep.setMessage("Some components are degraded.");
                break;
            default:
                dep.setStatus("FAILED");
                dep.setMessage("System is down or unknown.");
                break;
        }

        dep.setHeartbeatAt(now);
        dep.setCompletedAt(now);
        deploymentService.save(dep);
        logActivity(dep, "DMS health check completed with status: " + dep.getStatus(), dep.getStatus());

        log.info("Scheduler completed check for {}: status={}", dep.getId(), dep.getStatus());
    }

    // Best-effort activity logging for the health check outcome.
    private void logActivity(DeploymentEntity dep, String desc, String status) {
        try {
            String userId = environmentPlanService.resolveEnvironment(dep.getEnvId()).getEnvCustId();
            activityLogService.logActivity(dep.getEnvId(), desc, status, "DMS_HEALTH_CHECK", userId);
        } catch (Exception e) {
            log.error("Failed to log activity for deployment {}", dep.getId(), e);
        }
    }
}
