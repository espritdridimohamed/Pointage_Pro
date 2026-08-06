package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.DependentRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DependentRelationshipRepository extends JpaRepository<DependentRelationship, Long> {
    Optional<DependentRelationship> findByCode(String code);
}
