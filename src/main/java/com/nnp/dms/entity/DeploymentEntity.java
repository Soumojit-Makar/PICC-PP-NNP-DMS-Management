/**
 * DeploymentEntity.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.ZoneId;

// JPA entity for a DMS deployment request (portal.nnp_dms_deployments).
// Holds the async deployment lifecycle state plus the target VM connection details.
// Note: privateKey/passphrase columns exist in the table but are intentionally left null —
// credentials live only in the in-memory SshKeyCacheService.
@Entity
@Table(name = "nnp_dms_deployments", schema = "portal")
@Getter
@Setter
@NoArgsConstructor
public class DeploymentEntity {

    @Id
    private String id;                     // UUID generated at submission

    @Column(nullable = false)
    private String status;                 // PENDING / RUNNING / DEPLOYED / SUCCESS / DEGRADED / FAILED

    @Column(columnDefinition = "TEXT")
    private String message;                // Human-readable status message / last error

    private Integer exitCode;              // Exit code of the remote deploy script
    private String host;                   // Target VM hostname/IP
    private int port;                      // SSH port (default 22)
    private String username;               // SSH login user

    @Column(columnDefinition = "TEXT")
    private String privateKey;             // Not persisted with a real value (see class comment)

    private String passphrase;             // Passphrase for the private key (never persisted)
    private String filePath;               // Remote directory used for the deploy checkout
    private String component;              // Deployment component name ("dms")
    private String envId;                  // Customer environment id (portal.nnp_env)
    private LocalDateTime createdAt;       // When the request was submitted
    private LocalDateTime startedAt;       // When the async worker started
    private LocalDateTime completedAt;     // When the deploy/health flow reached a terminal state
    private LocalDateTime heartbeatAt;     // Last liveness update (health checks, deploy stages)

    public DeploymentEntity(String id, String status, String host, int port, String username,
                            String privateKey, String passphrase, String filePath) {
        this.id = id;
        this.status = status;
        this.host = host;
        this.port = port;
        this.username = username;
        this.privateKey = privateKey;
        this.passphrase = passphrase;
        this.filePath = filePath;
        this.createdAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
