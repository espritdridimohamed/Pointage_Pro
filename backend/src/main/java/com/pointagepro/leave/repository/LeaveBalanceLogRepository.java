package com.pointagepro.leave.repository;

import com.pointagepro.leave.entity.LeaveBalanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveBalanceLogRepository extends JpaRepository<LeaveBalanceLog, Long> {

    List<LeaveBalanceLog> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveBalanceLog> findByRefTypeAndRefIdOrderByCreatedAtAsc(String refType, Long refId);
}
