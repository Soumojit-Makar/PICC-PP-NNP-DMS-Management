/**
 * RefreshRequest.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.Getter;
import lombok.Setter;

// Request body for POST /deployments/{id}/refresh-containers.
@Getter
@Setter
public class RefreshRequest {
    private String privateKey;   // Optional fresh key (also refreshes the cache)
    private String passphrase;
}
