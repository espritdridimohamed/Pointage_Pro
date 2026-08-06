package com.pointagepro.leave.service;

import com.pointagepro.attendance.entity.Holiday;
import com.pointagepro.attendance.repository.HolidayRepository;
import com.pointagepro.attendance.service.AttendanceEngineService;
import com.pointagepro.audit.service.AuditService;
import com.pointagepro.auth.entity.Role;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.leave.dto.LeaveBalanceResponse;
import com.pointagepro.leave.dto.LeaveResponse;
import com.pointagepro.leave.entity.LeaveBalance;
import com.pointagepro.leave.entity.LeaveBalanceLog;
import com.pointagepro.leave.entity.LeaveRequest;
import com.pointagepro.leave.entity.LeaveRequestStatus;
import com.pointagepro.leave.entity.LeaveType;
import com.pointagepro.leave.repository.LeaveBalanceLogRepository;
import com.pointagepro.leave.repository.LeaveBalanceRepository;
import com.pointagepro.leave.repository.LeaveRequestRepository;
import com.pointagepro.leave.repository.LeaveRequestStatusRepository;
import com.pointagepro.leave.repository.LeaveTypeRepository;
import com.pointagepro.organization.entity.Department;
import com.pointagepro.payroll.repository.PayrollAttendanceSnapshotRepository;
import com.pointagepro.shared.approval.entity.Approval;
import com.pointagepro.shared.approval.entity.ApprovalStatus;
import com.pointagepro.shared.approval.repository.ApprovalRepository;
import com.pointagepro.shared.approval.repository.ApprovalStatusRepository;
import com.pointagepro.shared.approval.service.ApprovalAuthority;
import com.pointagepro.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long EMPLOYEE_ID = 10L;
    private static final long MANAGER_EMPLOYEE_ID = 50L;
    private static final LocalDate START = LocalDate.of(2026, 8, 10);   // Monday
    private static final LocalDate END = LocalDate.of(2026, 8, 14);     // Friday -> 5 working days

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private LeaveRequestStatusRepository leaveRequestStatusRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveBalanceRepository balanceRepository;
    @Mock
    private LeaveBalanceLogRepository balanceLogRepository;
    @Mock
    private ApprovalRepository approvalRepository;
    @Mock
    private ApprovalStatusRepository approvalStatusRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private PayrollAttendanceSnapshotRepository payrollSnapshotRepository;
    @Mock
    private AttendanceEngineService engineService;
    @Mock
    private AuditService auditService;
    @Spy
    private ApprovalAuthority approvalAuthority = new ApprovalAuthority();

    @InjectMocks
    private LeaveService service;

    private Company company;
    private User hrActor;
    private User managerActor;
    private List<Approval> lastSavedChain;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        hrActor = user(3L, null, "HR");
        managerActor = user(7L, MANAGER_EMPLOYEE_ID, "MANAGER");
        seedStatuses();
    }

    private void seedStatuses() {
        lenient().when(leaveRequestStatusRepository.findByCode("PENDING")).thenReturn(Optional.of(lrStatus("PENDING")));
        lenient().when(leaveRequestStatusRepository.findByCode("APPROVED")).thenReturn(Optional.of(lrStatus("APPROVED")));
        lenient().when(leaveRequestStatusRepository.findByCode("REJECTED")).thenReturn(Optional.of(lrStatus("REJECTED")));
        lenient().when(leaveRequestStatusRepository.findByCode("CANCELLED")).thenReturn(Optional.of(lrStatus("CANCELLED")));
        lenient().when(approvalStatusRepository.findByCode("PENDING")).thenReturn(Optional.of(approvalStatus("PENDING")));
        lenient().when(approvalStatusRepository.findByCode("APPROVED")).thenReturn(Optional.of(approvalStatus("APPROVED")));
        lenient().when(approvalStatusRepository.findByCode("REJECTED")).thenReturn(Optional.of(approvalStatus("REJECTED")));
        lenient().when(approvalStatusRepository.findByCode("CANCELLED")).thenReturn(Optional.of(approvalStatus("CANCELLED")));
    }

    private static Role role(String code) {
        Role r = new Role();
        r.setCode(code);
        return r;
    }

    private static User user(long id, Long employeeId, String... roleCodes) {
        User u = new User();
        u.setId(id);
        u.setEmployeeId(employeeId);
        u.setFullName("user" + id);
        u.setRoles(Set.of(java.util.Arrays.stream(roleCodes).map(LeaveServiceTest::role).toArray(Role[]::new)));
        return u;
    }

    private static Department department(Long managerEmployeeId) {
        Department d = new Department();
        d.setManagerEmployeeId(managerEmployeeId);
        return d;
    }

    private static Employee employee(long id, Long managerEmployeeId) {
        Employee e = new Employee();
        e.setId(id);
        e.setCompany(companyRef());
        e.setFirstName("Ahmed");
        e.setLastName("Ben Salah");
        if (managerEmployeeId != null) {
            e.setDepartment(department(managerEmployeeId));
        }
        return e;
    }

    private static Company companyRef() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        return c;
    }

    private static LeaveRequestStatus lrStatus(String code) {
        LeaveRequestStatus s = new LeaveRequestStatus();
        s.setCode(code);
        s.setLabel(code);
        return s;
    }

    private static ApprovalStatus approvalStatus(String code) {
        ApprovalStatus s = new ApprovalStatus();
        s.setCode(code);
        return s;
    }

    private static LeaveType leaveType(String code, BigDecimal defaultDays) {
        LeaveType t = new LeaveType();
        t.setId(code.equals("ANNUAL") ? 1L : 2L);
        t.setCode(code);
        t.setName(code.toLowerCase());
        t.setIsActive(true);
        t.setDefaultDaysPerYear(defaultDays);
        return t;
    }

    private static LeaveRequest request(long id, Employee employee, String statusCode, User createdBy) {
        LeaveRequest r = new LeaveRequest();
        r.setId(id);
        r.setEmployee(employee);
        r.setLeaveType(leaveType("ANNUAL", new BigDecimal("18")));
        r.setStartDate(START);
        r.setEndDate(END);
        r.setDaysRequested(new BigDecimal("5"));
        r.setStatus(lrStatus(statusCode));
        r.setCreatedBy(createdBy);
        return r;
    }

    private static Approval step(long id, int order, String role, String statusCode) {
        Approval s = new Approval();
        s.setId(id);
        s.setRequestType(LeaveService.REQUEST_TYPE);
        s.setRequestId(1L);
        s.setStepOrder(order);
        s.setApproverRole(role);
        s.setStatus(approvalStatus(statusCode));
        return s;
    }

    private void stubSaveReturnsSameInstance() {
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubSaveAllCapturesChain() {
        when(approvalRepository.saveAll(anyList())).thenAnswer(inv -> {
            lastSavedChain = new ArrayList<>(inv.getArgument(0));
            return lastSavedChain;
        });
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                eq(LeaveService.REQUEST_TYPE), eq(1L))).thenAnswer(inv -> lastSavedChain);
    }

    private void stubCreateBasics() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID)));
        when(userRepository.findByEmployeeId(MANAGER_EMPLOYEE_ID)).thenReturn(Optional.of(managerActor));
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(leaveType("ANNUAL", new BigDecimal("18"))));
        lenient().when(holidayRepository.findByCompanyIdAndHolidayDateBetweenOrderByHolidayDateAsc(
                eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        lenient().when(userRepository.getReferenceById(hrActor.getId())).thenReturn(hrActor);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        stubSaveAllCapturesChain();
    }

    @Test
    void userCreateOwnLeaveMaterializesChainAndComputesWorkingDays() {
        User userActor = user(9L, EMPLOYEE_ID, "USER");
        stubCreateBasics();
        when(userRepository.getReferenceById(userActor.getId())).thenReturn(userActor);

        LeaveResponse response = service.create(company, userActor, EMPLOYEE_ID, "ANNUAL",
                START, END, "vacances", null);

        assertThat(response.getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getDaysRequested()).isEqualByComparingTo("5");
        assertThat(response.getApprovals()).hasSize(2);
        assertThat(response.getApprovals().get(0).getApproverRole()).isEqualTo("MANAGER");
        assertThat(response.getApprovals().get(0).getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals().get(1).getApproverRole()).isEqualTo("HR");
        assertThat(response.getApprovals().get(1).getStatusCode()).isEqualTo("PENDING");
        verify(auditService).log(eq("CREATE"), eq(COMPANY_ID), eq(userActor.getId()),
                eq("LEAVE_REQUEST"), eq(1L), eq(null), anyString());
    }

    @Test
    void createExcludesCompanyHolidaysFromWorkingDays() {
        User userActor = user(9L, EMPLOYEE_ID, "USER");
        stubCreateBasics();
        when(userRepository.getReferenceById(userActor.getId())).thenReturn(userActor);
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(LocalDate.of(2026, 8, 11));
        when(holidayRepository.findByCompanyIdAndHolidayDateBetweenOrderByHolidayDateAsc(
                eq(COMPANY_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(holiday));

        LeaveResponse response = service.create(company, userActor, EMPLOYEE_ID, "ANNUAL",
                START, END, "vacances", null);

        assertThat(response.getDaysRequested()).isEqualByComparingTo("4");
    }

    @Test
    void hrCanCreateForAnyEmployee() {
        stubCreateBasics();
        when(userRepository.getReferenceById(hrActor.getId())).thenReturn(hrActor);

        LeaveResponse response = service.create(company, hrActor, EMPLOYEE_ID, "ANNUAL",
                START, END, null, null);

        assertThat(response.getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals()).hasSize(2);
        assertThat(response.getApprovals().get(1).getApproverRole()).isEqualTo("HR");
        assertThat(response.getApprovals().get(1).getStatusCode()).isEqualTo("PENDING");
    }

    @Test
    void userCannotCreateForAnotherEmployee() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID)));
        User otherUser = user(9L, 99L, "USER");

        assertThatThrownBy(() -> service.create(company, otherUser, EMPLOYEE_ID, "ANNUAL",
                START, END, "vacances", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createOverlapRejected() {
        User userActor = user(9L, EMPLOYEE_ID, "USER");
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID)));
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(leaveType("ANNUAL", new BigDecimal("18"))));
        when(leaveRequestRepository.existsByEmployeeIdAndStatus_CodeInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(EMPLOYEE_ID), anyList(), eq(END), eq(START))).thenReturn(true);

        assertThatThrownBy(() -> service.create(company, userActor, EMPLOYEE_ID, "ANNUAL",
                START, END, "vacances", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsUnknownType() {
        User userActor = user(9L, EMPLOYEE_ID, "USER");
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID)));
        when(leaveTypeRepository.findByCode("FOO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(company, userActor, EMPLOYEE_ID, "FOO",
                START, END, "vacances", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsInactiveType() {
        User userActor = user(9L, EMPLOYEE_ID, "USER");
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID)));
        LeaveType inactive = leaveType("ANNUAL", new BigDecimal("18"));
        inactive.setIsActive(false);
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(company, userActor, EMPLOYEE_ID, "ANNUAL",
                START, END, "vacances", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsSpanTooLong() {
        User userActor = user(9L, EMPLOYEE_ID, "USER");
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID)));
        when(leaveTypeRepository.findByCode("ANNUAL")).thenReturn(Optional.of(leaveType("ANNUAL", new BigDecimal("18"))));

        assertThatThrownBy(() -> service.create(company, userActor, EMPLOYEE_ID, "ANNUAL",
                LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 31), "vacances", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void managerApproveThenHrApproveDebitsAndRecomputes() {
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(approvalRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        when(userRepository.getReferenceById(managerActor.getId())).thenReturn(managerActor);
        when(userRepository.getReferenceById(hrActor.getId())).thenReturn(hrActor);
        when(balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(EMPLOYEE_ID), eq(1L), eq(2026)))
                .thenReturn(Optional.empty());
        when(balanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceLogRepository.save(any(LeaveBalanceLog.class))).thenAnswer(inv -> inv.getArgument(0));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        Approval hrStep = step(12L, 2, "HR", "PENDING");
        var pendingStepsAfterDecisions = new ArrayList<>(List.of(managerStep, hrStep));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                eq(LeaveService.REQUEST_TYPE), eq(1L)))
                .thenAnswer(inv -> pendingStepsAfterDecisions);
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(Optional.of(managerStep));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of(hrStep));

        LeaveResponse afterManager = service.approve(company, managerActor, 1L, "ok");

        assertThat(afterManager.getStatusCode()).isEqualTo("PENDING");
        verify(engineService, never()).recompute(any(), any(), any(), any(), any(), anyString());
        verify(balanceRepository, never()).save(any());

        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(Optional.of(hrStep));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of());

        LeaveResponse afterHr = service.approve(company, hrActor, 1L, "ok");

        assertThat(afterHr.getStatusCode()).isEqualTo("APPROVED");
        verify(engineService).recompute(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(START), eq(END),
                eq(hrActor), contains("leave:1"));
        verify(balanceRepository, org.mockito.Mockito.times(2)).save(any(LeaveBalance.class));
        verify(balanceLogRepository).save(any(LeaveBalanceLog.class));
        assertThat(afterHr.getApprovals()).allMatch(s -> s.getStatusCode().equals("APPROVED"));
    }

    @Test
    void approveInsufficientBalanceRejectedAndStaysPending() {
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        LeaveBalance balance = new LeaveBalance();
        balance.setEntitlementDays(new BigDecimal("18"));
        balance.setTakenDays(new BigDecimal("16"));
        when(balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(EMPLOYEE_ID), eq(1L), eq(2026)))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.approve(company, managerActor, 1L, "ok"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(approvalRepository, never()).save(any(Approval.class));
        verify(engineService, never()).recompute(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void approveFrozenMonthRejected() {
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(company, managerActor, 1L, "ok"))
                .isInstanceOf(ConflictException.class);
        verify(approvalRepository, never()).save(any(Approval.class));
    }

    @Test
    void approveByNonAssignedManagerDenied() {
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));
        User otherManager = user(8L, 77L, "MANAGER");

        assertThatThrownBy(() -> service.approve(company, otherManager, 1L, "ok"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requesterCannotApproveOwnSteps() {
        User requester = user(9L, EMPLOYEE_ID, "USER");
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", requester);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));

        assertThatThrownBy(() -> service.approve(company, requester, 1L, "ok"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void crossYearApproveDebitsBothYears() {
        LocalDate start = LocalDate.of(2026, 12, 28);
        LocalDate end = LocalDate.of(2027, 1, 3);
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        request.setStartDate(start);
        request.setEndDate(end);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 2, "HR", "PENDING")));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of());
        when(payrollSnapshotRepository.isMonthFrozen(eq(COMPANY_ID), anyInt(), anyInt())).thenReturn(false);
        when(userRepository.getReferenceById(hrActor.getId())).thenReturn(hrActor);
        when(balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(EMPLOYEE_ID), eq(1L), anyInt()))
                .thenReturn(Optional.empty());
        when(balanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceLogRepository.save(any(LeaveBalanceLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                eq(LeaveService.REQUEST_TYPE), eq(1L))).thenReturn(List.of());

        LeaveResponse response = service.approve(company, hrActor, 1L, "ok");

        assertThat(response.getStatusCode()).isEqualTo("APPROVED");
        verify(engineService).recompute(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(start), eq(end),
                eq(hrActor), contains("leave:1"));
        verify(balanceRepository, org.mockito.Mockito.times(4)).save(any(LeaveBalance.class));
        verify(balanceLogRepository, org.mockito.Mockito.times(2)).save(any(LeaveBalanceLog.class));
    }

    @Test
    void rejectRejectsAllPendingStepsAndStoresReason() {
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        Approval hrStep = step(12L, 2, "HR", "PENDING");
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(Optional.of(managerStep));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of(managerStep, hrStep));
        when(approvalRepository.saveAll(anyList())).thenReturn(List.of());
        when(userRepository.getReferenceById(managerActor.getId())).thenReturn(managerActor);
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                eq(LeaveService.REQUEST_TYPE), eq(1L))).thenReturn(List.of(managerStep, hrStep));

        LeaveResponse response = service.reject(company, managerActor, 1L, "planning chargé");

        assertThat(response.getStatusCode()).isEqualTo("REJECTED");
        assertThat(response.getRejectedReason()).isEqualTo("planning chargé");
        assertThat(response.getApprovals()).allMatch(s -> s.getStatusCode().equals("REJECTED"));
        verify(engineService, never()).recompute(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void creatorCanCancelOwnPendingRequest() {
        User requester = user(9L, EMPLOYEE_ID, "USER");
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", requester);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of(managerStep));
        when(approvalRepository.saveAll(anyList())).thenReturn(List.of());
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                eq(LeaveService.REQUEST_TYPE), eq(1L))).thenReturn(List.of(managerStep));

        LeaveResponse response = service.cancel(company, requester, 1L, "finalement je reste");

        assertThat(response.getStatusCode()).isEqualTo("CANCELLED");
        assertThat(response.getApprovals()).allMatch(s -> s.getStatusCode().equals("CANCELLED"));
        verify(balanceRepository, never()).save(any());
        verify(engineService, never()).recompute(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void hrCancelApprovedRefundsAndRecomputes() {
        User requester = user(9L, EMPLOYEE_ID, "USER");
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "APPROVED", requester);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        LeaveBalance balance = new LeaveBalance();
        balance.setEntitlementDays(new BigDecimal("18"));
        balance.setTakenDays(new BigDecimal("5"));
        when(balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(eq(EMPLOYEE_ID), eq(1L), eq(2026)))
                .thenReturn(Optional.of(balance));
        when(balanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceLogRepository.save(any(LeaveBalanceLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of());
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                eq(LeaveService.REQUEST_TYPE), eq(1L))).thenReturn(List.of());

        LeaveResponse response = service.cancel(company, hrActor, 1L, "erreur de saisie");

        assertThat(response.getStatusCode()).isEqualTo("CANCELLED");
        assertThat(balance.getTakenDays()).isEqualByComparingTo("0");
        verify(engineService).recompute(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(START), eq(END),
                eq(hrActor), contains("leave-cancel:1"));
    }

    @Test
    void creatorCannotCancelOwnApprovedRequest() {
        User requester = user(9L, EMPLOYEE_ID, "USER");
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "APPROVED", requester);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(company, requester, 1L, "erreur"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelTerminalRequestRejected() {
        User requester = user(9L, EMPLOYEE_ID, "USER");
        LeaveRequest request = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "REJECTED", requester);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(company, requester, 1L, "raison"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void pendingQueueReturnsOnlyDecidable() {
        LeaveRequest decidable = request(1L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", user(9L, EMPLOYEE_ID, "USER"));
        LeaveRequest ownRequest = request(2L, employee(EMPLOYEE_ID, MANAGER_EMPLOYEE_ID), "PENDING", managerActor);
        when(leaveRequestRepository.findScoped(COMPANY_ID, null, "PENDING")).thenReturn(List.of(decidable, ownRequest));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdInOrderByStepOrderAsc(
                LeaveService.REQUEST_TYPE, List.of(1L)))
                .thenReturn(List.of(step(11L, 1, "MANAGER", "PENDING")));

        var result = service.pendingQueue(company, managerActor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void balanceDeniedForAnotherEmployee() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        User otherUser = user(9L, 99L, "USER");

        assertThatThrownBy(() -> service.balance(company, otherUser, EMPLOYEE_ID, 2026))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void balanceReturnsRowsForYear() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employee(EMPLOYEE_ID, null));
        balance.setLeaveType(leaveType("ANNUAL", new BigDecimal("18")));
        balance.setYear(2026);
        balance.setEntitlementDays(new BigDecimal("18"));
        balance.setTakenDays(new BigDecimal("3"));
        when(balanceRepository.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026)).thenReturn(List.of(balance));

        List<LeaveBalanceResponse> result = service.balance(company, hrActor, EMPLOYEE_ID, 2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaveTypeCode()).isEqualTo("ANNUAL");
        assertThat(result.get(0).getAvailableDays()).isEqualByComparingTo("15");
    }

    @Test
    void listRejectsFromAfterTo() {
        assertThatThrownBy(() -> service.list(company, null, null,
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
