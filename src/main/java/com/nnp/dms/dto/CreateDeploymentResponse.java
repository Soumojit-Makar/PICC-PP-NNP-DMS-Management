/**
 * CreateDeploymentResponse.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

// 202 Accepted body for POST /create-dms — the deployment id and initial status.
@Getter
@Setter
public class CreateDeploymentResponse {
    private String id;       // UUID of the new deployment
    private String status;   // "PENDING"
    private String location; // URL to poll deployment status

    public CreateDeploymentResponse() {}

    public CreateDeploymentResponse(String id, String status) {
        this.id = id;
        this.status = status;
    }
}
