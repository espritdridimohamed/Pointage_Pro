package com.pointagepro.leave.repository;

import com.pointagepro.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, Integer year);

    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, Integer year);
}
