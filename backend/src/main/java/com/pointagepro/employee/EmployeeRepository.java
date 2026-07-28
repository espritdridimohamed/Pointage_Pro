package com.pointagepro.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByMatricule(String matricule);

    boolean existsByMatriculeAndIdNot(String matricule, Long id);

    boolean existsByRfidUid(String rfidUid);

    boolean existsByRfidUidAndIdNot(String rfidUid, Long id);

    Optional<Employee> findByRfidUid(String rfidUid);

    @Query("SELECT e FROM Employee e WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(e.matricule) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.position) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:department IS NULL OR :department = '' OR LOWER(e.department) = LOWER(:department))")
    Page<Employee> search(@Param("search") String search, @Param("department") String department, Pageable pageable);

    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND e.department <> '' ORDER BY e.department")
    List<String> findDistinctDepartments();

    long countByStatus(String status);

    List<Employee> findByStatus(String status);

    List<Employee> findByStatusIn(List<String> statuses);

    @Query("SELECT CAST(SUBSTRING(e.matricule, 5) AS long) FROM Employee e WHERE e.matricule LIKE 'EMP-%' ORDER BY CAST(SUBSTRING(e.matricule, 5) AS long) DESC")
    java.util.Optional<Long> findMaxMatriculeNumber();
}
