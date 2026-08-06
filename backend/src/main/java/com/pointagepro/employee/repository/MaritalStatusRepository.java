package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.MaritalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaritalStatusRepository extends JpaRepository<MaritalStatus, Long> {
    Optional<MaritalStatus> findByCode(String code);
}
