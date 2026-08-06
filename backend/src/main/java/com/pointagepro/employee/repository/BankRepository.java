package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {
    Optional<Bank> findByCode(String code);
}
