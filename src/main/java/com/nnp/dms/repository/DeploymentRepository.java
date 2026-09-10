/**
 * DeploymentRepository.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.repository;

import com.nnp.dms.entity.DeploymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// Spring Data repository for DeploymentEntity (portal.nnp_dms_deployments).
public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {
    // Used by the scheduler to find deployments still awaiting health checks.
    List<DeploymentEntity> findByStatusIn(List<String> statuses);
    // Default listing: newest deployments first.
    Page<DeploymentEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    // Filter by component only.
    Page<DeploymentEntity> findByComponent(String component, Pageable pageable);
    // Filter by component + one of several statuses.
    Page<DeploymentEntity> findByComponentAndStatusIn(String component, List<String> statuses, Pageable pageable);
    // Filter by one of several statuses only.
    Page<DeploymentEntity> findByStatusIn(List<String> statuses, Pageable pageable);
}
