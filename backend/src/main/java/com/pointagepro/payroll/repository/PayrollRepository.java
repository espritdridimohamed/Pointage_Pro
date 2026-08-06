package com.pointagepro.payroll.repository;

import com.pointagepro.payroll.entity.Payroll;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    Optional<Payroll> findByCompanyIdAndPeriodYearAndPeriodMonth(Long companyId, int periodYear, int periodMonth);

    @EntityGraph(attributePaths = {"company", "status", "createdBy", "approvedBy"})
    @Query("""
            select p from Payroll p
            where p.company.id = :companyId
              and (:year is null or p.periodYear = :year)
              and (:month is null or p.periodMonth = :month)
              and (:statusCode is null or p.status.code = :statusCode)
            order by p.periodYear desc, p.periodMonth desc, p.id desc""")
    List<Payroll> findScoped(@Param("companyId") Long companyId,
                             @Param("year") Integer year,
                             @Param("month") Integer month,
                             @Param("statusCode") String statusCode);

    @EntityGraph(attributePaths = {"company", "status", "createdBy", "approvedBy"})
    @Query("select p from Payroll p where p.id = :id")
    Optional<Payroll> findWithDetailsById(@Param("id") Long id);
}
