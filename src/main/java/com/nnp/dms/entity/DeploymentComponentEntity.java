/**
 * DeploymentComponentEntity.java
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

// JPA entity for per-deployment component health snapshots
// (portal.nnp_dms_deployment_components). Written by the scheduler after each health check.
@Entity
@Table(name = "nnp_dms_deployment_components", schema = "portal")
@Getter
@Setter
@NoArgsConstructor
public class DeploymentComponentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deploymentId;

    private String label;       // Component display label (e.g. "DMS App")
    private String container;   // Docker container backing this component
    private String status;      // RUNNING / STOPPED / ...
    @Column(columnDefinition = "TEXT")
    private String detail;      // Free-form health detail from the remote endpoint

    private LocalDateTime checkedAt;  // When the health snapshot was taken

    public DeploymentComponentEntity(String deploymentId, String label, String container, String status, String detail, LocalDateTime checkedAt) {
        this.deploymentId = deploymentId;
        this.label = label;
        this.container = container;
        this.status = status;
        this.detail = detail;
        this.checkedAt = checkedAt;
    }
}
