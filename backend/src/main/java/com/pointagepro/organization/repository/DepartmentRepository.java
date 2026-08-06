package com.pointagepro.organization.repository;

import com.pointagepro.organization.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Department> findByCompanyIdAndNameAndValidToIsNull(Long companyId, String name);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);
}
