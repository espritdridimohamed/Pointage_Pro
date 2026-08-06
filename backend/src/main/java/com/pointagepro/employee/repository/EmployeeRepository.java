package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByCompanyIdOrderByLastNameAsc(Long companyId);

    Optional<Employee> findByMatriculeAndCompanyId(String matricule, Long companyId);

    Optional<Employee> findByRfidUid(String rfidUid);

    boolean existsByMatriculeAndCompanyId(String matricule, Long companyId);

    boolean existsByRfidUid(String rfidUid);

    boolean existsByMatriculeAndCompanyIdAndIdNot(String matricule, Long companyId, Long id);

    boolean existsByRfidUidAndIdNot(String rfidUid, Long id);

    long countByCompanyId(Long companyId);

    long countByDepartmentId(Long departmentId);

    long countByPositionId(Long positionId);

    long countByLocationId(Long locationId);

    @Query("""
            select e from Employee e
            where e.company.id = :companyId
              and (:search is null
                   or lower(e.firstName) like lower(concat('%', :search, '%'))
                   or lower(e.lastName) like lower(concat('%', :search, '%'))
                   or lower(e.matricule) like lower(concat('%', :search, '%')))
              and (:departmentId is null or e.department.id = :departmentId)
            order by e.createdAt desc""")
    Page<Employee> searchEmployees(@Param("companyId") Long companyId,
                                   @Param("search") String search,
                                   @Param("departmentId") Long departmentId,
                                   Pageable pageable);

    @Query("""
            select distinct d.name from Employee e join e.department d
            where e.company.id = :companyId and d.name is not null order by d.name""")
    List<String> findDistinctDepartmentNames(@Param("companyId") Long companyId);
}
