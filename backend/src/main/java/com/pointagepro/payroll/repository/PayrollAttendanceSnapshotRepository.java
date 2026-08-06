package com.pointagepro.payroll.repository;

import com.pointagepro.payroll.entity.PayrollAttendanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only access to the frozen attendance facts of a payroll run. The payroll
 * service must read ONLY from here (never from the mutable attendance_summary).
 */
public interface PayrollAttendanceSnapshotRepository
        extends JpaRepository<PayrollAttendanceSnapshot, Long> {

    List<PayrollAttendanceSnapshot> findByPayrollId(Long payrollId);

    List<PayrollAttendanceSnapshot> findByEmployeeIdAndWorkDateBetween(Long employeeId,
                                                                       LocalDate from,
                                                                       LocalDate to);

    boolean existsByPayrollIdAndEmployeeIdAndWorkDate(Long payrollId, Long employeeId,
                                                      LocalDate workDate);

    /**
     * True when a payroll for (company, period) is frozen — status VALIDATED / APPROVED / PAID.
     * DRAFT / COMPUTED are still regenerable, so corrections remain allowed there
     * (business rules §5.7).
     */
    default boolean isMonthFrozen(Long companyId, int year, int month) {
        return countFrozenMonths(companyId, year, month) > 0;
    }

    /**
     * Native count of frozen payrolls for the period. Counted (not {@code CASE WHEN...THEN TRUE})
     * because the MySQL JDBC driver returns {@code Integer} for that expression, which cannot be
     * mapped to a {@code boolean} scalar.
     */
    @Query(value = "SELECT COUNT(p.id) " +
            "FROM payrolls p JOIN payroll_statuses ps ON ps.id = p.status_id " +
            "WHERE p.company_id = :companyId AND p.period_year = :year AND p.period_month = :month " +
            "AND ps.code IN ('VALIDATED','APPROVED','PAID')", nativeQuery = true)
    long countFrozenMonths(@Param("companyId") Long companyId,
                           @Param("year") int year,
                           @Param("month") int month);
}
