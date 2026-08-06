package com.pointagepro.shared.approval.repository;

import com.pointagepro.shared.approval.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalStatusRepository extends JpaRepository<ApprovalStatus, Long> {
    Optional<ApprovalStatus> findByCode(String code);
}
