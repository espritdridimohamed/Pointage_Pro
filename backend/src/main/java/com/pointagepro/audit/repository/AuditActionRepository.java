package com.pointagepro.audit.repository;

import com.pointagepro.audit.entity.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditActionRepository extends JpaRepository<AuditAction, Long> {
    Optional<AuditAction> findByCode(String code);
}
