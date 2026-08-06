package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.Gender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenderRepository extends JpaRepository<Gender, Long> {
    Optional<Gender> findByCode(String code);
}
