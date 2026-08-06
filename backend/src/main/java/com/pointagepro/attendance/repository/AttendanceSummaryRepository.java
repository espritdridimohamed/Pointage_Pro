package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.AttendanceSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {

    Optional<AttendanceSummary> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceSummary> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(Long employeeId,
                                                                                LocalDate from,
                                                                                LocalDate to);

    List<AttendanceSummary> findByCompanyIdAndWorkDateBetweenOrderByWorkDateAsc(Long companyId,
                                                                               LocalDate from,
                                                                               LocalDate to);

    long countByScheduleId(Long scheduleId);

    /**
     * Eagerly loads the associations needed to map a summary to its response DTO after the
     * transaction commits. {@code @Query} is used so the method name is not parsed as a
     * derived query. {@code attributePaths} is a String[] — one path per element.
     */
    @EntityGraph(attributePaths = {"employee.company", "status", "dayType", "schedule", "computedBy"})
    @Query("select s from AttendanceSummary s where s.id = :id")
    Optional<AttendanceSummary> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"employee.company", "status", "dayType", "schedule", "computedBy"})
    @Query("select s from AttendanceSummary s where s.employee.id = :employeeId and s.workDate = :workDate")
    Optional<AttendanceSummary> findWithDetailsByEmployeeIdAndWorkDate(@Param("employeeId") Long employeeId,
                                                                      @Param("workDate") LocalDate workDate);

    @EntityGraph(attributePaths = {"employee.company", "status", "dayType", "schedule", "computedBy"})
    @Query("""
            select s from AttendanceSummary s
            where s.employee.id = :employeeId and s.workDate between :from and :to
            order by s.workDate asc""")
    List<AttendanceSummary> findWithDetailsByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
