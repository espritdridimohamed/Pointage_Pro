package com.pointagepro.organization.repository;

import com.pointagepro.organization.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByCompanyIdOrderByNameAsc(Long companyId);
}
