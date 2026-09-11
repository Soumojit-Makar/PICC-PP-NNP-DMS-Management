/**
 * DeploymentService.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.dto.ComponentStatusResponse;
import com.nnp.dms.dto.DeployRequest;
import com.nnp.dms.dto.DeploymentStatusResponse;
import com.nnp.dms.entity.DeploymentComponentEntity;
import com.nnp.dms.entity.DeploymentEntity;
import com.nnp.dms.entity.Environment;
import com.nnp.dms.repository.ComponentStatusRepository;
import com.nnp.dms.repository.DeploymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// Orchestration service for deployments: submission, DB persistence, SSH-key caching,
// listing/querying, and mapping entities to API response DTOs.
@Service
@Slf4j
public class DeploymentService {

    private final DeploymentRepository repository;
    private final SshService sshService;
    private final DeploymentRunner deploymentRunner;
    private final ComponentStatusRepository componentRepo;
    private final SshKeyCacheService sshKeyCache;
    private final EnvironmentPlanService environmentPlanService;
    private final EnvActivityLogService activityLogService;

    public DeploymentService(DeploymentRepository repository, SshService sshService,
                             DeploymentRunner deploymentRunner, ComponentStatusRepository componentRepo,
                             SshKeyCacheService sshKeyCache, EnvironmentPlanService environmentPlanService,
                             EnvActivityLogService activityLogService) {
        this.repository = repository;
        this.sshService = sshService;
        this.deploymentRunner = deploymentRunner;
        this.componentRepo = componentRepo;
        this.sshKeyCache = sshKeyCache;
        this.environmentPlanService = environmentPlanService;
        this.activityLogService = activityLogService;
    }

    // Validate the environment, persist a PENDING deployment row, cache the SSH key,
    // write an activity entry, and kick off the async worker. Returns immediately.
    public CreateDeploymentResult submitDeployment(DeployRequest req) {
        Environment env = environmentPlanService.resolveEnvironment(req.getEnvId());
        String userId = env.getEnvCustId();

        // Planned DMS component validation is currently disabled (commented out):
        // it would assert the environment has at least one active DMS plan component.
        String id = UUID.randomUUID().toString();
        DeploymentEntity entity = new DeploymentEntity(id, "PENDING",
                req.getHost(), req.getPort(), req.getUsername(),
                null, null, req.getFilePath());
        entity.setComponent("dms");
        entity.setEnvId(req.getEnvId());
        repository.save(entity);

        // Leave a trace in the audit log right away (submitted state).
        activityLogService.logActivity(req.getEnvId(),
                "DMS deployment submitted for environment " + env.getEnvCode(),
                "PENDING", "DMS_DEPLOY_SUBMITTED", userId);

        // Remember the key in-memory (one hour TTL) so later tasks can reconnect;
        // it is never written to the DB.
        sshKeyCache.put(id, req.getPrivateKey(), req.getPassphrase());

        // Fire-and-forget: DeploymentRunner executes the actual SSH deployment async.
        deploymentRunner.runDeployment(id, req);

        return new CreateDeploymentResult(id, "PENDING");
    }

    // Re-cache a fresh SSH key for an existing deployment. Returns false if the deployment does not exist.
    public boolean refreshSshKey(String id, String privateKey, String passphrase) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        sshKeyCache.put(id, privateKey, passphrase);
        return true;
    }

    public Optional<DeploymentEntity> getDeployment(String id) {
        return repository.findById(id);
    }

    // Paginated query with optional component / status filters (status is comma-separated).
    public Page<DeploymentEntity> listDeployments(int page, int size, String component, String status) {
        PageRequest pr = PageRequest.of(page, size);
        if (component != null && status != null) {
            return repository.findByComponentAndStatusIn(component, List.of(status.split(",")), pr);
        }
        if (component != null) {
            return repository.findByComponent(component, pr);
        }
        if (status != null) {
            return repository.findByStatusIn(List.of(status.split(",")), pr);
        }
        return repository.findAllByOrderByCreatedAtDesc(pr);
    }

    public List<DeploymentEntity> findByStatusIn(List<String> statuses) {
        return repository.findByStatusIn(statuses);
    }

    public void save(DeploymentEntity entity) {
        repository.save(entity);
    }

    // Current component health records for a deployment (as stored by the scheduler).
    public List<ComponentStatusResponse> getComponents(String deploymentId) {
        return componentRepo.findByDeploymentId(deploymentId).stream()
                .map(this::toComponentResponse)
                .toList();
    }

    // Replace the stored component health list for a deployment (delete + re-insert).
    public void saveComponents(String deploymentId, List<ComponentStatusResponse> components) {
        componentRepo.deleteByDeploymentId(deploymentId);
        for (ComponentStatusResponse c : components) {
            componentRepo.save(new DeploymentComponentEntity(
                    deploymentId, c.getLabel(), c.getContainer(),
                    c.getStatus(), c.getDetail(), c.getCheckedAt()));
        }
    }

    // Flatten entity -> full API response (components come from a separate table).
    public DeploymentStatusResponse toFullResponse(DeploymentEntity e) {
        DeploymentStatusResponse r = new DeploymentStatusResponse();
        r.setId(e.getId());
        r.setStatus(e.getStatus());
        r.setMessage(e.getMessage());
        r.setExitCode(e.getExitCode());
        r.setHost(e.getHost());
        r.setPort(e.getPort());
        r.setUsername(e.getUsername());
        r.setFilePath(e.getFilePath());
        r.setComponent(e.getComponent());
        r.setEnvId(e.getEnvId());
        r.setCreatedAt(e.getCreatedAt());
        r.setStartedAt(e.getStartedAt());
        r.setCompletedAt(e.getCompletedAt());
        r.setHeartbeatAt(e.getHeartbeatAt());
        r.setComponents(getComponents(e.getId()));
        return r;
    }

    // Map a component entity to its DTO form.
    private ComponentStatusResponse toComponentResponse(DeploymentComponentEntity c) {
        ComponentStatusResponse r = new ComponentStatusResponse();
        r.setLabel(c.getLabel());
        r.setContainer(c.getContainer());
        r.setStatus(c.getStatus());
        r.setDetail(c.getDetail());
        r.setCheckedAt(c.getCheckedAt());
        return r;
    }

    // Lightweight result of submitDeployment: id + initial status.
    public static class CreateDeploymentResult {
        private final String id;
        private final String status;

        public CreateDeploymentResult(String id, String status) {
            this.id = id;
            this.status = status;
        }

        public String getId() { return id; }
        public String getStatus() { return status; }
    }
}
