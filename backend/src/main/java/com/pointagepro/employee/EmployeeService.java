package com.pointagepro.employee;

import com.pointagepro.employee.dto.EmployeeRequest;
import com.pointagepro.employee.dto.EmployeeResponse;
import com.pointagepro.notification.NotificationService;
import com.pointagepro.payroll.PayrollService;
import com.pointagepro.shared.PageResponse;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final PayrollService payrollService;
    private final NotificationService notificationService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           PayrollService payrollService,
                           NotificationService notificationService) {
        this.employeeRepository = employeeRepository;
        this.payrollService = payrollService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> getAll(String search, String department, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Employee> employees = employeeRepository.search(search, department, pageRequest);

        return new PageResponse<>(
                employees.getContent().stream().map(EmployeeResponse::fromEmployee).toList(),
                employees.getNumber(),
                employees.getSize(),
                employees.getTotalElements(),
                employees.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<String> getDepartments() {
        return employeeRepository.findDistinctDepartments();
    }

    @Transactional(readOnly = true)
    public long getCount() {
        return employeeRepository.count();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
        return EmployeeResponse.fromEmployee(employee);
    }

    public EmployeeResponse create(EmployeeRequest request) {
        if (request.getMatricule() != null && !request.getMatricule().isBlank()) {
            if (employeeRepository.existsByMatricule(request.getMatricule())) {
                throw new DuplicateResourceException("Employee", "matricule", request.getMatricule());
            }
        } else {
            request.setMatricule(generateMatricule());
        }
        if (request.getRfidUid() != null && !request.getRfidUid().isBlank()) {
            if (employeeRepository.existsByRfidUid(request.getRfidUid())) {
                throw new DuplicateResourceException("Employee", "RFID UID", request.getRfidUid());
            }
        }

        Employee employee = new Employee();
        mapRequestToEmployee(request, employee);
        Employee saved = employeeRepository.save(employee);

        payrollService.addEmployeeToCurrentDraftPayroll(saved);

        log.info("Employee created: {} {}", saved.getFirstName(), saved.getLastName());
        notificationService.notify("EMPLOYEE_CREATED", "Nouvel employé",
            saved.getFirstName() + " " + saved.getLastName() + " ajouté — " + (saved.getDepartment() != null ? saved.getDepartment() : "N/A"),
            "LOW", "EMPLOYEE", saved.getId());
        return EmployeeResponse.fromEmployee(saved);
    }

    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));

        if (employeeRepository.existsByMatriculeAndIdNot(request.getMatricule(), id)) {
            throw new DuplicateResourceException("Employee", "matricule", request.getMatricule());
        }
        if (request.getRfidUid() != null && !request.getRfidUid().isBlank()) {
            if (employeeRepository.existsByRfidUidAndIdNot(request.getRfidUid(), id)) {
                throw new DuplicateResourceException("Employee", "RFID UID", request.getRfidUid());
            }
        }

        mapRequestToEmployee(request, employee);
        Employee saved = employeeRepository.save(employee);

        payrollService.updateEmployeeInDraftPayroll(saved);

        log.info("Employee updated: {} {}", saved.getFirstName(), saved.getLastName());

        if (request.getRfidUid() != null && !request.getRfidUid().isBlank()) {
            notificationService.notify("EMPLOYEE_RFID_ASSIGNED", "Badge assigné",
                "Badge " + request.getRfidUid() + " → " + saved.getFirstName() + " " + saved.getLastName(),
                "LOW", "EMPLOYEE", saved.getId());
        }

        String oldStatus = employee.getStatus();
        String newStatus = saved.getStatus();
        if (!oldStatus.equals(newStatus)) {
            notificationService.notify("EMPLOYEE_STATUS_CHANGE", "Changement statut",
                saved.getFirstName() + " " + saved.getLastName() + " — " + oldStatus + " → " + newStatus,
                "MEDIUM", "EMPLOYEE", saved.getId());
        }

        return EmployeeResponse.fromEmployee(saved);
    }

    public void delete(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee", "id", id);
        }
        payrollService.removeEmployeeFromDraftPayrolls(id);
        employeeRepository.deleteById(id);
        log.info("Employee deleted: id={}", id);
    }

    private String generateMatricule() {
        Long maxNum = employeeRepository.findMaxMatriculeNumber().orElse(0L);
        long nextNum = maxNum + 1;
        return String.format("EMP-%03d", nextNum);
    }

    private void mapRequestToEmployee(EmployeeRequest request, Employee employee) {
        employee.setMatricule(request.getMatricule());
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setPosition(request.getPosition());
        employee.setDepartment(request.getDepartment());
        employee.setContractType(request.getContractType());
        employee.setPhoto(request.getPhoto());
        employee.setBirthDate(request.getBirthDate());
        employee.setCin(request.getCin());
        employee.setAddress(request.getAddress());
        employee.setBaseSalary(request.getBaseSalary());
        employee.setPrimeTransport(request.getPrimeTransport() != null ? request.getPrimeTransport() : BigDecimal.ZERO);
        employee.setPrimePerformance(request.getPrimePerformance() != null ? request.getPrimePerformance() : BigDecimal.ZERO);
        employee.setPrimeOther(request.getPrimeOther() != null ? request.getPrimeOther() : BigDecimal.ZERO);
        employee.setRfidUid(request.getRfidUid());
        employee.setWeeklySchedule(request.getWeeklySchedule());
        employee.setAnnualLeaveDays(request.getAnnualLeaveDays());
        employee.setMaternityLeaveDays(request.getMaternityLeaveDays());
        employee.setPaternityLeaveDays(request.getPaternityLeaveDays());
        employee.setHiringDate(request.getHiringDate());
        employee.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIF");
    }
}
