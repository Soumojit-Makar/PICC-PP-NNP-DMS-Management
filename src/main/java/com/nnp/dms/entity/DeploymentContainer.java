/**
 * DeploymentContainer.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

// JPA entity representing a Docker container running on the deployment's VM
// (portal.nnp_dms_deployment_containers). Refreshed by ContainerService.refreshContainers().
@Entity
@Table(name = "nnp_dms_deployment_containers", schema = "portal")
@Getter
@Setter
@NoArgsConstructor
public class DeploymentContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deploymentId;      // Owning deployment (logical FK -> nnp_dms_deployments)

    @Column(nullable = false)
    private String containerName;     // Docker container name

    private String appName;           // Docker image name:tag

    private String status;            // Container status string from `docker ps`

    private LocalDateTime checkedAt;  // When this snapshot was taken

    public DeploymentContainer(String deploymentId, String containerName, String appName, String status, LocalDateTime checkedAt) {
        this.deploymentId = deploymentId;
        this.containerName = containerName;
        this.appName = appName;
        this.status = status;
        this.checkedAt = checkedAt;
    }
}
