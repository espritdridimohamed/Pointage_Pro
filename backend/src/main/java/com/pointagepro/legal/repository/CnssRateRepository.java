package com.pointagepro.legal.repository;

import com.pointagepro.legal.entity.CnssRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CnssRateRepository extends JpaRepository<CnssRate, Long> {

    Optional<CnssRate> findByYear(Integer year);
}
