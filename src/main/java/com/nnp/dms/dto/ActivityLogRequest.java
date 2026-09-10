/**
 * ActivityLogRequest.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

// DTO form of an environment activity log entry (used by EnvActivityLogService.logActivity).
@Getter
@Setter
public class ActivityLogRequest {
    private String envId;
    private String actDesc;
    private String actStatus;
    private String actNote;
    private String userId;
}
