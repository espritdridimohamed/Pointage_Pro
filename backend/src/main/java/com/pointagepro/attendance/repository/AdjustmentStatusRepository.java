package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.AdjustmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdjustmentStatusRepository extends JpaRepository<AdjustmentStatus, Long> {

    Optional<AdjustmentStatus> findByCode(String code);
}
