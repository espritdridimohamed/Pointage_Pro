package com.pointagepro.contract.service;

import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.dto.ContractRequest;
import com.pointagepro.contract.dto.ContractResponse;
import com.pointagepro.contract.dto.SalaryComponentRequest;
import com.pointagepro.contract.entity.ContractStatus;
import com.pointagepro.contract.entity.ContractType;
import com.pointagepro.contract.entity.EmployeeContract;
import com.pointagepro.contract.entity.SalaryComponent;
import com.pointagepro.contract.entity.SalaryComponentType;
import com.pointagepro.contract.entity.SalaryHistory;
import com.pointagepro.contract.repository.ContractStatusRepository;
import com.pointagepro.contract.repository.ContractTypeRepository;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.contract.repository.SalaryComponentRepository;
import com.pointagepro.contract.repository.SalaryComponentTypeRepository;
import com.pointagepro.contract.repository.SalaryHistoryRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.organization.repository.LocationRepository;
import com.pointagepro.payroll.repository.PayrollItemRepository;
import com.pointagepro.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock private EmployeeContractRepository contractRepository;
    @Mock private SalaryComponentRepository componentRepository;
    @Mock private SalaryHistoryRepository salaryHistoryRepository;
    @Mock private ContractTypeRepository contractTypeRepository;
    @Mock private ContractStatusRepository contractStatusRepository;
    @Mock private SalaryComponentTypeRepository componentTypeRepository;
    @Mock private PayrollItemRepository payrollItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationRepository locationRepository;

    @InjectMocks private ContractService service;

    private Company company;
    private Employee employee;
    private ContractType cdi;
    private ContractStatus active;
    private ContractStatus expired;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        employee = new Employee();
        employee.setId(10L);
        employee.setCompany(company);
        employee.setFirstName("Ahmed");
        employee.setLastName("Ben Salah");
        cdi = new ContractType();
        cdi.setId(1L);
        cdi.setCode("CDI");
        cdi.setLabel("CDI");
        active = new ContractStatus();
        active.setId(1L);
        active.setCode("ACTIVE");
        active.setLabel("Active");
        expired = new ContractStatus();
        expired.setId(2L);
        expired.setCode("EXPIRED");
        expired.setLabel("Expired");
        lenient().when(contractTypeRepository.findByCode("CDI")).thenReturn(Optional.of(cdi));
        lenient().when(contractStatusRepository.findByCode("ACTIVE")).thenReturn(Optional.of(active));
        lenient().when(contractStatusRepository.findByCode("EXPIRED")).thenReturn(Optional.of(expired));
        lenient().when(contractRepository.save(any(EmployeeContract.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(componentRepository.save(any(SalaryComponent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(salaryHistoryRepository.save(any(SalaryHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createActiveContractAutoClosesOverlappingExistingActive() {
        EmployeeContract existing = contract(9L);
        existing.setStartDate(LocalDate.of(2026, 1, 1));
        existing.setEndDate(null);
        existing.setStatus(active);
        when(contractRepository.findByEmployeeIdOrderByStartDateDesc(10L)).thenReturn(List.of(existing));

        ContractResponse response = service.createContract(company, employee,
                request("CDI", "ACTIVE", LocalDate.of(2026, 6, 1), null), null);

        assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(existing.getStatus()).isSameAs(expired);
        assertThat(response.getStatusCode()).isEqualTo("ACTIVE");
        assertThat(response.getEmployeeName()).isEqualTo("Ahmed Ben Salah");
    }

    @Test
    void createActiveContractBackDatedOverExistingActiveIsRejected() {
        EmployeeContract existing = contract(9L);
        existing.setStartDate(LocalDate.of(2026, 6, 1));
        existing.setEndDate(null);
        existing.setStatus(active);
        when(contractRepository.findByEmployeeIdOrderByStartDateDesc(10L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createContract(company, employee,
                request("CDI", "ACTIVE", LocalDate.of(2026, 5, 1), null), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createExpiredContractDoesNotTouchExistingActive() {
        EmployeeContract existing = contract(9L);
        existing.setStartDate(LocalDate.of(2026, 1, 1));
        existing.setEndDate(null);
        existing.setStatus(active);

        service.createContract(company, employee,
                request("CDI", "EXPIRED", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31)), null);

        assertThat(existing.getEndDate()).isNull();
        assertThat(existing.getStatus()).isSameAs(active);
        verify(contractRepository, org.mockito.Mockito.never()).save(existing);
    }

    @Test
    void deleteContractReferencedByComponentsIsRejected() {
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract(1L)));
        when(componentRepository.countByContractId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteContract(company, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteContractWithoutReferencesDeletes() {
        EmployeeContract c = contract(1L);
        when(contractRepository.findById(1L)).thenReturn(Optional.of(c));
        when(componentRepository.countByContractId(1L)).thenReturn(0L);
        when(payrollItemRepository.countByContractId(1L)).thenReturn(0L);

        service.deleteContract(company, 1L);

        verify(contractRepository).delete(c);
    }

    @Test
    void createBaseComponentClosesPreviousBaseAndWritesHistory() {
        EmployeeContract c = contract(5L);
        when(contractRepository.findById(5L)).thenReturn(Optional.of(c));

        SalaryComponentType baseType = componentType("BASE_SALARY", "BASE");
        when(componentTypeRepository.findByCode("BASE_SALARY")).thenReturn(Optional.of(baseType));

        SalaryComponent existing = new SalaryComponent();
        existing.setId(1L);
        existing.setContract(c);
        existing.setComponentType(baseType);
        existing.setLabel("Salaire de base");
        existing.setAmount(new BigDecimal("1000"));
        existing.setStartDate(LocalDate.of(2026, 1, 1));
        existing.setEndDate(null);
        existing.setIsActive(true);
        when(componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(5L))
                .thenReturn(List.of(existing));

        SalaryComponentRequest req = new SalaryComponentRequest();
        req.setComponentTypeCode("BASE_SALARY");
        req.setLabel("Salaire de base");
        req.setAmount(new BigDecimal("1500"));
        req.setIsPercentage(false);
        req.setStartDate(LocalDate.of(2026, 6, 1));

        service.createComponent(company, 5L, req, null);

        assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        verify(salaryHistoryRepository).save(argThat(h ->
                h.getOldAmount() != null && h.getOldAmount().compareTo(new BigDecimal("1000")) == 0
                        && h.getNewAmount() != null && h.getNewAmount().compareTo(new BigDecimal("1500")) == 0));
    }

    @Test
    void createBaseComponentBackDatedIsRejected() {
        EmployeeContract c = contract(5L);
        when(contractRepository.findById(5L)).thenReturn(Optional.of(c));
        SalaryComponentType baseType = componentType("BASE_SALARY", "BASE");
        when(componentTypeRepository.findByCode("BASE_SALARY")).thenReturn(Optional.of(baseType));
        SalaryComponent existing = new SalaryComponent();
        existing.setId(1L);
        existing.setComponentType(baseType);
        existing.setAmount(new BigDecimal("1000"));
        existing.setStartDate(LocalDate.of(2026, 6, 1));
        existing.setEndDate(null);
        existing.setIsActive(true);
        when(componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(5L))
                .thenReturn(List.of(existing));

        SalaryComponentRequest req = new SalaryComponentRequest();
        req.setComponentTypeCode("BASE_SALARY");
        req.setLabel("Salaire de base");
        req.setAmount(new BigDecimal("1500"));
        req.setIsPercentage(false);
        req.setStartDate(LocalDate.of(2026, 5, 1));

        assertThatThrownBy(() -> service.createComponent(company, 5L, req, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void applyFlatSalaryCreatesAutoContractBaseAndPrimes() {
        when(contractRepository.findFirstByEmployeeIdAndStatusCodeOrderByStartDateDesc(10L, "ACTIVE"))
                .thenReturn(Optional.empty());
        SalaryComponentType baseType = componentType("BASE_SALARY", "BASE");
        when(componentTypeRepository.findByCode("BASE_SALARY")).thenReturn(Optional.of(baseType));
        SalaryComponentType transportType = componentType("PRIME_TRANSPORT", "ALLOWANCE");
        lenient().when(componentTypeRepository.findByCode("PRIME_TRANSPORT")).thenReturn(Optional.of(transportType));
        SalaryComponentType rendementType = componentType("PRIME_RENDEMENT", "BONUS");
        lenient().when(componentTypeRepository.findByCode("PRIME_RENDEMENT")).thenReturn(Optional.of(rendementType));

        service.applyFlatSalary(company, employee, new BigDecimal("1500"), new BigDecimal("50"),
                new BigDecimal("100"), null, LocalDate.of(2026, 1, 1), null);

        verify(contractRepository).save(argThat(c -> c.getContractType().getCode().equals("CDI")
                && c.getStatus().getCode().equals("ACTIVE")
                && c.getStartDate().equals(LocalDate.of(2026, 1, 1))));
        ArgumentCaptor<SalaryComponent> captor = ArgumentCaptor.forClass(SalaryComponent.class);
        verify(componentRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(sc ->
                "BASE_SALARY".equals(sc.getComponentType().getCode())
                        && sc.getAmount().compareTo(new BigDecimal("1500")) == 0);
        assertThat(captor.getAllValues()).anyMatch(sc ->
                "PRIME_TRANSPORT".equals(sc.getComponentType().getCode())
                        && sc.getAmount().compareTo(new BigDecimal("50")) == 0);
        assertThat(captor.getAllValues()).anyMatch(sc ->
                "PRIME_RENDEMENT".equals(sc.getComponentType().getCode())
                        && sc.getAmount().compareTo(new BigDecimal("100")) == 0);
        verify(salaryHistoryRepository).save(argThat(h ->
                h.getOldAmount() == null && h.getNewAmount() != null
                        && h.getNewAmount().compareTo(new BigDecimal("1500")) == 0));
    }

    @Test
    void applyFlatSalaryUpdatesExistingBaseAmountAndWritesHistory() {
        EmployeeContract c = contract(5L);
        c.setStatus(active);
        when(contractRepository.findFirstByEmployeeIdAndStatusCodeOrderByStartDateDesc(10L, "ACTIVE"))
                .thenReturn(Optional.of(c));
        SalaryComponentType baseType = componentType("BASE_SALARY", "BASE");
        when(componentTypeRepository.findByCode("BASE_SALARY")).thenReturn(Optional.of(baseType));

        SalaryComponent existingBase = new SalaryComponent();
        existingBase.setId(1L);
        existingBase.setContract(c);
        existingBase.setComponentType(baseType);
        existingBase.setAmount(new BigDecimal("1200"));
        existingBase.setStartDate(LocalDate.of(2026, 1, 1));
        existingBase.setEndDate(null);
        existingBase.setIsActive(true);
        when(componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(5L))
                .thenReturn(List.of(existingBase));

        service.applyFlatSalary(company, employee, new BigDecimal("1500"), null, null, null,
                LocalDate.of(2026, 8, 1), null);

        assertThat(existingBase.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        verify(salaryHistoryRepository).save(argThat(h ->
                h.getOldAmount() != null && h.getOldAmount().compareTo(new BigDecimal("1200")) == 0
                        && h.getNewAmount() != null && h.getNewAmount().compareTo(new BigDecimal("1500")) == 0));
    }

    @Test
    void closeActiveContractClosesOpenActiveOnExit() {
        EmployeeContract c = contract(5L);
        c.setStartDate(LocalDate.of(2026, 1, 1));
        c.setEndDate(null);
        c.setStatus(active);
        when(contractRepository.findByEmployeeIdOrderByStartDateDesc(10L)).thenReturn(List.of(c));

        service.closeActiveContract(employee, LocalDate.of(2026, 8, 6));

        assertThat(c.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(c.getStatus()).isSameAs(expired);
    }

    // ------------------------------------------------------------- helpers

    private EmployeeContract contract(long id) {
        EmployeeContract c = new EmployeeContract();
        c.setId(id);
        c.setEmployee(employee);
        c.setCompany(company);
        c.setContractType(cdi);
        c.setStatus(active);
        c.setWorkingHoursPerDay(new BigDecimal("8.00"));
        c.setWorkingDaysPerWeek(5);
        return c;
    }

    private ContractRequest request(String type, String status, LocalDate start, LocalDate end) {
        ContractRequest req = new ContractRequest();
        req.setContractTypeCode(type);
        req.setStatusCode(status);
        req.setStartDate(start);
        req.setEndDate(end);
        return req;
    }

    private SalaryComponentType componentType(String code, String category) {
        SalaryComponentType t = new SalaryComponentType();
        t.setId((long) code.hashCode());
        t.setCode(code);
        t.setLabel(code);
        t.setCategory(category);
        return t;
    }
}
