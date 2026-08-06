package com.pointagepro.contract.repository;

import com.pointagepro.contract.entity.SalaryComponentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalaryComponentTypeRepository extends JpaRepository<SalaryComponentType, Long> {
    Optional<SalaryComponentType> findByCode(String code);
}
