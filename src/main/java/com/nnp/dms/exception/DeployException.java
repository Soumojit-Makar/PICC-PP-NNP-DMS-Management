/**
 * DeployException.java
 *
 * @author Soumojit Makar
 * @date 28-Jul-2026
 */
package com.nnp.dms.exception;

// Unchecked exception for all DMS deployment failures. Mapped by DeployController's
// @ExceptionHandler to a 502 BAD_GATEWAY response.
public class DeployException extends RuntimeException {
    public DeployException(String message, Throwable cause) {
        super(message, cause);
    }

    public DeployException(String message) {
        super(message);
    }
}
