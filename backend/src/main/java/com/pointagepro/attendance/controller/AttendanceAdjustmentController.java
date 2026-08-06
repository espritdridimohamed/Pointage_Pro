package com.pointagepro.attendance.controller;

import com.pointagepro.attendance.dto.AdjustmentCancelRequest;
import com.pointagepro.attendance.dto.AdjustmentDecisionRequest;
import com.pointagepro.attendance.dto.AdjustmentRequest;
import com.pointagepro.attendance.dto.AdjustmentResponse;
import com.pointagepro.attendance.service.AttendanceAdjustmentService;
import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
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
 * Attendance adjustment workflow (contract §5). Controllers only resolve the tenant and map
 * the request — the workflow, guards, dry-run and recompute trigger live in
 * {@link AttendanceAdjustmentService}.
 */
@RestController
@RequestMapping("/attendance/adjustments")
@RequiredArgsConstructor
public class AttendanceAdjustmentController {

    private final AttendanceAdjustmentService adjustmentService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('attendance.adjust')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AdjustmentRequest request) {

        Company company = currentUserService.requireCompany(user);
        AdjustmentResponse data = adjustmentService.create(company, user, request.getEmployeeId(),
                request.getWorkDate(), request.getAdjustmentType(), request.getMinutes(), request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Demande d'ajustement créée", data));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<List<AdjustmentResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Company company = currentUserService.requireCompany(user);
        List<AdjustmentResponse> data = adjustmentService.list(company, employeeId, status, from, to);
        return ResponseEntity.ok(ApiResponse.success("Liste des ajustements", data));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('attendance.adjust')")
    public ResponseEntity<ApiResponse<List<AdjustmentResponse>>> pending(
            @AuthenticationPrincipal User user) {

        Company company = currentUserService.requireCompany(user);
        List<AdjustmentResponse> data = adjustmentService.pendingQueue(company, user);
        return ResponseEntity.ok(ApiResponse.success("File d'attente des validations", data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('attendance.read')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> getById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        AdjustmentResponse data = adjustmentService.get(company, id);
        return ResponseEntity.ok(ApiResponse.success("Ajustement", data));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('attendance.adjust')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> approve(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdjustmentDecisionRequest request) {

        Company company = currentUserService.requireCompany(user);
        String comment = request != null ? request.getComment() : null;
        AdjustmentResponse data = adjustmentService.approve(company, user, id, comment);
        return ResponseEntity.ok(ApiResponse.success("Ajustement approuvé", data));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('attendance.adjust')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> reject(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdjustmentDecisionRequest request) {

        Company company = currentUserService.requireCompany(user);
        String comment = request != null ? request.getComment() : null;
        AdjustmentResponse data = adjustmentService.reject(company, user, id, comment);
        return ResponseEntity.ok(ApiResponse.success("Ajustement rejeté", data));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('attendance.adjust')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody AdjustmentCancelRequest request) {

        Company company = currentUserService.requireCompany(user);
        AdjustmentResponse data = adjustmentService.cancel(company, user, id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Ajustement annulé", data));
    }
}
