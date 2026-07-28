package com.pointagepro.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    Optional<Payroll> findByMonthAndYear(int month, int year);

    boolean existsByMonthAndYear(int month, int year);
}
