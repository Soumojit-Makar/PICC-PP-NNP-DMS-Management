/**
 * ComponentStatusRepository.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.repository;

import com.nnp.dms.entity.DeploymentComponentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Spring Data repository for DeploymentComponentEntity (portal.nnp_dms_deployment_components).
public interface ComponentStatusRepository extends JpaRepository<DeploymentComponentEntity, Long> {
    List<DeploymentComponentEntity> findByDeploymentId(String deploymentId);
    // Replaces the previous health snapshot for a deployment.
    void deleteByDeploymentId(String deploymentId);
}
