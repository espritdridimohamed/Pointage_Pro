package com.pointagepro.attendance.service;

import com.pointagepro.attendance.dto.AdjustmentResponse;
import com.pointagepro.attendance.engine.DayResult;
import com.pointagepro.attendance.entity.AdjustmentStatus;
import com.pointagepro.attendance.entity.AdjustmentType;
import com.pointagepro.attendance.entity.AttendanceAdjustment;
import com.pointagepro.attendance.repository.AdjustmentStatusRepository;
import com.pointagepro.attendance.repository.AdjustmentTypeRepository;
import com.pointagepro.attendance.repository.AttendanceAdjustmentRepository;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.audit.service.AuditService;
import com.pointagepro.auth.entity.Role;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceAdjustmentServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long EMPLOYEE_ID = 10L;
    private static final long ACTOR_EMPLOYEE_ID = 50L;
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 5);

    @Mock
    private AttendanceAdjustmentRepository adjustmentRepository;
    @Mock
    private AdjustmentTypeRepository adjustmentTypeRepository;
    @Mock
    private AdjustmentStatusRepository adjustmentStatusRepository;
    @Mock
    private AttendanceSummaryRepository summaryRepository;
    @Mock
    private ApprovalRepository approvalRepository;
    @Mock
    private ApprovalStatusRepository approvalStatusRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AttendanceEngineService engineService;
    @Mock
    private PayrollAttendanceSnapshotRepository payrollSnapshotRepository;
    @Mock
    private AuditService auditService;
    @Spy
    private ApprovalAuthority approvalAuthority = new ApprovalAuthority();

    @InjectMocks
    private AttendanceAdjustmentService service;

    private Company company;
    private User hrActor;
    private User managerActor;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        hrActor = user(3L, 30L, "HR");
        managerActor = user(7L, ACTOR_EMPLOYEE_ID, "MANAGER");
        seedStatuses();
    }

    private void seedStatuses() {
        lenient().when(adjustmentStatusRepository.findByCode("PENDING")).thenReturn(Optional.of(adjStatus("PENDING")));
        lenient().when(adjustmentStatusRepository.findByCode("APPLIED")).thenReturn(Optional.of(adjStatus("APPLIED")));
        lenient().when(adjustmentStatusRepository.findByCode("REJECTED")).thenReturn(Optional.of(adjStatus("REJECTED")));
        lenient().when(adjustmentStatusRepository.findByCode("CANCELLED")).thenReturn(Optional.of(adjStatus("CANCELLED")));
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
        u.setRoles(Set.of(java.util.Arrays.stream(roleCodes).map(AttendanceAdjustmentServiceTest::role).toArray(Role[]::new)));
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

    private static AdjustmentStatus adjStatus(String code) {
        AdjustmentStatus s = new AdjustmentStatus();
        s.setCode(code);
        s.setLabel(code);
        return s;
    }

    private static ApprovalStatus approvalStatus(String code) {
        ApprovalStatus s = new ApprovalStatus();
        s.setCode(code);
        return s;
    }

    private static AdjustmentType adjType(String code) {
        AdjustmentType t = new AdjustmentType();
        t.setCode(code);
        t.setLabel(code);
        return t;
    }

    private static AttendanceAdjustment adjustment(long id, Employee employee, String statusCode, User createdBy) {
        AttendanceAdjustment a = new AttendanceAdjustment();
        a.setId(id);
        a.setCompany(companyRef());
        a.setEmployee(employee);
        a.setWorkDate(WORK_DATE);
        a.setAdjustmentType(adjType("ADD_MINUTES"));
        a.setMinutes(30);
        a.setReason("correction");
        a.setStatus(adjStatus(statusCode));
        a.setCreatedBy(createdBy);
        return a;
    }

    private static Approval step(long id, int order, String role, String statusCode) {
        Approval s = new Approval();
        s.setId(id);
        s.setRequestType(AttendanceAdjustmentService.REQUEST_TYPE);
        s.setRequestId(1L);
        s.setStepOrder(order);
        s.setApproverRole(role);
        s.setStatus(approvalStatus(statusCode));
        return s;
    }

    private void stubSaveReturnsSameInstance() {
        when(adjustmentRepository.save(any(AttendanceAdjustment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void hrCreateWithoutManagerStepAppliesImmediatelyAndRecomputes() {
        stubSaveReturnsSameInstance();
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        when(adjustmentTypeRepository.findByCode("ADD_MINUTES")).thenReturn(Optional.of(adjType("ADD_MINUTES")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        when(userRepository.getReferenceById(hrActor.getId())).thenReturn(hrActor);

        AdjustmentResponse response = service.create(company, hrActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause oubliée");

        assertThat(response.getStatusCode()).isEqualTo("APPLIED");
        assertThat(response.getApprovals()).hasSize(1);
        assertThat(response.getApprovals().get(0).getApproverRole()).isEqualTo("HR");
        assertThat(response.getApprovals().get(0).getStatusCode()).isEqualTo("APPROVED");
        verify(approvalRepository).saveAll(any());
        verify(engineService).recomputeDay(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(WORK_DATE),
                eq(hrActor), contains("adjustment:"));
        verify(auditService).log(eq("CREATE"), eq(COMPANY_ID), eq(hrActor.getId()),
                eq("ATTENDANCE_ADJUSTMENT"), any(), eq(null), anyString());
        verify(auditService).log(eq("STATUS_CHANGE"), eq(COMPANY_ID), eq(hrActor.getId()),
                eq("ATTENDANCE_ADJUSTMENT"), any(), anyString(), eq("{\"status\":\"APPLIED\"}"));
    }

    @Test
    void hrCreateForEmployeeWithManagerStaysPending() {
        stubSaveReturnsSameInstance();
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID)));
        when(userRepository.findByEmployeeId(ACTOR_EMPLOYEE_ID))
                .thenReturn(Optional.of(managerActor));
        when(adjustmentTypeRepository.findByCode("ADD_MINUTES")).thenReturn(Optional.of(adjType("ADD_MINUTES")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        when(userRepository.getReferenceById(hrActor.getId())).thenReturn(hrActor);

        AdjustmentResponse response = service.create(company, hrActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause oubliée");

        assertThat(response.getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals()).hasSize(2);
        assertThat(response.getApprovals().get(0).getApproverRole()).isEqualTo("MANAGER");
        assertThat(response.getApprovals().get(0).getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals().get(1).getApproverRole()).isEqualTo("HR");
        assertThat(response.getApprovals().get(1).getStatusCode()).isEqualTo("APPROVED");
        verify(engineService, never()).recomputeDay(any(), any(), any(), any(), anyString());
    }

    @Test
    void managerCreateSkipsManagerStepAndStaysPending() {
        stubSaveReturnsSameInstance();
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID)));
        when(adjustmentTypeRepository.findByCode("ADD_MINUTES")).thenReturn(Optional.of(adjType("ADD_MINUTES")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        when(userRepository.getReferenceById(managerActor.getId())).thenReturn(managerActor);

        AdjustmentResponse response = service.create(company, managerActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause oubliée");

        assertThat(response.getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals()).hasSize(1);
        assertThat(response.getApprovals().get(0).getApproverRole()).isEqualTo("HR");
        assertThat(response.getApprovals().get(0).getStatusCode()).isEqualTo("PENDING");
    }

    @Test
    void managerCannotCreateForOtherDepartment() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, 999L)));

        assertThatThrownBy(() -> service.create(company, managerActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonManagerNonHrCannotCreate() {
        User userActor = user(9L, 90L, "USER");
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));

        assertThatThrownBy(() -> service.create(company, userActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanCreateAdjustmentForAnyEmployee() {
        stubSaveReturnsSameInstance();
        User adminActor = user(5L, 55L, "ADMIN");
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID)));
        when(userRepository.findByEmployeeId(ACTOR_EMPLOYEE_ID))
                .thenReturn(Optional.of(managerActor));
        when(adjustmentTypeRepository.findByCode("ADD_MINUTES")).thenReturn(Optional.of(adjType("ADD_MINUTES")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        when(userRepository.getReferenceById(adminActor.getId())).thenReturn(adminActor);

        AdjustmentResponse response = service.create(company, adminActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause oubliée");

        assertThat(response.getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals()).hasSize(2);
        assertThat(response.getApprovals().get(0).getApproverRole()).isEqualTo("MANAGER");
        assertThat(response.getApprovals().get(0).getStatusCode()).isEqualTo("PENDING");
        assertThat(response.getApprovals().get(1).getApproverRole()).isEqualTo("HR");
        assertThat(response.getApprovals().get(1).getStatusCode()).isEqualTo("PENDING");
    }

    @Test
    void createUnknownTypeRejected() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        when(adjustmentTypeRepository.findByCode("FOO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(company, hrActor, EMPLOYEE_ID, WORK_DATE,
                "FOO", 30, "pause"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSetAbsentWithNonZeroMinutesRejected() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        when(adjustmentTypeRepository.findByCode("SET_ABSENT"))
                .thenReturn(Optional.of(adjType("SET_ABSENT")));

        assertThatThrownBy(() -> service.create(company, hrActor, EMPLOYEE_ID, WORK_DATE,
                "SET_ABSENT", 30, "absent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createFrozenMonthRejected() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        when(adjustmentTypeRepository.findByCode("ADD_MINUTES")).thenReturn(Optional.of(adjType("ADD_MINUTES")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(true);

        assertThatThrownBy(() -> service.create(company, hrActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, "pause"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createWithoutReasonRejected() {
        when(employeeRepository.findById(EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(EMPLOYEE_ID, null)));
        when(adjustmentTypeRepository.findByCode("ADD_MINUTES")).thenReturn(Optional.of(adjType("ADD_MINUTES")));

        assertThatThrownBy(() -> service.create(company, hrActor, EMPLOYEE_ID, WORK_DATE,
                "ADD_MINUTES", 30, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void managerApproveAppliesAndRecomputes() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(Optional.of(managerStep));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        DayResult preview = new DayResult();
        preview.setWorkedMinutes(480);
        when(engineService.previewDay(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(WORK_DATE), any()))
                .thenReturn(preview);
        when(userRepository.getReferenceById(managerActor.getId())).thenReturn(managerActor);
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of());
        when(summaryRepository.findByEmployeeIdAndWorkDate(EMPLOYEE_ID, WORK_DATE))
                .thenReturn(Optional.empty());
        when(adjustmentRepository.save(any(AttendanceAdjustment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L)).thenReturn(List.of(managerStep));

        AdjustmentResponse response = service.approve(company, managerActor, 1L, "ok");

        assertThat(response.getStatusCode()).isEqualTo("APPLIED");
        verify(engineService).recomputeDay(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(WORK_DATE),
                eq(managerActor), contains("adjustment:"));
    }

    @Test
    void approveNotPendingRejected() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "APPLIED", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));

        assertThatThrownBy(() -> service.approve(company, managerActor, 1L, "ok"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approveExceedingDailyCapRejected() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        DayResult preview = new DayResult();
        preview.setWorkedMinutes(1500);
        preview.setRawWorkedMinutes(1500);
        when(engineService.previewDay(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(WORK_DATE), any()))
                .thenReturn(preview);

        assertThatThrownBy(() -> service.approve(company, managerActor, 1L, "ok"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(engineService, never()).recomputeDay(any(), any(), any(), any(), anyString());
    }

    @Test
    void creatorCannotApproveOwnRequest() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, null),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 2, "HR", "PENDING")));

        assertThatThrownBy(() -> service.approve(company, hrActor, 1L, "ok"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAssignedApproverCannotApprove() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 1, "MANAGER", "PENDING")));
        User otherManager = user(8L, 77L, "MANAGER");

        assertThatThrownBy(() -> service.approve(company, otherManager, 1L, "ok"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanApproveManagerStep() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        User adminActor = user(5L, 55L, "ADMIN");
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(Optional.of(managerStep));
        when(payrollSnapshotRepository.isMonthFrozen(COMPANY_ID, 2026, 8)).thenReturn(false);
        DayResult preview = new DayResult();
        preview.setWorkedMinutes(480);
        when(engineService.previewDay(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(WORK_DATE), any()))
                .thenReturn(preview);
        when(userRepository.getReferenceById(adminActor.getId())).thenReturn(adminActor);
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of());
        when(summaryRepository.findByEmployeeIdAndWorkDate(EMPLOYEE_ID, WORK_DATE))
                .thenReturn(Optional.empty());
        when(adjustmentRepository.save(any(AttendanceAdjustment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L)).thenReturn(List.of(managerStep));

        AdjustmentResponse response = service.approve(company, adminActor, 1L, "ok");

        assertThat(response.getStatusCode()).isEqualTo("APPLIED");
        verify(engineService).recomputeDay(eq(COMPANY_ID), eq(EMPLOYEE_ID), eq(WORK_DATE),
                eq(adminActor), contains("adjustment:"));
    }

    @Test
    void adminCannotApproveOwnRequest() {
        User adminActor = user(5L, 55L, "ADMIN");
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, null),
                "PENDING", adminActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 2, "HR", "PENDING")));

        assertThatThrownBy(() -> service.approve(company, adminActor, 1L, "ok"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectRejectsAllPendingStepsAndAdjustment() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        Approval hrStep = step(12L, 2, "HR", "PENDING");
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(Optional.of(managerStep));
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(List.of(managerStep, hrStep));
        when(approvalRepository.saveAll(any())).thenReturn(List.of());
        when(adjustmentRepository.save(any(AttendanceAdjustment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(managerActor.getId())).thenReturn(managerActor);
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L)).thenReturn(List.of(managerStep, hrStep));

        AdjustmentResponse response = service.reject(company, managerActor, 1L, "pièce manquante");

        assertThat(response.getStatusCode()).isEqualTo("REJECTED");
        assertThat(response.getApprovals()).allMatch(s -> s.getStatusCode().equals("REJECTED"));
        verify(engineService, never()).recomputeDay(any(), any(), any(), any(), anyString());
    }

    @Test
    void creatorCanCancelOwnPendingRequest() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        Approval managerStep = step(11L, 1, "MANAGER", "PENDING");
        when(approvalRepository.findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING")).thenReturn(List.of(managerStep));
        when(approvalRepository.saveAll(any())).thenReturn(List.of());
        when(adjustmentRepository.save(any(AttendanceAdjustment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L)).thenReturn(List.of(managerStep));

        AdjustmentResponse response = service.cancel(company, hrActor, 1L, "saisie en double");

        assertThat(response.getStatusCode()).isEqualTo("CANCELLED");
        assertThat(response.getApprovals()).allMatch(s -> s.getStatusCode().equals("CANCELLED"));
        verify(engineService, never()).recomputeDay(any(), any(), any(), any(), anyString());
    }

    @Test
    void nonCreatorNonHrCannotCancel() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));
        User outsider = user(9L, 90L, "USER");

        assertThatThrownBy(() -> service.cancel(company, outsider, 1L, "raison"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelRequiresReason() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "PENDING", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));

        assertThatThrownBy(() -> service.cancel(company, hrActor, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotDecideTerminalAdjustment() {
        AttendanceAdjustment adjustment = adjustment(1L, employee(EMPLOYEE_ID, ACTOR_EMPLOYEE_ID),
                "REJECTED", hrActor);
        when(adjustmentRepository.findById(1L)).thenReturn(Optional.of(adjustment));

        assertThatThrownBy(() -> service.approve(company, managerActor, 1L, "ok"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.cancel(company, hrActor, 1L, "raison"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listRejectsFromAfterTo() {
        assertThatThrownBy(() -> service.list(company, null, null,
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingQueueReturnsOnlyDecidableForActor() {
        AttendanceAdjustment decidable = adjustment(1L, employee(EMPLOYEE_ID, null),
                "PENDING", managerActor);
        AttendanceAdjustment ownRequest = adjustment(2L, employee(EMPLOYEE_ID, null),
                "PENDING", hrActor);
        when(adjustmentRepository.findWithDetailsByCompanyIdAndStatusCodeOrderByCreatedAtDesc(
                COMPANY_ID, "PENDING")).thenReturn(List.of(decidable, ownRequest));
        when(approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, 1L, "PENDING"))
                .thenReturn(Optional.of(step(11L, 2, "HR", "PENDING")));
        when(approvalRepository.findWithDetailsByRequestTypeAndRequestIdInOrderByStepOrderAsc(
                AttendanceAdjustmentService.REQUEST_TYPE, List.of(1L)))
                .thenReturn(List.of(step(11L, 2, "HR", "PENDING")));

        var result = service.pendingQueue(company, hrActor);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }
}
