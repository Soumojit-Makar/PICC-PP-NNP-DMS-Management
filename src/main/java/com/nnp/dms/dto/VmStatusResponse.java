/**
 * VmStatusResponse.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

// Response for a VM health check: overall verdict + per-component statuses.
@Getter
@Setter
public class VmStatusResponse {
    private String host;                    // Checked host
    private String overall;                 // healthy / degraded / unreachable / parse_error / unknown
    private LocalDateTime checkedAt;        // When the check ran
    private List<ComponentStatusResponse> components;  // Per-component health snapshot
}
