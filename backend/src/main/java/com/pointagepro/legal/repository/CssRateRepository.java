package com.pointagepro.legal.repository;

import com.pointagepro.legal.entity.CssRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CssRateRepository extends JpaRepository<CssRate, Long> {

    Optional<CssRate> findByYear(Integer year);
}
