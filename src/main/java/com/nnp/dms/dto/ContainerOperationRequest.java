/**
 * ContainerOperationRequest.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Request body for restart/remove container endpoints. privateKey/passphrase are optional —
// if omitted the cached SSH key for the deployment is used.
@Getter
@Setter
public class ContainerOperationRequest {
    @NotBlank
    private String containerName;  // Docker container to operate on

    private String privateKey;     // Optional fresh key (also refreshes the cache)
    private String passphrase;
}
