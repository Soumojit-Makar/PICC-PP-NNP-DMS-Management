/**
 * EnvironmentRepository.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.repository;

import com.nnp.dms.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, String> {

    // Look up one environment by its id (used for validation + deploy-script params).
    Optional<Environment> findByEnvId(String envId);

    // Native query that counts active DMS plan components belonging to this environment.
    // Traverses plan components -> plans -> account plans -> accounts to scope by env_id,
    // matching components whose name contains "dms" (case-insensitive).
    @Query(value = "SELECT COUNT(*) FROM portal.env_bbcomp b " +
            "JOIN portal.nnp_plan_comp pc ON pc.env_compid = b.env_compid " +
            "JOIN portal.nnp_plan p ON p.host_planid = pc.host_plid " +
            "JOIN portal.nnp_account_plan ap ON ap.plan_id = p.host_planid " +
            "JOIN portal.nnp_account a ON a.acc_id = ap.acc_id " +
            "WHERE a.env_id = :envId AND ap.active = true AND LOWER(b.env_compname) LIKE '%dms%'",
            nativeQuery = true)
    long countDmsCompsForEnv(@Param("envId") String envId);
}
