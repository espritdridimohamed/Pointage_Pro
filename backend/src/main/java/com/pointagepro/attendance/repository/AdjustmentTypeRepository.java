package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.AdjustmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdjustmentTypeRepository extends JpaRepository<AdjustmentType, Long> {

    Optional<AdjustmentType> findByCode(String code);
}
