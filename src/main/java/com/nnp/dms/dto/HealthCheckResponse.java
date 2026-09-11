/**
 * HealthCheckResponse.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

// Deserialization model for the JSON returned by the remote VM's /status endpoint.
@Getter
@Setter
public class HealthCheckResponse {
    private String overall;                    // healthy / degraded / ...
    private String checked_at;                 // When the VM service checked itself
    private Map<String, Integer> summary;      // Aggregated counts by status
    private List<HealthComponent> components;  // Per-component detail

    @Getter
    @Setter
    public static class HealthComponent {
        private String label;      // Display label, e.g. "DMS App"
        private String container;  // Docker container backing the component
        private String status;     // RUNNING / STOPPED / ...
        private String detail;     // Extra detail/message
    }
}
