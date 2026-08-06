package com.pointagepro.leave.repository;

import com.pointagepro.leave.entity.LeaveRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveRequestStatusRepository extends JpaRepository<LeaveRequestStatus, Long> {

    Optional<LeaveRequestStatus> findByCode(String code);
}
