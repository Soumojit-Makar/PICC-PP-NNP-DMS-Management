/**
 * IDRepo.java
 *
 * @author Soumojit Makar
 * @date 31-Jul-2026
 */
package com.nnp.dms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nnp.dms.entity.EnvActivityLogV2;

// Repository used only to generate activity-log IDs from the shared DB sequence.
// There is no dedicated entity; it borrows EnvActivityLogV2 purely to satisfy JpaRepository.
@Repository
public interface IDRepo extends JpaRepository<EnvActivityLogV2, String> {

    // Pull the next value of portal.env_generic_id_seq, used for act_id ("ACT_<nextval>").
    @Query(value = "SELECT nextval('portal.env_generic_id_seq')", nativeQuery = true)
    Long getNextSeqVal();
}
