package com.pointagepro.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveAllocationRepository extends JpaRepository<LeaveAllocation, Long> {

    List<LeaveAllocation> findByEmployeeIdAndYear(Long employeeId, int year);

    Optional<LeaveAllocation> findByEmployeeIdAndYearAndLeaveType(Long employeeId, int year, String leaveType);

    List<LeaveAllocation> findByEmployeeIdAndLeaveTypeOrderByYearAsc(Long employeeId, String leaveType);

    @Query("SELECT COALESCE(SUM(la.allocated), 0) FROM LeaveAllocation la WHERE la.employee.id = :employeeId AND la.leaveType = :leaveType")
    long sumAllocatedByEmployeeAndType(@Param("employeeId") Long employeeId, @Param("leaveType") String leaveType);
}
