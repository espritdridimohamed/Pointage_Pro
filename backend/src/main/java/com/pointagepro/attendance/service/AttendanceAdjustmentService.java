package com.pointagepro.attendance.service;

import com.pointagepro.attendance.dto.AdjustmentResponse;
import com.pointagepro.attendance.engine.AdjustmentInput;
import com.pointagepro.attendance.engine.DayCalculator;
import com.pointagepro.attendance.engine.DayResult;
import com.pointagepro.attendance.entity.AdjustmentStatus;
import com.pointagepro.attendance.entity.AdjustmentType;
import com.pointagepro.attendance.entity.AttendanceAdjustment;
import com.pointagepro.attendance.repository.AdjustmentStatusRepository;
import com.pointagepro.attendance.repository.AdjustmentTypeRepository;
import com.pointagepro.attendance.repository.AttendanceAdjustmentRepository;
import com.pointagepro.attendance.repository.AttendanceSummaryRepository;
import com.pointagepro.audit.service.AuditService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.organization.entity.Department;
import com.pointagepro.payroll.repository.PayrollAttendanceSnapshotRepository;
import com.pointagepro.shared.approval.entity.Approval;
import com.pointagepro.shared.approval.entity.ApprovalStatus;
import com.pointagepro.shared.approval.dto.ApprovalStepResponse;
import com.pointagepro.shared.approval.repository.ApprovalRepository;
import com.pointagepro.shared.approval.repository.ApprovalStatusRepository;
import com.pointagepro.shared.approval.service.ApprovalAuthority;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Attendance adjustment workflow (business rules §5, contract §5).
 *
 * Lifecycle: PENDING → APPLIED | REJECTED | CANCELLED. The approval chain is
 * materialized at creation (request type {@value #REQUEST_TYPE}): step 1 MANAGER
 * (the target's department manager; skipped when there is none, the target is the
 * manager, or the creator is the manager), step 2 HR (auto-decided when the creator
 * is HR). The creator and the target employee can never decide a step. An empty
 * remaining chain applies the adjustment immediately in the same transaction.
 *
 * Applying a correction recomputes exactly that employee/day with reason
 * {@code adjustment:<id>}; the engine attaches {@code attendance_adjustments.summary_id}.
 * Approval and adjustment states are guarded (PENDING only) and race-safe via
 * optimistic locking. Every transition is appended to {@code audit_logs}.
 *
 * Public methods return response DTOs so the controller stays thin and no lazy
 * association is touched after the transaction commits.
 */
@Service
@RequiredArgsConstructor
public class AttendanceAdjustmentService {

    public static final String REQUEST_TYPE = "ATTENDANCE_ADJUST";
    private static final String ENTITY_TYPE = "ATTENDANCE_ADJUSTMENT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final int MAX_COMMENT_LENGTH = 500;

    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final AdjustmentTypeRepository adjustmentTypeRepository;
    private final AdjustmentStatusRepository adjustmentStatusRepository;
    private final AttendanceSummaryRepository summaryRepository;
    private final ApprovalRepository approvalRepository;
    private final ApprovalStatusRepository approvalStatusRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AttendanceEngineService engineService;
    private final PayrollAttendanceSnapshotRepository payrollSnapshotRepository;
    private final AuditService auditService;
    private final ApprovalAuthority approvalAuthority;

    @Transactional
    public AdjustmentResponse create(Company company, User actor, Long employeeId, LocalDate workDate,
                                     String typeCode, Integer minutes, String reason) {
        Employee employee = loadEmployee(company, employeeId);
        assertCanCreate(actor, employee);

        AdjustmentType type = adjustmentTypeRepository.findByCode(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown adjustment type: " + typeCode));
        validateMinutes(type.getCode(), minutes);
        String trimmedReason = requireReason(reason, 255);

        checkNotFrozen(company.getId(), workDate);

        AttendanceAdjustment adjustment = new AttendanceAdjustment();
        adjustment.setCompany(employee.getCompany());
        adjustment.setEmployee(employee);
        adjustment.setWorkDate(workDate);
        adjustment.setAdjustmentType(type);
        adjustment.setMinutes(minutes);
        adjustment.setReason(trimmedReason);
        adjustment.setStatus(pendingStatus());
        adjustment.setCreatedBy(userRepository.getReferenceById(actor.getId()));

        AttendanceAdjustment saved = adjustmentRepository.save(adjustment);
        List<Approval> chain = buildChain(saved.getId(), actor, employee);
        boolean allDecided = chain.stream()
                .allMatch(step -> !STATUS_PENDING.equals(step.getStatus().getCode()));

        if (allDecided) {
            LocalDateTime now = LocalDateTime.now();
            saved.setStatus(adjustmentStatusRepository.findByCode(STATUS_APPLIED)
                    .orElseThrow(() -> new IllegalStateException("Adjustment status APPLIED is not seeded")));
            saved.setApprovedBy(userRepository.getReferenceById(actor.getId()));
            saved.setApprovedAt(now);
            adjustmentRepository.save(saved);
        }
        approvalRepository.saveAll(chain);

        auditService.log("CREATE", company.getId(), actor.getId(), ENTITY_TYPE, saved.getId(),
                null, json(STATUS_PENDING));
        if (allDecided) {
            apply(saved, actor);
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, saved.getId(),
                    json(STATUS_PENDING), json(STATUS_APPLIED));
        }
        return toResponse(saved, chain);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentResponse> list(Company company, Long employeeId, String statusCode,
                                         LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        List<AttendanceAdjustment> adjustments;
        if (employeeId != null && statusCode != null) {
            adjustments = adjustmentRepository.findWithDetailsByCompanyIdAndEmployeeIdAndStatusCodeOrderByCreatedAtDesc(
                    company.getId(), employeeId, statusCode);
        } else if (employeeId != null) {
            adjustments = adjustmentRepository.findWithDetailsByCompanyIdAndEmployeeIdOrderByCreatedAtDesc(
                    company.getId(), employeeId);
        } else if (statusCode != null) {
            adjustments = adjustmentRepository.findWithDetailsByCompanyIdAndStatusCodeOrderByCreatedAtDesc(
                    company.getId(), statusCode);
        } else {
            adjustments = adjustmentRepository.findWithDetailsByCompanyIdOrderByCreatedAtDesc(company.getId());
        }
        return toResponses(adjustments.stream()
                .filter(a -> from == null || !a.getWorkDate().isBefore(from))
                .filter(a -> to == null || !a.getWorkDate().isAfter(to))
                .toList());
    }

    @Transactional(readOnly = true)
    public AdjustmentResponse get(Company company, Long id) {
        AttendanceAdjustment adjustment = adjustmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance adjustment not found: " + id));
        assertSameCompany(adjustment, company);
        return toResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentResponse> pendingQueue(Company company, User actor) {
        return toResponses(adjustmentRepository.findWithDetailsByCompanyIdAndStatusCodeOrderByCreatedAtDesc(
                        company.getId(), STATUS_PENDING).stream()
                .filter(a -> canDecide(actor, a))
                .toList());
    }

    @Transactional
    public AdjustmentResponse approve(Company company, User actor, Long id, String comment) {
        AttendanceAdjustment adjustment = loadAdjustment(company, id);
        requireStatus(adjustment, STATUS_PENDING);
        Approval step = currentPendingStep(id)
                .orElseThrow(() -> new ConflictException("No pending approval step for this adjustment"));
        assertCanDecide(actor, adjustment, step);
        checkNotFrozen(company.getId(), adjustment.getWorkDate());
        validateCap(adjustment);
        validateComment(comment);

        LocalDateTime now = LocalDateTime.now();
        step.setStatus(approvalStatusRepository.findByCode("APPROVED")
                .orElseThrow(() -> new IllegalStateException("Approval status APPROVED is not seeded")));
        step.setApprover(userRepository.getReferenceById(actor.getId()));
        step.setComment(comment);
        step.setDecidedAt(now);
        approvalRepository.save(step);
        auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), "APPROVAL", step.getId(),
                json(STATUS_PENDING), json("APPROVED"));

        boolean allDecided = approvalRepository
                .findByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(REQUEST_TYPE, id, STATUS_PENDING)
                .isEmpty();
        if (allDecided) {
            adjustment.setStatus(adjustmentStatusRepository.findByCode(STATUS_APPLIED)
                    .orElseThrow(() -> new IllegalStateException("Adjustment status APPLIED is not seeded")));
            adjustment.setApprovedBy(userRepository.getReferenceById(actor.getId()));
            adjustment.setApprovedAt(now);
            adjustmentRepository.save(adjustment);
            apply(adjustment, actor);
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, id,
                    json(STATUS_PENDING), json(STATUS_APPLIED));
        }
        return toResponse(adjustment);
    }

    @Transactional
    public AdjustmentResponse reject(Company company, User actor, Long id, String comment) {
        AttendanceAdjustment adjustment = loadAdjustment(company, id);
        requireStatus(adjustment, STATUS_PENDING);
        Approval step = currentPendingStep(id)
                .orElseThrow(() -> new ConflictException("No pending approval step for this adjustment"));
        assertCanDecide(actor, adjustment, step);
        validateComment(comment);

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

        adjustment.setStatus(adjustmentStatusRepository.findByCode(STATUS_REJECTED)
                .orElseThrow(() -> new IllegalStateException("Adjustment status REJECTED is not seeded")));
        adjustmentRepository.save(adjustment);

        for (Approval pendingStep : pendingSteps) {
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), "APPROVAL", pendingStep.getId(),
                    json(STATUS_PENDING), json(STATUS_REJECTED));
        }
        auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, id,
                json(STATUS_PENDING), json(STATUS_REJECTED));
        return toResponse(adjustment);
    }

    @Transactional
    public AdjustmentResponse cancel(Company company, User actor, Long id, String reason) {
        AttendanceAdjustment adjustment = loadAdjustment(company, id);
        requireStatus(adjustment, STATUS_PENDING);
        boolean isCreator = adjustment.getCreatedBy().getId().equals(actor.getId());
        if (!isCreator && !approvalAuthority.isHr(actor)) {
            throw new AccessDeniedException("Only the creator or an HR member can cancel an adjustment");
        }
        String trimmedReason = requireReason(reason, 255);

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

        adjustment.setStatus(adjustmentStatusRepository.findByCode(STATUS_CANCELLED)
                .orElseThrow(() -> new IllegalStateException("Adjustment status CANCELLED is not seeded")));
        adjustmentRepository.save(adjustment);

        for (Approval pendingStep : pendingSteps) {
            auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), "APPROVAL", pendingStep.getId(),
                    json(STATUS_PENDING), json(STATUS_CANCELLED));
        }
        auditService.log("STATUS_CHANGE", company.getId(), actor.getId(), ENTITY_TYPE, id,
                json(STATUS_PENDING), "{\"status\":\"" + STATUS_CANCELLED + "\",\"reason\":\"" + trimmedReason + "\"}");
        return toResponse(adjustment);
    }

    private List<AdjustmentResponse> toResponses(List<AttendanceAdjustment> adjustments) {
        if (adjustments.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Approval>> chains = approvalRepository
                .findWithDetailsByRequestTypeAndRequestIdInOrderByStepOrderAsc(
                        REQUEST_TYPE, adjustments.stream().map(AttendanceAdjustment::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Approval::getRequestId));
        return adjustments.stream()
                .map(a -> toResponse(a, chains.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    private AdjustmentResponse toResponse(AttendanceAdjustment adjustment) {
        List<Approval> chain = approvalRepository
                .findWithDetailsByRequestTypeAndRequestIdOrderByStepOrderAsc(REQUEST_TYPE, adjustment.getId());
        return toResponse(adjustment, chain);
    }

    private AdjustmentResponse toResponse(AttendanceAdjustment adjustment, List<Approval> chain) {
        return AdjustmentResponse.from(adjustment, chain.stream().map(ApprovalStepResponse::from).toList());
    }

    private List<Approval> buildChain(Long adjustmentId, User actor, Employee employee) {
        boolean creatorIsHr = approvalAuthority.isHr(actor);
        ApprovalStatus pending = approvalStatusRepository.findByCode(STATUS_PENDING)
                .orElseThrow(() -> new IllegalStateException("Approval status PENDING is not seeded"));
        ApprovalStatus approved = approvalStatusRepository.findByCode("APPROVED")
                .orElseThrow(() -> new IllegalStateException("Approval status APPROVED is not seeded"));

        List<Approval> chain = new ArrayList<>();
        Department department = employee.getDepartment();
        Long managerEmployeeId = department != null ? department.getManagerEmployeeId() : null;
        boolean skipManager = managerEmployeeId == null
                || managerEmployeeId.equals(employee.getId())
                || managerEmployeeId.equals(actor.getEmployeeId())
                || userRepository.findByEmployeeId(managerEmployeeId).isEmpty();
        if (!skipManager) {
            chain.add(newStep(adjustmentId, 1, "MANAGER", pending, null, null, null));
        }

        if (creatorIsHr) {
            User actorRef = userRepository.getReferenceById(actor.getId());
            chain.add(newStep(adjustmentId, 2, "HR", pending, actorRef, approved, LocalDateTime.now()));
        } else {
            chain.add(newStep(adjustmentId, 2, "HR", pending, null, null, null));
        }
        return chain;
    }

    private Approval newStep(Long requestId, int stepOrder, String approverRole, ApprovalStatus status,
                             User approver, ApprovalStatus resolvedStatus, LocalDateTime decidedAt) {
        Approval step = new Approval();
        step.setRequestType(REQUEST_TYPE);
        step.setRequestId(requestId);
        step.setStepOrder(stepOrder);
        step.setApproverRole(approverRole);
        step.setStatus(resolvedStatus != null ? resolvedStatus : status);
        step.setApprover(approver);
        step.setDecidedAt(decidedAt);
        return step;
    }

    /** Applies the decision: recompute the day with reason {@code adjustment:<id>}, then attach the summary. */
    private void apply(AttendanceAdjustment adjustment, User actor) {
        engineService.recomputeDay(adjustment.getCompany().getId(), adjustment.getEmployee().getId(),
                adjustment.getWorkDate(), actor, "adjustment:" + adjustment.getId());
        summaryRepository.findByEmployeeIdAndWorkDate(adjustment.getEmployee().getId(), adjustment.getWorkDate())
                .ifPresent(adjustment::setSummary);
        adjustmentRepository.save(adjustment);
    }

    /** Dry-run including the prospective adjustment: reject above the daily cap or below zero. */
    private void validateCap(AttendanceAdjustment adjustment) {
        String typeCode = adjustment.getAdjustmentType().getCode();
        if ("SET_ABSENT".equals(typeCode)) {
            return;
        }
        DayResult preview = engineService.previewDay(adjustment.getCompany().getId(),
                adjustment.getEmployee().getId(), adjustment.getWorkDate(),
                List.of(new AdjustmentInput(typeCode, adjustment.getMinutes())));
        int worked = preview.getWorkedMinutes();
        int rawWorked = preview.getRawWorkedMinutes();
        if (rawWorked > DayCalculator.MAX_DAILY_WORKED_MINUTES) {
            throw new IllegalArgumentException("This adjustment would exceed the "
                    + DayCalculator.MAX_DAILY_WORKED_MINUTES + "-minute daily maximum");
        }
        if (worked < 0) {
            throw new IllegalArgumentException("This adjustment would produce a negative worked time");
        }
    }

    private void assertCanCreate(User actor, Employee employee) {
        if (approvalAuthority.isHr(actor) || approvalAuthority.isAdmin(actor)) {
            return;
        }
        if (approvalAuthority.hasRole(actor, ApprovalAuthority.ROLE_MANAGER)
                && approvalAuthority.isDepartmentManagerOf(actor, employee)) {
            return;
        }
        throw new AccessDeniedException("Only HR or a department manager can create attendance adjustments");
    }

    private boolean canDecide(User actor, AttendanceAdjustment adjustment) {
        if (adjustment.getCreatedBy().getId().equals(actor.getId())) {
            return false;
        }
        if (adjustment.getEmployee().getId().equals(actor.getEmployeeId())) {
            return false;
        }
        return currentPendingStep(adjustment.getId())
                .map(step -> canDecideStep(actor, step, adjustment.getEmployee()))
                .orElse(false);
    }

    private void assertCanDecide(User actor, AttendanceAdjustment adjustment, Approval step) {
        if (adjustment.getCreatedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("The creator of a request cannot approve its own steps");
        }
        if (adjustment.getEmployee().getId().equals(actor.getEmployeeId())) {
            throw new AccessDeniedException("An employee cannot decide on their own adjustment");
        }
        if (!canDecideStep(actor, step, adjustment.getEmployee())) {
            throw new AccessDeniedException("You are not the assigned approver for the current step");
        }
    }

    private boolean canDecideStep(User actor, Approval step, Employee target) {
        return approvalAuthority.canDecideStep(actor, step.getApproverRole(), target);
    }

    private Optional<Approval> currentPendingStep(Long adjustmentId) {
        return approvalRepository.findFirstByRequestTypeAndRequestIdAndStatusCodeOrderByStepOrderAsc(
                REQUEST_TYPE, adjustmentId, STATUS_PENDING);
    }

    private AttendanceAdjustment loadAdjustment(Company company, Long id) {
        AttendanceAdjustment adjustment = adjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance adjustment not found: " + id));
        assertSameCompany(adjustment, company);
        return adjustment;
    }

    private void assertSameCompany(AttendanceAdjustment adjustment, Company company) {
        if (!adjustment.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Attendance adjustment not found: " + adjustment.getId());
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

    private void requireStatus(AttendanceAdjustment adjustment, String expected) {
        if (!expected.equals(adjustment.getStatus().getCode())) {
            throw new ConflictException("Adjustment is not " + expected);
        }
    }

    private void checkNotFrozen(Long companyId, LocalDate workDate) {
        if (payrollSnapshotRepository.isMonthFrozen(companyId, workDate.getYear(), workDate.getMonthValue())) {
            throw new ConflictException("Payroll period is frozen: the " + workDate.getMonth() + " "
                    + workDate.getYear() + " attendance is locked. Reopen the payroll or use a next-period correction.");
        }
    }

    private void validateMinutes(String typeCode, Integer minutes) {
        if (minutes == null || minutes < 0) {
            throw new IllegalArgumentException("'minutes' must be a positive integer");
        }
        if ("SET_ABSENT".equals(typeCode) && minutes != 0) {
            throw new IllegalArgumentException("SET_ABSENT adjustments must have minutes = 0");
        }
    }

    private String requireReason(String reason, int maxLength) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("'reason' is required");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("'reason' must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private void validateComment(String comment) {
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("'comment' must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
    }

    private AdjustmentStatus pendingStatus() {
        return adjustmentStatusRepository.findByCode(STATUS_PENDING)
                .orElseThrow(() -> new IllegalStateException("Adjustment status PENDING is not seeded"));
    }

    private String json(String status) {
        return "{\"status\":\"" + status + "\"}";
    }
}
