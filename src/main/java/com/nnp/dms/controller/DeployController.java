/**
 * DeployController.java
 *
 * @author Soumojit Makar
 * @date 28-Jul-2026
 */
package com.nnp.dms.controller;

import com.nnp.dms.dto.*;
import com.nnp.dms.entity.DeploymentAction;
import com.nnp.dms.entity.DeploymentContainer;
import com.nnp.dms.entity.DeploymentEntity;
import com.nnp.dms.exception.DeployException;
import com.nnp.dms.service.ContainerService;
import com.nnp.dms.service.DeploymentService;
import com.nnp.dms.service.SshService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

// REST controller exposing all DMS deployment endpoints.
// Base path /api/dms is set via `server.servlet.context-path` in application.properties.
@RestController
@RequestMapping("")
public class DeployController {

    private final DeploymentService deploymentService;
    private final SshService sshService;
    private final ContainerService containerService;

    public DeployController(DeploymentService deploymentService, SshService sshService, ContainerService containerService) {
        this.deploymentService = deploymentService;
        this.sshService = sshService;
        this.containerService = containerService;
    }

    // One-shot VM health check: SSH in and curl the health endpoint on the remote host.
    @PostMapping("/check-status")
    public ResponseEntity<VmStatusResponse> checkStatus(@Valid @RequestBody VmStatusCheckRequest request) {
        VmStatusResponse result = sshService.checkVmStatus(request);
        return ResponseEntity.ok(result);
    }

    // Submit a new DMS deployment. Runs asynchronously -> returns 202 Accepted immediately
    // with a Location header pointing at the deployment status resource.
    @PostMapping("/create-dms")
    public ResponseEntity<CreateDeploymentResponse> createDms(@Valid @RequestBody DeployRequest request) {
        var result = deploymentService.submitDeployment(request);
        String locationUri = "/api/dms/deployments/" + result.getId();
        CreateDeploymentResponse body = new CreateDeploymentResponse(result.getId(), result.getStatus());
        body.setLocation(locationUri);
        return ResponseEntity
                .accepted()
                .location(URI.create(locationUri))
                .body(body);
    }

    // Fetch the current status of a single deployment (with cached component health).
    @GetMapping("/deployments/{id}")
    public ResponseEntity<DeploymentStatusResponse> getDeployment(@PathVariable String id) {
        return deploymentService.getDeployment(id)
                .map(deploymentService::toFullResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Paginated list of deployments, optionally filtered by component and/or status
    // (status supports comma-separated values e.g. DEPLOYED,SUCCESS).
    @GetMapping("/deployments")
    public ResponseEntity<Page<DeploymentStatusResponse>> listDeployments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String component,
            @RequestParam(required = false) String status) {
        Page<DeploymentEntity> entities = deploymentService.listDeployments(page, size, component, status);
        Page<DeploymentStatusResponse> response = entities.map(deploymentService::toFullResponse);
        return ResponseEntity.ok(response);
    }

    // Store/refresh the cached SSH key for a deployment (used when the key expires).
    @PostMapping("/deployments/{id}/ssh-key")
    public ResponseEntity<?> storeSshKey(@PathVariable String id, @Valid @RequestBody SshKeyRequest req) {
        boolean updated = deploymentService.refreshSshKey(id, req.getPrivateKey(), req.getPassphrase());
        if (!updated) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new ScriptExecutionResponse(true, "SSH key stored successfully.", null, 0));
    }

    // SSH in, run `sudo docker ps`, and persist the up-to-date container list for the deployment.
    @PostMapping("/deployments/{id}/refresh-containers")
    public ResponseEntity<List<DeploymentContainer>> refreshContainers(
            @PathVariable String id, @Valid @RequestBody RefreshRequest req) {
        DeploymentEntity dep = deploymentService.getDeployment(id).orElse(null);
        if (dep == null) return ResponseEntity.notFound().build();
        List<DeploymentContainer> containers = containerService.refreshContainers(
                id, dep.getHost(), dep.getPort(), dep.getUsername(),
                req.getPrivateKey(), req.getPassphrase());
        return ResponseEntity.ok(containers);
    }

    // Return the last known container list from the DB (no SSH call).
    @GetMapping("/deployments/{id}/containers")
    public ResponseEntity<List<DeploymentContainer>> listContainers(@PathVariable String id) {
        if (deploymentService.getDeployment(id).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(containerService.getContainers(id));
    }

    // SSH `sudo docker restart <containerName>`.
    @PostMapping("/deployments/{id}/restart")
    public ResponseEntity<ScriptExecutionResponse> restartContainer(
            @PathVariable String id, @Valid @RequestBody ContainerOperationRequest req) {
        DeploymentEntity dep = deploymentService.getDeployment(id).orElse(null);
        if (dep == null) return ResponseEntity.notFound().build();
        ScriptExecutionResponse res = containerService.restartContainer(
                id, req.getContainerName(), dep.getHost(), dep.getPort(), dep.getUsername(),
                req.getPrivateKey(), req.getPassphrase());
        return res.isSuccess() ? ResponseEntity.ok(res) : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(res);
    }

    // Run an arbitrary command on the remote VM (used e.g. for `docker logs`).
    @PostMapping("/deployments/{id}/exec")
    public ResponseEntity<ScriptExecutionResponse> execCommand(
            @PathVariable String id, @Valid @RequestBody ExecRequest req) {
        DeploymentEntity dep = deploymentService.getDeployment(id).orElse(null);
        if (dep == null) return ResponseEntity.notFound().build();
        ScriptExecutionResponse res = containerService.execCommand(
                id, req.getCommand(), dep.getHost(), dep.getPort(), dep.getUsername(),
                req.getPrivateKey(), req.getPassphrase());
        return res.isSuccess() ? ResponseEntity.ok(res) : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(res);
    }

    // SSH `sudo docker rm -f <containerName>` and delete the container record in the DB.
    @PostMapping("/deployments/{id}/remove")
    public ResponseEntity<ScriptExecutionResponse> removeContainer(
            @PathVariable String id, @Valid @RequestBody ContainerOperationRequest req) {
        DeploymentEntity dep = deploymentService.getDeployment(id).orElse(null);
        if (dep == null) return ResponseEntity.notFound().build();
        ScriptExecutionResponse res = containerService.removeContainer(
                id, req.getContainerName(), dep.getHost(), dep.getPort(), dep.getUsername(),
                req.getPrivateKey(), req.getPassphrase());
        return res.isSuccess() ? ResponseEntity.ok(res) : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(res);
    }

    // Audit trail of operations (restart/exec/remove) performed on a deployment.
    @GetMapping("/deployments/{id}/actions")
    public ResponseEntity<List<DeploymentAction>> listActions(@PathVariable String id) {
        if (deploymentService.getDeployment(id).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(containerService.getActions(id));
    }

    // Central handler: any DeployException is converted to a 502 BAD_GATEWAY response.
    @ExceptionHandler(DeployException.class)
    public ResponseEntity<ScriptExecutionResponse> handleDeployException(DeployException ex) {
        ScriptExecutionResponse body = new ScriptExecutionResponse(false, ex.getMessage(), null, -1);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}
