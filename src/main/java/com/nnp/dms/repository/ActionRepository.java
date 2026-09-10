/**
 * ActionRepository.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.repository;

import com.nnp.dms.entity.DeploymentAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Spring Data repository for DeploymentAction (portal.nnp_dms_deployment_actions).
public interface ActionRepository extends JpaRepository<DeploymentAction, Long> {
    // Full audit trail of a deployment, newest action first.
    List<DeploymentAction> findByDeploymentIdOrderByPerformedAtDesc(String deploymentId);
}
