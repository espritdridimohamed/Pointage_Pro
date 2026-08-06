package com.pointagepro.employee.service;

import com.pointagepro.attendance.repository.EmployeeScheduleRepository;
import com.pointagepro.attendance.repository.WorkScheduleRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.contract.repository.SalaryComponentRepository;
import com.pointagepro.contract.service.ContractService;
import com.pointagepro.employee.dto.EmployeeRequest;
import com.pointagepro.employee.dto.EmployeeResponse;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.entity.EmployeeAssignment;
import com.pointagepro.employee.entity.EmployeeStatus;
import com.pointagepro.employee.entity.Gender;
import com.pointagepro.employee.entity.MaritalStatus;
import com.pointagepro.employee.repository.EmployeeAssignmentRepository;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.employee.repository.EmployeeStatusRepository;
import com.pointagepro.employee.repository.GenderRepository;
import com.pointagepro.employee.repository.MaritalStatusRepository;
import com.pointagepro.leave.entity.LeaveBalance;
import com.pointagepro.leave.entity.LeaveType;
import com.pointagepro.leave.repository.LeaveBalanceRepository;
import com.pointagepro.leave.repository.LeaveTypeRepository;
import com.pointagepro.organization.entity.Department;
import com.pointagepro.organization.entity.Location;
import com.pointagepro.organization.entity.Position;
import com.pointagepro.organization.repository.DepartmentRepository;
import com.pointagepro.organization.repository.LocationRepository;
import com.pointagepro.organization.repository.PositionRepository;
import com.pointagepro.schedule.service.ScheduleService;
import com.pointagepro.shared.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final LocalDate HIRING = LocalDate.of(2026, 1, 1);

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeStatusRepository statusRepository;
    @Mock private GenderRepository genderRepository;
    @Mock private MaritalStatusRepository maritalStatusRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EmployeeAssignmentRepository assignmentRepository;
    @Mock private EmployeeContractRepository contractRepository;
    @Mock private SalaryComponentRepository componentRepository;
    @Mock private WorkScheduleRepository scheduleRepository;
    @Mock private EmployeeScheduleRepository employeeScheduleRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeaveBalanceRepository balanceRepository;
    @Mock private ContractService contractService;
    @Mock private ScheduleService scheduleService;

    @InjectMocks private EmployeeService service;

    private Company company;
    private EmployeeStatus active;
    private EmployeeStatus terminated;
    private Department dept;
    private Position pos;
    private Location loc;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        active = status("ACTIVE");
        terminated = status("TERMINATED");
        lenient().when(statusRepository.findByCode("ACTIVE")).thenReturn(Optional.of(active));
        lenient().when(statusRepository.findByCode("TERMINATED")).thenReturn(Optional.of(terminated));

        Gender male = new Gender();
        male.setId(1L);
        male.setCode("M");
        lenient().when(genderRepository.findByCode("M")).thenReturn(Optional.of(male));
        MaritalStatus married = new MaritalStatus();
        married.setId(1L);
        married.setCode("MARIE");
        lenient().when(maritalStatusRepository.findByCode("MARIE")).thenReturn(Optional.of(married));

        dept = new Department();
        dept.setId(5L);
        dept.setCompany(company);
        dept.setName("Comptabilite");
        pos = new Position();
        pos.setId(6L);
        pos.setCompany(company);
        pos.setName("Comptable");
        loc = new Location();
        loc.setId(7L);
        loc.setCompany(company);
        loc.setName("Siege");
        loc.setIsActive(true);

        LeaveType annual = leaveType("ANNUAL", new BigDecimal("18"));
        LeaveType maternity = leaveType("MATERNITY", new BigDecimal("90"));
        LeaveType paternity = leaveType("PATERNITY", null);
        lenient().when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(annual));
        lenient().when(leaveTypeRepository.findByCode("MATERNITY")).thenReturn(Optional.of(maternity));
        lenient().when(leaveTypeRepository.findByCode("PATERNITY")).thenReturn(Optional.of(paternity));

        lenient().when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee em = inv.getArgument(0);
            if (em.getId() == null) {
                em.setId(1L);
            }
            return em;
        });
        lenient().when(assignmentRepository.save(any(EmployeeAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(balanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(contractRepository.findFirstByEmployeeIdAndStatusCodeOrderByStartDateDesc(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(anyLong()))
                .thenReturn(List.of());
        lenient().when(employeeScheduleRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(scheduleRepository.findFirstByCompanyIdAndIsDefaultTrue(anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void createTranslatesFlatPayload() {
        when(employeeRepository.countByCompanyId(1L)).thenReturn(0L);
        when(employeeRepository.existsByMatriculeAndCompanyId("EMP-001", 1L)).thenReturn(false);
        when(departmentRepository.findByCompanyIdAndNameAndValidToIsNull(1L, "Comptabilite"))
                .thenReturn(Optional.of(dept));
        when(positionRepository.findByCompanyIdAndNameAndValidToIsNull(1L, "Comptable"))
                .thenReturn(Optional.of(pos));
        when(locationRepository.findById(7L)).thenReturn(Optional.of(loc));

        EmployeeRequest req = new EmployeeRequest();
        req.setFirstName("Ali");
        req.setLastName("Trabelsi");
        req.setHiringDate(HIRING);
        req.setStatus("ACTIF");
        req.setGender("M");
        req.setMaritalStatus("MARIE");
        req.setDepartment("Comptabilite");
        req.setPosition("Comptable");
        req.setLocationId(7L);
        req.setContractType("CDI");
        req.setBaseSalary(new BigDecimal("1500"));
        req.setPrimeTransport(new BigDecimal("50"));
        req.setPrimePerformance(new BigDecimal("100"));
        req.setWeeklySchedule("STD");
        req.setAnnualLeaveDays(new BigDecimal("18"));
        req.setMaternityLeaveDays(new BigDecimal("90"));

        EmployeeResponse response = service.create(company, req, null);

        assertThat(response.getMatricule()).isEqualTo("EMP-001");
        assertThat(response.getStatus()).isEqualTo("ACTIF");
        assertThat(response.getDepartment()).isEqualTo("Comptabilite");
        assertThat(response.getGender()).isEqualTo("M");
        assertThat(response.getAnnualLeaveDays()).isEqualByComparingTo("18");
        verify(assignmentRepository).save(argThat(a ->
                a.getDepartment() == dept && a.getPosition() == pos && a.getLocation() == loc
                        && a.getValidFrom().equals(HIRING)));
        verify(contractService).createAutoContract(eq(company), argThat(e -> e.getId() == 1L),
                eq("CDI"), eq(HIRING), eq(null));
        verify(scheduleService).assignByCode(eq(company), argThat(e -> e.getId() == 1L),
                eq("STD"), eq(HIRING));
        verify(balanceRepository, times(2)).save(any(LeaveBalance.class));
    }

    @Test
    void createRejectsUnknownDepartmentName() {
        when(employeeRepository.countByCompanyId(1L)).thenReturn(0L);
        when(employeeRepository.existsByMatriculeAndCompanyId("EMP-001", 1L)).thenReturn(false);
        when(departmentRepository.findByCompanyIdAndNameAndValidToIsNull(1L, "Inconnu"))
                .thenReturn(Optional.empty());

        EmployeeRequest req = new EmployeeRequest();
        req.setFirstName("Ali");
        req.setLastName("Trabelsi");
        req.setDepartment("Inconnu");
        req.setHiringDate(HIRING);

        assertThatThrownBy(() -> service.create(company, req, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsDuplicateMatricule() {
        when(employeeRepository.existsByMatriculeAndCompanyId("EMP-X", 1L)).thenReturn(true);

        EmployeeRequest req = new EmployeeRequest();
        req.setFirstName("Ali");
        req.setLastName("Trabelsi");
        req.setMatricule("EMP-X");
        req.setHiringDate(HIRING);

        assertThatThrownBy(() -> service.create(company, req, null))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createRejectsDuplicateRfid() {
        when(employeeRepository.countByCompanyId(1L)).thenReturn(0L);
        when(employeeRepository.existsByMatriculeAndCompanyId("EMP-001", 1L)).thenReturn(false);
        when(employeeRepository.existsByRfidUid("A1B2")).thenReturn(true);

        EmployeeRequest req = new EmployeeRequest();
        req.setFirstName("Ali");
        req.setLastName("Trabelsi");
        req.setRfidUid("A1B2");
        req.setHiringDate(HIRING);

        assertThatThrownBy(() -> service.create(company, req, null))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateToActiveClearsExitDate() {
        Employee e = employee();
        e.setStatus(terminated);
        e.setExitDate(LocalDate.of(2026, 7, 1));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(e));

        EmployeeRequest req = new EmployeeRequest();
        req.setStatus("ACTIF");
        req.setFirstName("Ali");
        req.setLastName("Trabelsi");

        EmployeeResponse response = service.update(company, 1L, req, null);

        assertThat(e.getExitDate()).isNull();
        assertThat(response.getStatus()).isEqualTo("ACTIF");
    }

    @Test
    void terminateClosesOpenContract() {
        Employee e = employee();
        e.setStatus(active);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(e));

        service.terminate(company, 1L);

        assertThat(e.getExitDate()).isEqualTo(LocalDate.now());
        assertThat(e.getStatus()).isSameAs(terminated);
        verify(contractService).closeActiveContract(e, LocalDate.now());
    }

    @Test
    void updatePlacementChangeClosesOpenAssignment() {
        Employee e = employee();
        e.setDepartment(dept);
        e.setStatus(active);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(e));

        Department newDept = new Department();
        newDept.setId(6L);
        newDept.setCompany(company);
        newDept.setName("Ressources Humaines");
        when(departmentRepository.findByCompanyIdAndNameAndValidToIsNull(1L, "Ressources Humaines"))
                .thenReturn(Optional.of(newDept));

        EmployeeAssignment open = new EmployeeAssignment();
        open.setId(1L);
        open.setEmployee(e);
        open.setDepartment(dept);
        open.setValidFrom(LocalDate.of(2026, 1, 1));
        open.setValidTo(null);
        when(assignmentRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(1L))
                .thenReturn(Optional.of(open));

        EmployeeRequest req = new EmployeeRequest();
        req.setDepartment("Ressources Humaines");

        service.update(company, 1L, req, null);

        assertThat(open.getValidTo()).isEqualTo(LocalDate.now().minusDays(1));
        ArgumentCaptor<EmployeeAssignment> captor = ArgumentCaptor.forClass(EmployeeAssignment.class);
        verify(assignmentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(a ->
                a.getDepartment() == newDept && a.getValidFrom().equals(LocalDate.now()));
    }

    @Test
    void listFiltersByDepartmentAndSearch() {
        when(departmentRepository.findByCompanyIdAndNameAndValidToIsNull(1L, "Comptabilite"))
                .thenReturn(Optional.of(dept));
        Employee e = employee();
        e.setStatus(active);
        PageRequest pageable = PageRequest.of(0, 20);
        when(employeeRepository.searchEmployees(1L, "Ali", 5L, pageable))
                .thenReturn(new PageImpl<>(List.of(e)));

        Page<EmployeeResponse> page = service.list(company, " Ali ", "Comptabilite", pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getMatricule()).isEqualTo("EMP-001");
    }

    // ------------------------------------------------------------- helpers

    private Employee employee() {
        Employee e = new Employee();
        e.setId(1L);
        e.setCompany(company);
        e.setMatricule("EMP-001");
        e.setFirstName("Ali");
        e.setLastName("Trabelsi");
        e.setHiringDate(HIRING);
        return e;
    }

    private EmployeeStatus status(String code) {
        EmployeeStatus s = new EmployeeStatus();
        s.setId((long) code.hashCode());
        s.setCode(code);
        s.setLabel(code);
        return s;
    }

    private LeaveType leaveType(String code, BigDecimal defaultDays) {
        LeaveType t = new LeaveType();
        t.setId((long) code.hashCode());
        t.setCode(code);
        t.setDefaultDaysPerYear(defaultDays);
        return t;
    }
}
