package com.pointagepro.schedule.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.schedule.dto.ScheduleAssignmentRequest;
import com.pointagepro.schedule.dto.ScheduleAssignmentResponse;
import com.pointagepro.schedule.service.ScheduleService;
import com.pointagepro.shared.ApiResponse;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/employees/{employeeId}/schedules")
@RequiredArgsConstructor
public class EmployeeScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserService currentUserService;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('schedule.read')")
    public ResponseEntity<ApiResponse<List<ScheduleAssignmentResponse>>> list(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId) {
        Company company = currentUserService.requireCompany(user);
        Employee employee = requireEmployee(company, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Affectations de planning",
                scheduleService.listAssignments(company, employee)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('schedule.write')")
    public ResponseEntity<ApiResponse<ScheduleAssignmentResponse>> assign(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId,
            @Valid @RequestBody ScheduleAssignmentRequest request) {
        Company company = currentUserService.requireCompany(user);
        Employee employee = requireEmployee(company, employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Planning affecté", scheduleService.assign(company, employee, request)));
    }

    @PutMapping("/{asgId}")
    @PreAuthorize("hasAuthority('schedule.write')")
    public ResponseEntity<ApiResponse<ScheduleAssignmentResponse>> update(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId, @PathVariable Long asgId,
            @Valid @RequestBody ScheduleAssignmentRequest request) {
        Company company = currentUserService.requireCompany(user);
        Employee employee = requireEmployee(company, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Affectation mise à jour",
                scheduleService.updateAssignment(company, employee, asgId, request)));
    }

    @DeleteMapping("/{asgId}")
    @PreAuthorize("hasAuthority('schedule.write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId, @PathVariable Long asgId) {
        Company company = currentUserService.requireCompany(user);
        Employee employee = requireEmployee(company, employeeId);
        scheduleService.deleteAssignment(company, employee, asgId);
        return ResponseEntity.ok(ApiResponse.success("Affectation supprimée"));
    }

    private Employee requireEmployee(Company company, Long employeeId) {
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        if (!e.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
        return e;
    }
}
