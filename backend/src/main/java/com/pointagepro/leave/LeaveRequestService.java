package com.pointagepro.leave;

import com.pointagepro.employee.Employee;
import com.pointagepro.employee.EmployeeRepository;
import com.pointagepro.auth.User;
import com.pointagepro.auth.UserRepository;
import com.pointagepro.leave.dto.LeaveBalanceResponse;
import com.pointagepro.leave.dto.LeaveRequestCreate;
import com.pointagepro.leave.dto.LeaveRequestResponse;
import com.pointagepro.notification.NotificationService;
import com.pointagepro.payroll.PayrollService;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);

    private static final Map<String, Integer> DEFAULT_LIMITS = Map.of(
        "Congé Annuel", 22,
        "Congé Maternité", 90,
        "Congé Paternité", 5
    );

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveAllocationRepository leaveAllocationRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PayrollService payrollService;
    private final NotificationService notificationService;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                               LeaveAllocationRepository leaveAllocationRepository,
                               EmployeeRepository employeeRepository,
                               UserRepository userRepository,
                               PayrollService payrollService,
                               NotificationService notificationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveAllocationRepository = leaveAllocationRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.payrollService = payrollService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getAll(String search, String status, String leaveType, String sort) {
        List<LeaveRequest> results;
        boolean asc = "oldest".equals(sort);
        String dbStatus = mapStatusToDb(status);
        if (asc) {
            results = leaveRequestRepository.searchAsc(search, dbStatus, leaveType);
        } else {
            results = leaveRequestRepository.searchDesc(search, dbStatus, leaveType);
        }
        return results.stream()
                .map(LeaveRequestResponse::fromLeaveRequest)
                .toList();
    }

    private String mapStatusToDb(String status) {
        if (status == null || status.isBlank()) return "";
        return switch (status) {
            case "Approuvé" -> "APPROVED";
            case "Refusé" -> "REFUSED";
            case "En cours" -> "PENDING";
            default -> status;
        };
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Long id) {
        LeaveRequest lr = leaveRequestRepository.findByIdWithEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", id));
        return LeaveRequestResponse.fromLeaveRequestDetail(lr);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getByEmployeeId(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(LeaveRequestResponse::fromLeaveRequest)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getByStatus(String status) {
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(LeaveRequestResponse::fromLeaveRequest)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> getBalance(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return List.of();
        }

        String[] types = {"Congé Annuel", "Congé Maladie", "Congé Maternité", "Congé Paternité", "Formation"};
        String[] colors = {"#2563EB", "#10B981", "#8B5CF6", "#06B6D4", "#EC4899"};

        return java.util.stream.IntStream.range(0, types.length).mapToObj(i -> {
            String type = types[i];

            long approvedDays = leaveRequestRepository.sumApprovedDaysByEmployeeAndType(employeeId, type);
            long pendingDays = leaveRequestRepository.sumPendingDaysByEmployeeAndType(employeeId, type);
            long used = approvedDays + pendingDays;

            if (!DEFAULT_LIMITS.containsKey(type)) {
                return new LeaveBalanceResponse(type, null, used, null, colors[i]);
            }

            Integer limit = switch (type) {
                case "Congé Annuel" -> employee.getAnnualLeaveDays();
                case "Congé Maternité" -> employee.getMaternityLeaveDays();
                case "Congé Paternité" -> employee.getPaternityLeaveDays();
                default -> null;
            };

            if (limit == null) {
                return new LeaveBalanceResponse(type, null, used, null, colors[i]);
            }

            long total = limit;
            long remaining = Math.max(total - used, 0);

            return new LeaveBalanceResponse(type, total, used, remaining, colors[i]);
        }).toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return leaveRequestRepository.countPending();
    }

    @Transactional(readOnly = true)
    public long countApproved() {
        return leaveRequestRepository.countApproved();
    }

    @Transactional(readOnly = true)
    public long countRefused() {
        return leaveRequestRepository.countRefused();
    }

    public LeaveRequestResponse create(LeaveRequestCreate request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", request.getEmployeeId()));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        if (DEFAULT_LIMITS.containsKey(request.getLeaveType())) {
            long requestedDays = java.time.temporal.ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
            Long remaining = getRemainingDays(employee.getId(), request.getLeaveType());
            if (remaining != null && requestedDays > remaining) {
                throw new IllegalArgumentException(
                    "Solde insuffisant pour " + request.getLeaveType() + ". Disponible: " + remaining + " j, Demandé: " + requestedDays + " j");
            }
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(request.getLeaveType());
        lr.setStartDate(request.getStartDate());
        lr.setEndDate(request.getEndDate());
        lr.setReason(request.getReason());
        lr.setAttachment(request.getAttachment());
        lr.setStatus("PENDING");

        LeaveRequest saved = leaveRequestRepository.save(lr);
        log.info("Leave request created: employee={} {} type={}", employee.getFirstName(), employee.getLastName(), request.getLeaveType());

        String empName = employee.getFirstName() + " " + employee.getLastName();
        notificationService.notify("LEAVE_REQUEST", "Demande de congé",
            empName + " — " + request.getLeaveType() + " du " + request.getStartDate() + " au " + request.getEndDate(),
            "HIGH", "LEAVE", saved.getId());

        long pendingCount = countPending();
        if (pendingCount >= 3) {
            notificationService.notify("LEAVE_LOW_BALANCE", "Solde congé bas",
                pendingCount + " demandes de congé en attente", "MEDIUM");
        }

        return LeaveRequestResponse.fromLeaveRequest(saved);
    }

    public LeaveRequestResponse update(Long id, LeaveRequestCreate request) {
        LeaveRequest lr = leaveRequestRepository.findByIdWithEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", id));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        String oldStatus = lr.getStatus();

        lr.setLeaveType(request.getLeaveType());
        lr.setStartDate(request.getStartDate());
        lr.setEndDate(request.getEndDate());
        lr.setReason(request.getReason());
        lr.setAttachment(request.getAttachment());

        if (request.getStatus() != null) {
            lr.setStatus(request.getStatus());
        }

        LeaveRequest saved = leaveRequestRepository.save(lr);
        log.info("Leave request updated: id={}, status {} -> {}", id, oldStatus, saved.getStatus());

        String newStatus = saved.getStatus();
        if (!oldStatus.equals(newStatus)) {
            triggerPayrollRecalculation(lr.getEmployee().getId(), lr.getStartDate());
            if ("APPROVED".equals(newStatus) && !"CONGE".equals(lr.getEmployee().getStatus())) {
                lr.getEmployee().setStatus("CONGE");
                employeeRepository.save(lr.getEmployee());
            } else if ("PENDING".equals(newStatus) || "REFUSED".equals(newStatus)) {
                boolean hasActiveApproved = leaveRequestRepository
                        .existsByEmployeeIdAndStatusInRange(
                                lr.getEmployee().getId(), "APPROVED", LocalDate.now());
                if (!hasActiveApproved) {
                    lr.getEmployee().setStatus("ACTIF");
                    employeeRepository.save(lr.getEmployee());
                }
            }
        }

        return LeaveRequestResponse.fromLeaveRequest(saved);
    }

    public LeaveRequestResponse approve(Long id) {
        LeaveRequest lr = leaveRequestRepository.findByIdWithEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", id));
        lr.setStatus("APPROVED");
        lr.setApprovedBy(getCurrentUser());

        Employee employee = lr.getEmployee();
        if (!"CONGE".equals(employee.getStatus())) {
            employee.setStatus("CONGE");
            employeeRepository.save(employee);
            log.info("Employee {} {} set to CONGE status", employee.getFirstName(), employee.getLastName());
        }

        LeaveRequest saved = leaveRequestRepository.save(lr);
        log.info("Leave request approved: id={}", id);

        String empName = employee.getFirstName() + " " + employee.getLastName();
        notificationService.notify("LEAVE_APPROVED_INFO", "Congé approuvé",
            empName + " — " + lr.getLeaveType() + " du " + lr.getStartDate() + " au " + lr.getEndDate() + " (approuvé)",
            "LOW", "LEAVE", saved.getId());

        updateAllocationUsed(employee.getId(), lr.getLeaveType(), lr.getStartDate(), lr.getEndDate(), 1);
        triggerPayrollRecalculation(employee.getId(), lr.getStartDate());

        return LeaveRequestResponse.fromLeaveRequest(saved);
    }

    public LeaveRequestResponse refuse(Long id) {
        LeaveRequest lr = leaveRequestRepository.findByIdWithEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", id));
        lr.setStatus("REFUSED");
        lr.setApprovedBy(getCurrentUser());
        LeaveRequest saved = leaveRequestRepository.save(lr);
        log.info("Leave request refused: id={}", id);

        String empName = lr.getEmployee().getFirstName() + " " + lr.getEmployee().getLastName();
        notificationService.notify("LEAVE_REFUSED", "Congé refusé",
            empName + " — " + lr.getLeaveType() + " du " + lr.getStartDate() + " au " + lr.getEndDate() + " (refusé)",
            "MEDIUM", "LEAVE", saved.getId());

        triggerPayrollRecalculation(lr.getEmployee().getId(), lr.getStartDate());

        return LeaveRequestResponse.fromLeaveRequest(saved);
    }

    public LeaveRequestResponse resetToPending(Long id) {
        LeaveRequest lr = leaveRequestRepository.findByIdWithEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", id));

        String oldStatus = lr.getStatus();
        lr.setStatus("PENDING");
        LeaveRequest saved = leaveRequestRepository.save(lr);
        log.info("Leave request reset to pending: id={}, was {}", id, oldStatus);

        Employee employee = lr.getEmployee();
        if ("APPROVED".equals(oldStatus)) {
            updateAllocationUsed(employee.getId(), lr.getLeaveType(), lr.getStartDate(), lr.getEndDate(), -1);

            boolean hasOtherApproved = leaveRequestRepository
                    .existsByEmployeeIdAndStatusInRange(
                            employee.getId(), "APPROVED", LocalDate.now());
            if (!hasOtherApproved) {
                employee.setStatus("ACTIF");
                employeeRepository.save(employee);
                log.info("Employee {} {} restored to ACTIF", employee.getFirstName(), employee.getLastName());
            }
        }

        triggerPayrollRecalculation(employee.getId(), lr.getStartDate());

        return LeaveRequestResponse.fromLeaveRequest(saved);
    }

    private void updateAllocationUsed(Long employeeId, String leaveType, LocalDate startDate, LocalDate endDate, int direction) {
        if (!DEFAULT_LIMITS.containsKey(leaveType)) return;

        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int year = startDate.getYear();

        LeaveAllocation allocation = leaveAllocationRepository
                .findByEmployeeIdAndYearAndLeaveType(employeeId, year, leaveType)
                .orElse(null);

        if (allocation != null) {
            int newUsed = Math.max(0, allocation.getUsed() + (int)(days * direction));
            allocation.setUsed(newUsed);
            leaveAllocationRepository.save(allocation);
            log.info("Updated allocation: employee={} year={} type={} used={} (direction={})",
                    employeeId, year, leaveType, newUsed, direction);
        }
    }

    private Long getRemainingDays(Long employeeId, String leaveType) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) return null;

        Integer limit = switch (leaveType) {
            case "Congé Annuel" -> employee.getAnnualLeaveDays();
            case "Congé Maternité" -> employee.getMaternityLeaveDays();
            case "Congé Paternité" -> employee.getPaternityLeaveDays();
            default -> null;
        };
        if (limit == null) return null;

        long approvedDays = leaveRequestRepository.sumApprovedDaysByEmployeeAndType(employeeId, leaveType);
        long pendingDays = leaveRequestRepository.sumPendingDaysByEmployeeAndType(employeeId, leaveType);
        return Math.max(limit - approvedDays - pendingDays, 0);
    }

    private void triggerPayrollRecalculation(Long employeeId, LocalDate leaveDate) {
        try {
            payrollService.recalculateEmployeeInPayroll(
                    employeeId, leaveDate.getMonthValue(), leaveDate.getYear());
        } catch (Exception e) {
            log.warn("Could not auto-recalculate payroll for employee {} month {}/{}: {}",
                    employeeId, leaveDate.getMonthValue(), leaveDate.getYear(), e.getMessage());
        }
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    public void delete(Long id) {
        if (!leaveRequestRepository.existsById(id)) {
            throw new ResourceNotFoundException("LeaveRequest", "id", id);
        }
        leaveRequestRepository.deleteById(id);
        log.info("Leave request deleted: id={}", id);
    }
}
