/**
 * EnvActivityLogRepoV2.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.repository;

import com.nnp.dms.entity.EnvActivityLogV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Spring Data repository for the environment activity log (portal.nnp_env_activity_log).
@Repository
public interface EnvActivityLogRepoV2 extends JpaRepository<EnvActivityLogV2, String> {

    List<EnvActivityLogV2> findByEnvId(String envId);

    // Activity history for an environment, newest entries first.
    List<EnvActivityLogV2> findByEnvIdOrderByActDateDesc(String envId);
}
