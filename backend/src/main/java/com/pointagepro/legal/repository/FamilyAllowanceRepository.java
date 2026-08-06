package com.pointagepro.legal.repository;

import com.pointagepro.legal.entity.FamilyAllowance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyAllowanceRepository extends JpaRepository<FamilyAllowance, Long> {

    Optional<FamilyAllowance> findByYear(Integer year);
}
