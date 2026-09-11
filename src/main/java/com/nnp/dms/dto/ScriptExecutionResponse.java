/**
 * ScriptExecutionResponse.java
 *
 * @author Soumojit Makar
 * @date 29-Jul-2026
 */
package com.nnp.dms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Generic result of a remote command/deploy operation (success flag + output + exit code).
// Also used as the 502 error body when a DeployException occurs.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScriptExecutionResponse {

    private boolean success;     // Whether the command exited with code 0
    private String message;      // Human-readable outcome
    private String remoteOutput; // Captured stdout(+stderr) from the remote host
    private int exitCode;        // Remote process exit code
}
