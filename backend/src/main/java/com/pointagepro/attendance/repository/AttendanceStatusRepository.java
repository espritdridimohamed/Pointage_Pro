package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendanceStatusRepository extends JpaRepository<AttendanceStatus, Long> {

    Optional<AttendanceStatus> findByCode(String code);
}
