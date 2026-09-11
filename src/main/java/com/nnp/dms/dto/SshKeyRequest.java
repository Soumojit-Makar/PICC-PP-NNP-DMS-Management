/**
 * SshKeyRequest.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /deployments/{id}/ssh-key — re-enter an SSH key for a deployment.
@Getter
@Setter
public class SshKeyRequest {
    @NotBlank
    private String privateKey;   // The new private key to cache
    private String passphrase;   // Optional passphrase for the key
}
