package com.pointagepro.organization.service;

import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeAssignmentRepository;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.organization.dto.DepartmentRequest;
import com.pointagepro.organization.entity.Department;
import com.pointagepro.organization.entity.Location;
import com.pointagepro.organization.entity.Position;
import com.pointagepro.organization.repository.DepartmentRepository;
import com.pointagepro.organization.repository.LocationRepository;
import com.pointagepro.organization.repository.PositionRepository;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import com.pointagepro.terminal.repository.TerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private DepartmentRepository departmentRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository employeeAssignmentRepository;
    @Mock private EmployeeContractRepository employeeContractRepository;
    @Mock private TerminalRepository terminalRepository;

    @InjectMocks private OrganizationService service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        lenient().when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(positionRepository.save(any(Position.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createDepartmentRejectsDuplicateActiveName() {
        Department existing = department(2L, "Comptabilité");
        when(departmentRepository.findByCompanyIdOrderByNameAsc(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createDepartment(company, departmentRequest("comptabilité", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createDepartmentRejectsDuplicateCode() {
        when(departmentRepository.existsByCompanyIdAndCode(1L, "COMPTA")).thenReturn(true);

        assertThatThrownBy(() -> service.createDepartment(company, departmentRequest("Finance", "COMPTA")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createDepartmentAllowsNameOfClosedDepartment() {
        Department closed = department(2L, "Ancien");
        closed.setValidTo(LocalDate.of(2025, 12, 31));
        when(departmentRepository.findByCompanyIdOrderByNameAsc(1L)).thenReturn(List.of(closed));

        var response = service.createDepartment(company, departmentRequest("Ancien", null));

        assertThat(response.getName()).isEqualTo("Ancien");
    }

    @Test
    void createDepartmentRejectsManagerFromAnotherCompany() {
        when(departmentRepository.findByCompanyIdOrderByNameAsc(1L)).thenReturn(List.of());
        Employee other = new Employee();
        other.setId(99L);
        Company otherCompany = new Company();
        otherCompany.setId(2L);
        other.setCompany(otherCompany);
        when(employeeRepository.findById(99L)).thenReturn(Optional.of(other));

        DepartmentRequest req = departmentRequest("Compta", null);
        req.setManagerEmployeeId(99L);

        assertThatThrownBy(() -> service.createDepartment(company, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteDepartmentReferencedByEmployeeIsRejected() {
        Department d = department(1L, "Compta");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(employeeRepository.countByDepartmentId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteDepartment(company, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteDepartmentWithoutReferencesDeletes() {
        Department d = department(1L, "Compta");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(d));
        when(employeeRepository.countByDepartmentId(1L)).thenReturn(0L);
        when(positionRepository.countByDepartmentId(1L)).thenReturn(0L);
        when(employeeAssignmentRepository.countByDepartmentId(1L)).thenReturn(0L);

        service.deleteDepartment(company, 1L);

        verify(departmentRepository).delete(d);
    }

    @Test
    void deleteLocationReferencedByTerminalIsRejected() {
        Location l = new Location();
        l.setId(1L);
        l.setCompany(company);
        l.setName("Usine");
        l.setIsActive(true);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(l));
        when(terminalRepository.countByLocationId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteLocation(company, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateDepartmentAllowsActiveNameNotUsedByOtherActiveRow() {
        Department d = department(1L, "Compta");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(d));
        Department closed = department(2L, "Compta");
        closed.setValidTo(LocalDate.of(2025, 12, 31));
        when(departmentRepository.findByCompanyIdOrderByNameAsc(1L)).thenReturn(List.of(d, closed));

        DepartmentRequest req = departmentRequest("Compta", null);
        req.setValidFrom(LocalDate.of(2026, 1, 1));

        var response = service.updateDepartment(company, 1L, req);

        assertThat(response.getName()).isEqualTo("Compta");
    }

    // ------------------------------------------------------------- helpers

    private Department department(long id, String name) {
        Department d = new Department();
        d.setId(id);
        d.setCompany(company);
        d.setName(name);
        d.setValidFrom(LocalDate.of(2026, 1, 1));
        return d;
    }

    private DepartmentRequest departmentRequest(String name, String code) {
        DepartmentRequest req = new DepartmentRequest();
        req.setName(name);
        req.setCode(code);
        return req;
    }
}
