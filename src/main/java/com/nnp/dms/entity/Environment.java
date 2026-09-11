/**
 * Environment.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// JPA entity for a customer environment (portal.nnp_env).
// EnvCustId is the acting customer id used for auditing and passed to the deploy script as ADMIN_USER.
@Entity
@Table(name = "nnp_env", schema = "portal")
@Getter
@Setter
public class Environment {

    @Id
    @Column(name = "env_id", nullable = false)
    private String envId;         // Environment unique id (referenced by deployment.envId)

    @Column(name = "env_code", nullable = false)
    private String envCode;       // Human-readable environment code

    @Column(name = "env_custid", nullable = false)
    private String envCustId;     // Customer id -> acting user (ADMIN_USER)

    @Column(name = "env_email")
    private String envEmail;      // Contact email for this environment (passed to deploy script)
}
