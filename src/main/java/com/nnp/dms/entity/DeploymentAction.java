/**
 * DeploymentAction.java
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

// JPA entity for the per-deployment audit trail of container operations
// (portal.nnp_dms_deployment_actions). Records every restart / exec / remove.
@Entity
@Table(name = "nnp_dms_deployment_actions", schema = "portal")
@Getter
@Setter
@NoArgsConstructor
public class DeploymentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deploymentId;      // Owning deployment

    @Column(nullable = false)
    private String actionType;        // RESTART / EXEC / REMOVE

    private String containerName;     // Container involved (null for generic EXEC)

    @Column(columnDefinition = "TEXT")
    private String command;           // The full shell command that was run

    @Column(columnDefinition = "TEXT")
    private String output;            // Captured output of the command

    private Integer exitCode;         // Exit code of the command

    @Column(nullable = false)
    private LocalDateTime performedAt;// When the action happened

    public DeploymentAction(String deploymentId, String actionType, String containerName, String command, String output, Integer exitCode, LocalDateTime performedAt) {
        this.deploymentId = deploymentId;
        this.actionType = actionType;
        this.containerName = containerName;
        this.command = command;
        this.output = output;
        this.exitCode = exitCode;
        this.performedAt = performedAt;
    }
}
