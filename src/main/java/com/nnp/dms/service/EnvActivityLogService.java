/**
 * EnvActivityLogService.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.dto.ActivityLogRequest;
import com.nnp.dms.entity.EnvActivityLogV2;
import com.nnp.dms.repository.EnvActivityLogRepoV2;
import com.nnp.dms.repository.IDRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// Append-only activity trail writer for environments (portal.nnp_env_activity_log).
// IDs are generated as ACT_<nextval> from the shared portal.env_generic_id_seq sequence.
@Service
@Slf4j
public class EnvActivityLogService {

    private final EnvActivityLogRepoV2 envActivityLogRepoV2;
    private final IDRepo idRepo;

    public EnvActivityLogService(EnvActivityLogRepoV2 envActivityLogRepoV2, IDRepo idRepo) {
        this.envActivityLogRepoV2 = envActivityLogRepoV2;
        this.idRepo = idRepo;
    }

    // Build an activity row (ID from sequence, timestamp = now) and insert it.
    public void logActivity(String envId, String actDesc, String actStatus, String actNote, String userId) {
        EnvActivityLogV2 envActivityLogV2 = new EnvActivityLogV2();
        envActivityLogV2.setActId("ACT_" + idRepo.getNextSeqVal());
        envActivityLogV2.setEnvId(envId);
        envActivityLogV2.setActDate(LocalDateTime.now());
        envActivityLogV2.setActDesc(actDesc);
        envActivityLogV2.setActStatus(actStatus);
        envActivityLogV2.setActNote(actNote);
        envActivityLogV2.setUserId(userId);

        // saveAndFlush so the sequence/id is committed immediately.
        envActivityLogRepoV2.saveAndFlush(envActivityLogV2);
        log.info("Activity logged for env {}: {}", envId, actNote);
    }

    // Convenience overload accepting the DTO form directly.
    public void logActivity(ActivityLogRequest req) {
        logActivity(req.getEnvId(), req.getActDesc(), req.getActStatus(), req.getActNote(), req.getUserId());
    }

    // Full history of an environment, newest first.
    public List<EnvActivityLogV2> getActivityLogByEnvId(String envId) {
        return envActivityLogRepoV2.findByEnvIdOrderByActDateDesc(envId);
    }
}
