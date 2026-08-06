package com.pointagepro.contract.repository;

import com.pointagepro.contract.entity.SalaryComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {

    List<SalaryComponent> findByContractIdOrderByStartDateDesc(Long contractId);

    List<SalaryComponent> findByContractIdAndIsActiveTrueOrderByStartDateDesc(Long contractId);
}
