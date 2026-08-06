package com.pointagepro.contract.repository;

import com.pointagepro.contract.entity.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractStatusRepository extends JpaRepository<ContractStatus, Long> {
    Optional<ContractStatus> findByCode(String code);
}
