/**
 * EnvironmentPlanService.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.service;

import com.nnp.dms.entity.Environment;
import com.nnp.dms.exception.DeployException;
import com.nnp.dms.repository.EnvironmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Lookup and validation of customer environments (portal.nnp_env) and their DMS plans.
@Service
@Slf4j
public class EnvironmentPlanService {

    private final EnvironmentRepository environmentRepository;
    private final EnvActivityLogService activityLogService;

    public EnvironmentPlanService(EnvironmentRepository environmentRepository,
                                  EnvActivityLogService activityLogService) {
        this.environmentRepository = environmentRepository;
        this.activityLogService = activityLogService;
    }

    // Fetch the environment by id or throw a DeployException (=> 502 response).
    public Environment resolveEnvironment(String envId) {
        return environmentRepository.findByEnvId(envId)
                .orElseThrow(() -> new DeployException("Environment not found for envId: " + envId));
    }

    // Validate the environment actually has at least one active DMS plan component.
    // Currently not invoked from DeploymentService (call is commented out).
    public void assertPlanHasDms(String envId, String userId) {
        long count = environmentRepository.countDmsCompsForEnv(envId);
        if (count == 0) {
            String message = "You have no any DMS plan for this environment: " + envId;
            log.warn(message);
            activityLogService.logActivity(envId, message, "FAILED", "DMS_PLAN_CHECK_FAILED", userId);
            throw new DeployException(message);
        }
    }
}
