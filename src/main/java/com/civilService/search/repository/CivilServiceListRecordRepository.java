package com.civilService.search.repository;

import com.civilService.search.entity.CivilServiceListRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CivilServiceListRecordRepository extends JpaRepository<CivilServiceListRecord, Long> {

    Optional<CivilServiceListRecord> findByExamNoAndListNoAndListAgencyCodeAndFirstNameAndLastName(
            String examNo, BigDecimal listNo, String listAgencyCode, String firstName, String lastName
    );

    java.util.List<CivilServiceListRecord> findByExamNo(String examNo);

    List<CivilServiceListRecord> findByExamNoIn(Collection<String> examNos);

    long countByStatus(String status);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM CivilServiceListRecord r WHERE r.status = :status")
    void deleteByStatus(@org.springframework.data.repository.query.Param("status") String status);
}
