package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.DayType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DayTypeRepository extends JpaRepository<DayType, Long> {

    Optional<DayType> findByCode(String code);
}
