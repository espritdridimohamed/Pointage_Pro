package com.pointagepro.organization.repository;

import com.pointagepro.organization.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByCompanyIdOrderByNameAsc(Long companyId);
}
