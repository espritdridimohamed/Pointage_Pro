package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.AttendanceAdjustment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, Long> {

    List<AttendanceAdjustment> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<AttendanceAdjustment> findBySummaryId(Long summaryId);

    List<AttendanceAdjustment> findBySummaryIdAndStatusCode(Long summaryId, String statusCode);

    List<AttendanceAdjustment> findByEmployeeIdAndSummaryIsNull(Long employeeId);

    List<AttendanceAdjustment> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<AttendanceAdjustment> findByCompanyIdAndEmployeeIdOrderByCreatedAtDesc(Long companyId, Long employeeId);

    List<AttendanceAdjustment> findByCompanyIdAndStatusCodeOrderByCreatedAtDesc(Long companyId, String statusCode);

    List<AttendanceAdjustment> findByCompanyIdAndEmployeeIdAndStatusCodeOrderByCreatedAtDesc(
            Long companyId, Long employeeId, String statusCode);

    /**
     * Entity-graph variants: eagerly load the associations needed to map a response DTO after the
     * transaction commits (same convention as {@code AttendanceSummaryRepository}).
     */
    @EntityGraph(attributePaths = {"employee", "adjustmentType", "status", "createdBy", "approvedBy", "summary"})
    @Query("select a from AttendanceAdjustment a where a.id = :id")
    Optional<AttendanceAdjustment> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"employee", "adjustmentType", "status", "createdBy", "approvedBy", "summary"})
    @Query("select a from AttendanceAdjustment a where a.company.id = :companyId order by a.createdAt desc")
    List<AttendanceAdjustment> findWithDetailsByCompanyIdOrderByCreatedAtDesc(@Param("companyId") Long companyId);

    @EntityGraph(attributePaths = {"employee", "adjustmentType", "status", "createdBy", "approvedBy", "summary"})
    @Query("""
            select a from AttendanceAdjustment a
            where a.company.id = :companyId and a.employee.id = :employeeId
            order by a.createdAt desc""")
    List<AttendanceAdjustment> findWithDetailsByCompanyIdAndEmployeeIdOrderByCreatedAtDesc(
            @Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    @EntityGraph(attributePaths = {"employee", "adjustmentType", "status", "createdBy", "approvedBy", "summary"})
    @Query("""
            select a from AttendanceAdjustment a
            where a.company.id = :companyId and a.status.code = :statusCode
            order by a.createdAt desc""")
    List<AttendanceAdjustment> findWithDetailsByCompanyIdAndStatusCodeOrderByCreatedAtDesc(
            @Param("companyId") Long companyId, @Param("statusCode") String statusCode);

    @EntityGraph(attributePaths = {"employee", "adjustmentType", "status", "createdBy", "approvedBy", "summary"})
    @Query("""
            select a from AttendanceAdjustment a
            where a.company.id = :companyId and a.employee.id = :employeeId and a.status.code = :statusCode
            order by a.createdAt desc""")
    List<AttendanceAdjustment> findWithDetailsByCompanyIdAndEmployeeIdAndStatusCodeOrderByCreatedAtDesc(
            @Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
            @Param("statusCode") String statusCode);
}
