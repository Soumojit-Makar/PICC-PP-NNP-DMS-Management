/**
 * DeploymentStatusResponse.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

// Full API view of a deployment (GET /deployments and list endpoints). Mirrors
// DeploymentEntity plus the last known component health snapshot.
@Getter
@Setter
public class DeploymentStatusResponse {
    private String id;
    private String status;                 // PENDING / RUNNING / DEPLOYED / SUCCESS / DEGRADED / FAILED
    private String message;                // Status message / error
    private Integer exitCode;              // Deploy script exit code
    private String host;
    private int port;
    private String username;
    private String filePath;
    private String component;
    private String envId;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime completedAt;
    private java.time.LocalDateTime heartbeatAt;
    private java.util.List<ComponentStatusResponse> components;  // Cached health of each component
}
