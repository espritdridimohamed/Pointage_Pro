package com.pointagepro.organization.repository;

import com.pointagepro.organization.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Position> findByCompanyIdAndNameAndValidToIsNull(Long companyId, String name);

    Optional<Position> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);

    long countByDepartmentId(Long departmentId);
}
