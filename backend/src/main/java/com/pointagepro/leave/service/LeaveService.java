package com.pointagepro.leave.service;

import com.pointagepro.attendance.entity.Holiday;
import com.pointagepro.attendance.repository.HolidayRepository;
import com.pointagepro.attendance.service.AttendanceEngineService;
import com.pointagepro.audit.service.AuditService;
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
import com.pointagepro.shared.approval.dto.ApprovalStepResponse;
import com.pointagepro.shared.approval.entity.Approval;
import com.pointagepro.shared.approval.entity.ApprovalStatus;
import com.pointagepro.shared.approval.repository.ApprovalRepository;
import com.pointagepro.shared.approval.repository.ApprovalStatusRepository;
import com.pointagepro.shared.approval.service.ApprovalAuthority;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leave management workflow (LEAVE_BUSINESS_RULES.md, contract §2-§9).
 *
 * Lifecycle: PENDING → APPROVED | REJECTED | CANCELLED; terminal states are immutable
 * (409). The approval chain is materialized at creation (request type {@value #REQUEST_TYPE}):
 * step 1 MANAGER (the requester's department manager; skipped when there is none, the
 * requester is the manager, or the manager has no active account), step 2 HR (always
 * present and never auto-decided). The requester never decides their own steps; there is
 * no empty-chain immediate-apply for leaves.
 *
 * {@code daysRequested} is server-computed as working days (Mon-Fri minus company
 * holidays). Approval debits tracked balances per covered year (auto-provisioning missing
 * rows) and recomputes attendance over the range with reason {@code leave:<id>}; HR/ADMIN
 * cancellation of an approved request refunds those days and recomputes with
 * {@code leave-cancel:<id>}. Every transition is audited and every balance movement is
 * recorded in {@code leave_balance_logs} (ref_type='LEAVE', ref_id=request id).
 *
 * Public methods return response DTOs so the controller stays thin and no lazy
 * association is touched after the transaction commits.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    public static final String REQUEST_TYPE = "LEAVE";
    private static final String ENTITY_TYPE = "LEAVE_REQUEST";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String OPERATION_APPROVAL = "APPROVAL";
    private static final String OPERATION_CANCELLATION = "CANCELLATION";
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_ATTACHMENT_LENGTH = 255;
    private static final int MAX_DECISION_COMMENT_LENGTH = 500;
    private static final int MAX_REJECT_COMMENT_LENGTH = 255;
    private static final long MAX_SPAN_CALENDAR_DAYS = 366;

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestStatusRepository leaveRequestStatusRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveBalanceLogRepository balanceLogRepository;
    private final ApprovalRepository approvalRepository;
    private final ApprovalStatusRepository approvalStatusRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final HolidayRepository holidayRepository;
    private final PayrollAttendanceSnapshotRepository payrollSnapshotRepository;
    private final AttendanceEngineService engineService;
    private final AuditService auditService;
    private final ApprovalAuthority approvalAuthority;

    @Transactional
    public LeaveResponse create(Company company, User actor, Long employeeId, String leaveTypeCode,
                                LocalDate startDate, LocalDate endDate, String reason, String attachmentPath) {
        Employee employee = loadEmployee(company, employeeId);
        assertCanCreate(actor, employee);

        LeaveType type = leaveTypeRepository.findByCode(leaveTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown leave type: " + leaveTypeCode));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw new IllegalArgumentException("Leave type is not active: " + leaveTypeCode);
        }
        validateSpan(startDate, endDate);
        String trimmedReason = optionalTrim(reason, MAX_REASON_LENGTH, "reason");
        String trimmedAttachment = optionalTrim(attachmentPath, MAX_ATTACHMENT_LENGTH, "attachmentPath");

        checkNoOverlap(employee.getId(), startDate, endDate);

        BigDecimal days = workingDays(company.getId(), startDate, endDate);
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("The requested span contains no working days");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setLeaveType(type);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDaysRequested(days);
        request.setReason(trimmedReason);
        request.setAttachmentPath(trimmedAttachment);
        request.setStatus(pendingRequestStatus());
        request.setCreatedBy(userRepository.getReferenceById(actor.getId()));

        LeaveRequest saved = leaveRequestRepository.save(request);
        List<Approval> chain = buildChain(saved.getId(), actor, employee);
        approvalRepository.saveAll(chain);

        auditService.log("CREATE", company.getId(), actor.getId(), ENTITY_TYPE, saved.getId(),
                null, json(STATUS_PENDING));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> list(Company company, Long employeeId, String statusCode,
                                    LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        return toResponses(leaveRequestRepository.findScoped(company.getId(), employeeId, statusCode).stream()
                .filter(r -> from == null || !r.getStartDate().isBefore(from))
                .filter(r -> to == null || !r.getStartDate().isAfter(to))
                .toList());
    }

    @Transactional(readOnly = true)
    public LeaveResponse get(Company company, Long id) {
        LeaveRequest request = leaveRequestRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        assertSameCompany(request, company);
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> pendingQueue(Company company, User actor) {
        return toResponses(leaveRequestRepository.findScoped(company.getId(), null, STATUS_PENDING).stream()
                .filter(r -> canDecide(actor, r))
                .toList());
    }

    @Transactional
    public LeaveResponse approve(Company company, User actor, Long id, String comment) {
        LeaveRequest request = loadRequest(company, id);
        requireStatus(request, STATUS_PENDING);
        Approval step = currentPendingStep(id)
                .orElseThrow(() -> new ConflictException("No pending approval step for this leave request"));
        assertCanDecide(actor, request, step);
        checkNotFrozen(company.getId(), request.getStartDate(), request.getEndDate());
        validateComment(comment);
        if (isTracked(request.getLeaveType())) {
            dryRunBalance(company.getId(), request);
        }

        LocalDateTime now = LocalDateTime.now();
        step.setStatus(approvalStatusRepository.findByCode(STATUS_APPROVED)
                .orElseThrow(() -> new IllegalStateException("Approval status APPROVED is not seeded")));
        step.setApprover(userRepository.getReferenceById(actor.getId()));
        step.setComment(comment);
        step.setDecidedAt(now);
        approvalRepository.save(step);
        auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), "APPROVAL", step.getId(),
                json(STATUS_PENDING), json(STATUS_APPROVED));

        boolean allDecided = approvalRepository
                .findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(REQUEST_TYPE, id, STATUS_PENDING)
                .isEmpty();
        if (allDecided) {
            request.setStatus(leaveRequestStatusRepository.findByCode(STATUS_APPROVED)
                    .orElseThrow(() -> new IllegalStateException("Leave request status APPROVED is not seeded")));
            request.setApprovedBy(userRepository.getReferenceById(actor.getId()));
            request.setApprovedAt(now);
            if (isTracked(request.getLeaveType())) {
                debitBalance(company, request, actor);
            }
            leaveRequestRepository.save(request);
            engineService.recompute(company.getId(), request.getEmployee().getId(),
                    request.getStartDate(), request.getEndDate(), actor, "leave:" + id);
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, id,
                    json(STATUS_PENDING), json(STATUS_APPROVED));
        }
        return toResponse(request);
    }

    @Transactional
    public LeaveResponse reject(Company company, User actor, Long id, String comment) {
        LeaveRequest request = loadRequest(company, id);
        requireStatus(request, STATUS_PENDING);
        Approval step = currentPendingStep(id)
                .orElseThrow(() -> new ConflictException("No pending approval step for this leave request"));
        assertCanDecide(actor, request, step);
        validateRejectComment(comment);

        LocalDateTime now = LocalDateTime.now();
        ApprovalStatus rejectedApproval = approvalStatusRepository.findByCode(STATUS_REJECTED)
                .orElseThrow(() -> new IllegalStateException("Approval status REJECTED is not seeded"));
        User actorRef = userRepository.getReferenceById(actor.getId());
        List<Approval> pendingSteps = approvalRepository
                .findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(REQUEST_TYPE, id, STATUS_PENDING);
        for (Approval pendingStep : pendingSteps) {
            pendingStep.setStatus(rejectedApproval);
            pendingStep.setApprover(actorRef);
            pendingStep.setComment(comment);
            pendingStep.setDecidedAt(now);
        }
        approvalRepository.saveAll(pendingSteps);

        request.setStatus(leaveRequestStatusRepository.findByCode(STATUS_REJECTED)
                .orElseThrow(() -> new IllegalStateException("Leave request status REJECTED is not seeded")));
        request.setRejectedReason(comment);
        leaveRequestRepository.save(request);

        for (Approval pendingStep : pendingSteps) {
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), "APPROVAL", pendingStep.getId(),
                    json(STATUS_PENDING), json(STATUS_REJECTED));
        }
        auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, id,
                json(STATUS_PENDING), json(STATUS_REJECTED));
        return toResponse(request);
    }

    @Transactional
    public LeaveResponse cancel(Company company, User actor, Long id, String reason) {
        LeaveRequest request = loadRequest(company, id);
        String status = request.getStatus().getCode();
        if (!STATUS_PENDING.equals(status) && !STATUS_APPROVED.equals(status)) {
            throw new ConflictException("Leave request is not PENDING or APPROVED");
        }
        boolean isCreator = request.getCreatedBy().getId().equals(actor.getId());
        boolean privileged = approvalAuthority.isHr(actor) || approvalAuthority.isAdmin(actor);
        if (STATUS_PENDING.equals(status) && isCreator && !privileged) {
            // creator withdrawal - allowed
        } else if (!privileged) {
            throw new AccessDeniedException("Only the requester (while PENDING) or an HR/ADMIN can cancel a leave request");
        }
        String trimmedReason = requireReason(reason, 255);

        boolean fromApproved = STATUS_APPROVED.equals(status);
        if (fromApproved) {
            checkNotFrozen(company.getId(), request.getStartDate(), request.getEndDate());
        }

        LocalDateTime now = LocalDateTime.now();
        ApprovalStatus cancelledApproval = approvalStatusRepository.findByCode(STATUS_CANCELLED)
                .orElseThrow(() -> new IllegalStateException("Approval status CANCELLED is not seeded"));
        List<Approval> pendingSteps = approvalRepository
                .findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(REQUEST_TYPE, id, STATUS_PENDING);
        for (Approval pendingStep : pendingSteps) {
            pendingStep.setStatus(cancelledApproval);
            pendingStep.setComment(trimmedReason);
            pendingStep.setDecidedAt(now);
        }
        approvalRepository.saveAll(pendingSteps);

        request.setStatus(leaveRequestStatusRepository.findByCode(STATUS_CANCELLED)
                .orElseThrow(() -> new IllegalStateException("Leave request status CANCELLED is not seeded")));
        leaveRequestRepository.save(request);

        if (fromApproved) {
            if (isTracked(request.getLeaveType())) {
                refundBalance(company, request, actor);
            }
            engineService.recompute(company.getId(), request.getEmployee().getId(),
                    request.getStartDate(), request.getEndDate(), actor, "leave-cancel:" + id);
        }

        for (Approval pendingStep : pendingSteps) {
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), "APPROVAL", pendingStep.getId(),
                    json(STATUS_PENDING), json(STATUS_CANCELLED));
        }
        auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, id,
                json(status), "{\"status\":\"" + STATUS_CANCELLED + "\",\"reason\":\"" + trimmedReason + "\"}");
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> balance(Company company, User actor, Long employeeId, Integer year) {
        Employee employee = loadEmployee(company, employeeId);
        boolean self = employee.getId().equals(actor.getEmployeeId());
        if (!self && !approvalAuthority.isHr(actor) && !approvalAuthority.isAdmin(actor)) {
            throw new AccessDeniedException("You can only view your own leave balance");
        }
        int effectiveYear = year != null ? year : LocalDate.now().getYear();
        return balanceRepository.findByEmployeeIdAndYear(employee.getId(), effectiveYear).stream()
                .map(LeaveBalanceResponse::from)
                .toList();
    }

    private List<LeaveResponse> toResponses(List<LeaveRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Approval>> chains = approvalRepository
                .findWithDetailsByRequestTypeAndRequestIdInOrderByStepOrderAsc(
                        REQUEST_TYPE, requests.stream().map(LeaveRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Approval::getRequestId));
        return requests.stream()
                .map(r -> toResponse(r, chains.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    private LeaveResponse toResponse(LeaveRequest request) {
        List<Approval> chain = approvalRepository
                .findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(REQUEST_TYPE, request.getId());
        return toResponse(request, chain);
    }

    private LeaveResponse toResponse(LeaveRequest request, List<Approval> chain) {
        return LeaveResponse.from(request, chain.stream().map(ApprovalStepResponse::from).toList());
    }

    private List<Approval> buildChain(Long requestId, User actor, Employee employee) {
        ApprovalStatus pending = approvalStatusRepository.findByCode(STATUS_PENDING)
                .orElseThrow(() -> new IllegalStateException("Approval status PENDING is not seeded"));

        List<Approval> chain = new ArrayList<>();
        Department department = employee.getDepartment();
        Long managerEmployeeId = department != null ? department.getManagerEmployeeId() : null;
        boolean skipManager = managerEmployeeId == null
                || managerEmployeeId.equals(employee.getId())
                || managerEmployeeId.equals(actor.getEmployeeId())
                || userRepository.findByEmployeeId(managerEmployeeId).isEmpty();
        if (!skipManager) {
            chain.add(newStep(requestId, 1, ApprovalAuthority.ROLE_MANAGER, pending));
        }
        chain.add(newStep(requestId, 2, ApprovalAuthority.ROLE_HR, pending));
        return chain;
    }

    private Approval newStep(Long requestId, int stepOrder, String approverRole, ApprovalStatus status) {
        Approval step = new Approval();
        step.setRequestType(REQUEST_TYPE);
        step.setRequestId(requestId);
        step.setStepOrder(stepOrder);
        step.setApproverRole(approverRole);
        step.setStatus(status);
        return step;
    }

    private boolean isTracked(LeaveType type) {
        return type.getDefaultDaysPerYear() != null;
    }

    /** Per-year debit dry-run: each covered year's working days must fit the available balance (auto-provisioned if missing). */
    private void dryRunBalance(Long companyId, LeaveRequest request) {
        for (YearSegment segment : yearSegments(request)) {
            BigDecimal days = workingDays(companyId, segment.from(), segment.to());
            BigDecimal available = availableFor(segment);
            if (days.compareTo(available) > 0) {
                throw new IllegalArgumentException("Insufficient balance: " + days
                        + " working day(s) requested in " + segment.year()
                        + " but only " + available + " available for " + request.getLeaveType().getCode());
            }
        }
    }

    private void debitBalance(Company company, LeaveRequest request, User actor) {
        for (YearSegment segment : yearSegments(request)) {
            BigDecimal days = workingDays(company.getId(), segment.from(), segment.to());
            LeaveBalance balance = balanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(request.getEmployee().getId(),
                            request.getLeaveType().getId(), segment.year())
                    .orElseGet(() -> provision(company, request, segment.year(), actor));
            BigDecimal available = availableFor(balance);
            if (days.compareTo(available) > 0) {
                throw new IllegalArgumentException("Insufficient balance: " + days
                        + " working day(s) requested in " + segment.year()
                        + " but only " + available + " available");
            }
            balance.setTakenDays(balance.getTakenDays().add(days));
            balanceRepository.save(balance);
            logBalanceMovement(request, actor, segment.year(), days, OPERATION_APPROVAL, "Approval of leave request");
        }
    }

    private void refundBalance(Company company, LeaveRequest request, User actor) {
        for (YearSegment segment : yearSegments(request)) {
            BigDecimal days = workingDays(company.getId(), segment.from(), segment.to());
            LeaveBalance balance = balanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(request.getEmployee().getId(),
                            request.getLeaveType().getId(), segment.year())
                    .orElseThrow(() -> new IllegalStateException("No balance row to refund for "
                            + request.getLeaveType().getCode() + " " + segment.year()));
            balance.setTakenDays(balance.getTakenDays().subtract(days));
            balanceRepository.save(balance);
            logBalanceMovement(request, actor, segment.year(), days.negate(), OPERATION_CANCELLATION,
                    "Cancellation of approved leave request");
        }
    }

    /** Auto-provisions a missing balance row with the type default entitlement and an audit entry. */
    private LeaveBalance provision(Company company, LeaveRequest request, int year, User actor) {
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(request.getEmployee());
        balance.setLeaveType(request.getLeaveType());
        balance.setYear(year);
        balance.setEntitlementDays(request.getLeaveType().getDefaultDaysPerYear());
        balance.setTakenDays(BigDecimal.ZERO);
        balance.setCarriedOverDays(BigDecimal.ZERO);
        balance.setAdjustedDays(BigDecimal.ZERO);
        LeaveBalance saved = balanceRepository.save(balance);
        auditService.log("CREATE", company.getId(), actor.getId(), "LEAVE_BALANCE", saved.getId(), null,
                "{\"entitlementDays\":" + saved.getEntitlementDays() + "}");
        return saved;
    }

    private void logBalanceMovement(LeaveRequest request, User actor, int year, BigDecimal delta, String operation,
                                    String reason) {
        LeaveBalanceLog log = new LeaveBalanceLog();
        log.setEmployee(request.getEmployee());
        log.setLeaveType(request.getLeaveType());
        log.setYear(year);
        log.setDeltaDays(delta);
        log.setOperation(operation);
        log.setReason(reason);
        log.setRefType(REQUEST_TYPE);
        log.setRefId(request.getId());
        log.setCreatedBy(userRepository.getReferenceById(actor.getId()));
        balanceLogRepository.save(log);
    }

    private BigDecimal availableFor(YearSegment segment) {
        return balanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(segment.employeeId(), segment.leaveTypeId(), segment.year())
                .map(LeaveService::availableFor)
                .orElseGet(segment::defaultEntitlement);
    }

    private static BigDecimal availableFor(LeaveBalance balance) {
        return balance.getEntitlementDays()
                .add(balance.getCarriedOverDays())
                .add(balance.getAdjustedDays())
                .subtract(balance.getTakenDays());
    }

    private record YearSegment(int year, LocalDate from, LocalDate to,
                               Long employeeId, Long leaveTypeId, BigDecimal defaultEntitlement) {
    }

    private List<YearSegment> yearSegments(LeaveRequest request) {
        return yearSegments(request.getStartDate(), request.getEndDate(),
                request.getEmployee().getId(), request.getLeaveType().getId(),
                request.getLeaveType().getDefaultDaysPerYear());
    }

    private List<YearSegment> yearSegments(LocalDate start, LocalDate end,
                                           Long employeeId, Long leaveTypeId, BigDecimal defaultEntitlement) {
        List<YearSegment> segments = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            int year = d.getYear();
            if (segments.isEmpty() || segments.get(segments.size() - 1).year() != year) {
                segments.add(new YearSegment(year, d, d, employeeId, leaveTypeId, defaultEntitlement));
            } else {
                YearSegment last = segments.get(segments.size() - 1);
                segments.set(segments.size() - 1,
                        new YearSegment(year, last.from(), d, employeeId, leaveTypeId, defaultEntitlement));
            }
        }
        return segments;
    }

    private BigDecimal workingDays(Long companyId, LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = new HashSet<>(holidayRepository
                .findByCompanyIdAndHolidayDateBetweenOrderByHolidayDateAsc(companyId, from, to)
                .stream().map(Holiday::getHolidayDate).toList());
        long count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                count++;
            }
        }
        return BigDecimal.valueOf(count);
    }

    private void checkNoOverlap(Long employeeId, LocalDate start, LocalDate end) {
        boolean overlaps = leaveRequestRepository
                .existsByEmployeeIdAndStatus_CodeInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, List.of(STATUS_PENDING, STATUS_APPROVED), end, start);
        if (overlaps) {
            throw new ConflictException("The employee already has a pending or approved leave request "
                    + "overlapping " + start + " - " + end);
        }
    }

    private void assertCanCreate(User actor, Employee employee) {
        if (approvalAuthority.isHr(actor) || approvalAuthority.isAdmin(actor)) {
            return;
        }
        if (employee.getId().equals(actor.getEmployeeId())) {
            return;
        }
        throw new AccessDeniedException("Users can only request leave for themselves");
    }

    private boolean canDecide(User actor, LeaveRequest request) {
        if (request.getCreatedBy().getId().equals(actor.getId())) {
            return false;
        }
        if (request.getEmployee().getId().equals(actor.getEmployeeId())) {
            return false;
        }
        return currentPendingStep(request.getId())
                .map(step -> approvalAuthority.canDecideStep(actor, step.getApproverRole(), request.getEmployee()))
                .orElse(false);
    }

    private void assertCanDecide(User actor, LeaveRequest request, Approval step) {
        if (request.getCreatedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("The requester cannot decide on their own leave request");
        }
        if (request.getEmployee().getId().equals(actor.getEmployeeId())) {
            throw new AccessDeniedException("An employee cannot decide on their own leave request");
        }
        if (!approvalAuthority.canDecideStep(actor, step.getApproverRole(), request.getEmployee())) {
            throw new AccessDeniedException("You are not the assigned approver for the current step");
        }
    }

    private Optional<Approval> currentPendingStep(Long requestId) {
        return approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                REQUEST_TYPE, requestId, STATUS_PENDING);
    }

    private LeaveRequest loadRequest(Company company, Long id) {
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        assertSameCompany(request, company);
        return request;
    }

    private void assertSameCompany(LeaveRequest request, Company company) {
        if (!request.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Leave request not found: " + request.getId());
        }
    }

    private Employee loadEmployee(Company company, Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (!employee.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
        return employee;
    }

    private void requireStatus(LeaveRequest request, String expected) {
        if (!expected.equals(request.getStatus().getCode())) {
            throw new ConflictException("Leave request is not " + expected);
        }
    }

    private void checkNotFrozen(Long companyId, LocalDate start, LocalDate end) {
        LocalDate cursor = start.withDayOfMonth(1);
        LocalDate lastMonth = end.withDayOfMonth(1);
        while (!cursor.isAfter(lastMonth)) {
            if (payrollSnapshotRepository.isMonthFrozen(companyId, cursor.getYear(), cursor.getMonthValue())) {
                throw new ConflictException("Payroll period is frozen: the " + cursor.getMonth() + " "
                        + cursor.getYear() + " attendance is locked. Reopen the payroll first.");
            }
            cursor = cursor.plusMonths(1);
        }
    }

    private void validateSpan(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("'startDate' and 'endDate' are required");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'startDate' must not be after 'endDate'");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_SPAN_CALENDAR_DAYS) {
            throw new IllegalArgumentException("The leave span must be at most "
                    + MAX_SPAN_CALENDAR_DAYS + " calendar days");
        }
    }

    private String optionalTrim(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("'" + field + "' must be at most " + maxLength + " characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireReason(String reason, int maxLength) {
        String trimmed = optionalTrim(reason, maxLength, "reason");
        if (trimmed == null) {
            throw new IllegalArgumentException("'reason' is required");
        }
        return trimmed;
    }

    private void validateComment(String comment) {
        if (comment != null && comment.length() > MAX_DECISION_COMMENT_LENGTH) {
            throw new IllegalArgumentException("'comment' must be at most "
                    + MAX_DECISION_COMMENT_LENGTH + " characters");
        }
    }

    private void validateRejectComment(String comment) {
        if (comment != null && comment.length() > MAX_REJECT_COMMENT_LENGTH) {
            throw new IllegalArgumentException("'comment' must be at most "
                    + MAX_REJECT_COMMENT_LENGTH + " characters when rejecting");
        }
    }

    private LeaveRequestStatus pendingRequestStatus() {
        return leaveRequestStatusRepository.findByCode(STATUS_PENDING)
                .orElseThrow(() -> new IllegalStateException("Leave request status PENDING is not seeded"));
    }

    private String json(String status) {
        return "{\"status\":\"" + status + "\"}";
    }
}
