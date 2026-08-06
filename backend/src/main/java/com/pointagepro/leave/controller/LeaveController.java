package com.pointagepro.leave.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.leave.dto.LeaveBalanceResponse;
import com.pointagepro.leave.dto.LeaveCancelRequest;
import com.pointagepro.leave.dto.LeaveCreateRequest;
import com.pointagepro.leave.dto.LeaveDecisionRequest;
import com.pointagepro.leave.dto.LeaveResponse;
import com.pointagepro.leave.service.LeaveService;
import com.pointagepro.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Leave management workflow (contract §2-§9). Controllers only resolve the tenant and map
 * the request — the workflow, guards, balance debit/refund and recompute trigger live in
 * {@link LeaveService}.
 */
@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('leave.write')")
    public ResponseEntity<ApiResponse<LeaveResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody LeaveCreateRequest request) {

        Company company = currentUserService.requireCompany(user);
        LeaveResponse data = leaveService.create(company, user, request.getEmployeeId(),
                request.getLeaveTypeCode(), request.getStartDate(), request.getEndDate(),
                request.getReason(), request.getAttachmentPath());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Demande de congé créée", data));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('leave.read')")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Company company = currentUserService.requireCompany(user);
        List<LeaveResponse> data = leaveService.list(company, employeeId, status, from, to);
        return ResponseEntity.ok(ApiResponse.success("Liste des congés", data));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('leave.approve')")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> pending(
            @AuthenticationPrincipal User user) {

        Company company = currentUserService.requireCompany(user);
        List<LeaveResponse> data = leaveService.pendingQueue(company, user);
        return ResponseEntity.ok(ApiResponse.success("File d'attente des validations de congés", data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('leave.read')")
    public ResponseEntity<ApiResponse<LeaveResponse>> getById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        LeaveResponse data = leaveService.get(company, id);
        return ResponseEntity.ok(ApiResponse.success("Demande de congé", data));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('leave.approve')")
    public ResponseEntity<ApiResponse<LeaveResponse>> approve(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeaveDecisionRequest request) {

        Company company = currentUserService.requireCompany(user);
        String comment = request != null ? request.getComment() : null;
        LeaveResponse data = leaveService.approve(company, user, id, comment);
        return ResponseEntity.ok(ApiResponse.success("Congé approuvé", data));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('leave.approve')")
    public ResponseEntity<ApiResponse<LeaveResponse>> reject(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeaveDecisionRequest request) {

        Company company = currentUserService.requireCompany(user);
        String comment = request != null ? request.getComment() : null;
        LeaveResponse data = leaveService.reject(company, user, id, comment);
        return ResponseEntity.ok(ApiResponse.success("Congé rejeté", data));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('leave.approve') or hasAuthority('leave.write')")
    public ResponseEntity<ApiResponse<LeaveResponse>> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody LeaveCancelRequest request) {

        Company company = currentUserService.requireCompany(user);
        LeaveResponse data = leaveService.cancel(company, user, id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Congé annulé", data));
    }

    @GetMapping("/balance/{employeeId}")
    @PreAuthorize("hasAuthority('leave.read')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> balance(
            @AuthenticationPrincipal User user,
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {

        Company company = currentUserService.requireCompany(user);
        List<LeaveBalanceResponse> data = leaveService.balance(company, user, employeeId, year);
        return ResponseEntity.ok(ApiResponse.success("Soldes de congés", data));
    }
}
