package com.pointagepro.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveRequest> findAllByOrderByCreatedAtDesc();

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.employee WHERE lr.id = :id")
    java.util.Optional<LeaveRequest> findByIdWithEmployee(@Param("id") Long id);

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.employee ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findAllWithEmployee();

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.status = :status")
    long countByEmployeeIdAndStatus(@Param("employeeId") Long employeeId, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(DATEDIFF(lr.endDate, lr.startDate) + 1), 0) FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.leaveType = :leaveType AND lr.status = 'APPROVED'")
    long sumApprovedDaysByEmployeeAndType(@Param("employeeId") Long employeeId, @Param("leaveType") String leaveType);

    @Query("SELECT COALESCE(SUM(DATEDIFF(lr.endDate, lr.startDate) + 1), 0) FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.leaveType = :leaveType AND lr.status = 'PENDING'")
    long sumPendingDaysByEmployeeAndType(@Param("employeeId") Long employeeId, @Param("leaveType") String leaveType);

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.status = 'PENDING'")
    long countPending();

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.status = 'APPROVED'")
    long countApproved();

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.status = 'REFUSED'")
    long countRefused();

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.employee e WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.matricule) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR lr.status = :status) AND " +
           "(:leaveType IS NULL OR :leaveType = '' OR lr.leaveType = :leaveType) " +
           "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> searchDesc(@Param("search") String search, @Param("status") String status, @Param("leaveType") String leaveType);

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.employee e WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.matricule) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR lr.status = :status) AND " +
           "(:leaveType IS NULL OR :leaveType = '' OR lr.leaveType = :leaveType) " +
           "ORDER BY lr.createdAt ASC")
    List<LeaveRequest> searchAsc(@Param("search") String search, @Param("status") String status, @Param("leaveType") String leaveType);

    @Query("SELECT COALESCE(SUM(DATEDIFF(lr.endDate, lr.startDate) + 1), 0) FROM LeaveRequest lr WHERE lr.status = 'APPROVED' AND lr.leaveType = :leaveType AND lr.startDate <= :end AND lr.endDate >= :start")
    long sumApprovedDaysByRangeAndType(@Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end, @Param("leaveType") String leaveType);

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.status = 'APPROVED' AND lr.startDate <= :today AND lr.endDate >= :today")
    long countActiveLeavesByEmployee(@Param("employeeId") Long employeeId, @Param("today") java.time.LocalDate today);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.status = 'APPROVED' AND lr.startDate <= :end AND lr.endDate >= :start")
    List<LeaveRequest> findApprovedLeavesInRange(@Param("employeeId") Long employeeId, @Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end);

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.employee WHERE lr.status = 'APPROVED' AND lr.startDate <= :end AND lr.endDate >= :start")
    List<LeaveRequest> findAllApprovedLeavesInRange(@Param("start") java.time.LocalDate start, @Param("end") java.time.LocalDate end);

    @Query("SELECT CASE WHEN COUNT(lr) > 0 THEN true ELSE false END FROM LeaveRequest lr WHERE lr.employee.id = :employeeId AND lr.status = :status AND lr.startDate <= :date AND lr.endDate >= :date")
    boolean existsByEmployeeIdAndStatusInRange(@Param("employeeId") Long employeeId, @Param("status") String status, @Param("date") java.time.LocalDate date);
}
