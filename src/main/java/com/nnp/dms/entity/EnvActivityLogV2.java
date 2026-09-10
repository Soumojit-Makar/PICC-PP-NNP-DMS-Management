/**
 * EnvActivityLogV2.java
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

import java.io.Serializable;
import java.time.LocalDateTime;

// JPA entity for the append-only environment activity trail (portal.nnp_env_activity_log).
// Each entry records a lifecycle event for an environment (deploy submitted, health check, etc.).
@Entity
@Table(name = "nnp_env_activity_log", schema = "portal")
@Getter
@Setter
public class EnvActivityLogV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "act_id", nullable = false)
    private String actId;                  // "ACT_<nextval>" from portal.env_generic_id_seq

    @Column(name = "env_id", nullable = false)
    private String envId;                  // Environment this log entry belongs to

    @Column(name = "act_date")
    private LocalDateTime actDate;         // When the event occurred

    @Column(name = "act_desc")
    private String actDesc;                // Human-readable description of the event

    @Column(name = "act_status")
    private String actStatus;              // e.g. PENDING / RUNNING / FAILED / SUCCESS

    @Column(name = "act_note")
    private String actNote;                // Event type code (e.g. DMS_DEPLOY_SUBMITTED)

    @Column(name = "user_id")
    private String userId;                 // Acting user (env_custid)
}
