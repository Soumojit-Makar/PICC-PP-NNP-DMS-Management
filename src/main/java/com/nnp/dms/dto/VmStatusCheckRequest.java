/**
 * VmStatusCheckRequest.java
 *
 * @author Soumojit Makar
 * @date 30-Jul-2026
 */
package com.nnp.dms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /check-status — target VM + credentials for a one-shot health check.
@Getter
@Setter
public class VmStatusCheckRequest {
    @NotBlank private String host;
    @NotNull @Min(1) private Integer port;
    @NotBlank private String username;
    @NotBlank private String privateKey;
    private String passphrase;
}
