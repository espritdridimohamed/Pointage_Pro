package com.pointagepro.legal.repository;

import com.pointagepro.legal.entity.SmigValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmigValueRepository extends JpaRepository<SmigValue, Long> {

    Optional<SmigValue> findByYear(Integer year);
}
