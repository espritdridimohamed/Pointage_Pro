package com.pointagepro.contract.repository;

import com.pointagepro.contract.entity.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Long> {
    Optional<ContractType> findByCode(String code);
}
