package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.HolidayType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HolidayTypeRepository extends JpaRepository<HolidayType, Long> {

    Optional<HolidayType> findByCode(String code);
}
