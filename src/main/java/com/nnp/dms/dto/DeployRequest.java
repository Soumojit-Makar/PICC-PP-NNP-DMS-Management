/**
 * DeployRequest.java
 *
 * @author Soumojit Makar
 * @date 29-Jul-2026
 */
package com.nnp.dms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /create-dms — everything needed to deploy DMS on the remote VM.
@Getter
@Setter
public class DeployRequest {
    @NotBlank
    String envId;          // Customer environment id (must exist in portal.nnp_env)
    @NotBlank
    String host;           // Target VM hostname/IP
    @NotNull
    @Min(1) Integer port;  // SSH port
    @NotBlank String username;    // SSH login user
    @NotBlank String privateKey;  // SSH private key (cached in memory, never stored)
    String passphrase;            // Optional passphrase for the key
    @NotBlank String filePath;    // Remote checkout dir for the deploy script
    @NotBlank String password;    // ADM_IN_PASS passed to deploy-dms.sh
}
