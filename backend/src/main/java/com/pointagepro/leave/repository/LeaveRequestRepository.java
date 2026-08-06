package com.pointagepro.leave.repository;

import com.pointagepro.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"employee", "leaveType", "status", "employee.department"})
    @Query("""
            select lr from LeaveRequest lr
            where lr.employee.company.id = :companyId
              and (:employeeId is null or lr.employee.id = :employeeId)
              and (:statusCode is null or lr.status.code = :statusCode)
            order by lr.createdAt desc
            """)
    List<LeaveRequest> findScoped(@Param("companyId") Long companyId,
                                  @Param("employeeId") Long employeeId,
                                  @Param("statusCode") String statusCode);

    @EntityGraph(attributePaths = {"employee", "leaveType", "status", "employee.department"})
    Optional<LeaveRequest> findWithDetailsById(Long id);

    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    List<LeaveRequest> findByEmployeeIdAndStatus_Code(Long employeeId, String statusCode);

    List<LeaveRequest> findByEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
            Long employeeId, LocalDate endDate, LocalDate startDate);

    @EntityGraph(attributePaths = {"leaveType"})
    List<LeaveRequest> findByEmployeeIdAndStatus_CodeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
            Long employeeId, String statusCode, LocalDate endDate, LocalDate startDate);

    boolean existsByEmployeeIdAndStatusCodeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, String statusCode, LocalDate endDate, LocalDate startDate);

    boolean existsByEmployeeIdAndStatus_CodeInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, List<String> statusCodes, LocalDate endDate, LocalDate startDate);
}
