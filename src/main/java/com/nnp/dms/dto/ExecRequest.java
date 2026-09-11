/**
 * ExecRequest.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /deployments/{id}/exec — an arbitrary command to run on the VM.
@Getter
@Setter
public class ExecRequest {
    @NotBlank
    private String command;      // Shell command to execute remotely

    private String privateKey;   // Optional fresh key (also refreshes the cache)
    private String passphrase;
}
