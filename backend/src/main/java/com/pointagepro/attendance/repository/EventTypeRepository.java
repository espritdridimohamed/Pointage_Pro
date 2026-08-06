package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventTypeRepository extends JpaRepository<EventType, Long> {

    Optional<EventType> findByCode(String code);
}
