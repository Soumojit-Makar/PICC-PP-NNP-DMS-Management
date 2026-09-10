/**
 * ContainerRepository.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.repository;

import com.nnp.dms.entity.DeploymentContainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Spring Data repository for DeploymentContainer (portal.nnp_dms_deployment_containers).
public interface ContainerRepository extends JpaRepository<DeploymentContainer, Long> {
    List<DeploymentContainer> findByDeploymentId(String deploymentId);
    // Used when refreshing: drop the old snapshot before persisting the new one.
    void deleteByDeploymentId(String deploymentId);
}
