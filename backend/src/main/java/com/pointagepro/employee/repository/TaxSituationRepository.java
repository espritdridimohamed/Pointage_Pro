package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.TaxSituation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaxSituationRepository extends JpaRepository<TaxSituation, Long> {
    Optional<TaxSituation> findByCode(String code);
}
