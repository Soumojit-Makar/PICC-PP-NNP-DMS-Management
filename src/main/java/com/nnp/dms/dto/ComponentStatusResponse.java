/**
 * ComponentStatusResponse.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// API exposure of one component's health (API DTO for DeploymentComponentEntity).
@Getter
@Setter
public class ComponentStatusResponse {
    private String label;
    private String container;
    private String status;
    private String detail;
    private LocalDateTime checkedAt;
}
