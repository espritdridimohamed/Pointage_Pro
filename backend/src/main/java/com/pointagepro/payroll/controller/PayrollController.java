package com.pointagepro.payroll.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.payroll.dto.PayrollItemResponse;
import com.pointagepro.payroll.dto.PayrollNoteRequest;
import com.pointagepro.payroll.dto.PayrollPayRequest;
import com.pointagepro.payroll.dto.PayrollRunCreateRequest;
import com.pointagepro.payroll.dto.PayrollRunResponse;
import com.pointagepro.payroll.dto.PayslipResponse;
import com.pointagepro.payroll.service.PayrollService;
import com.pointagepro.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * Payroll run workflow (PAYROLL_API_CONTRACT.md §2-§12). Controllers only
 * resolve the tenant and map the request; the state machine, freeze step and
 * legal math live in {@link PayrollService}.
 */
@RestController
@RequestMapping("/payrolls")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('payroll.run')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PayrollRunCreateRequest request) {

        Company company = currentUserService.requireCompany(user);
        PayrollRunResponse data = payrollService.create(company, user,
                request.getPeriodYear(), request.getPeriodMonth(), request.getNotes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Paie créée", data));
    }

    @PostMapping("/{id}/compute")
    @PreAuthorize("hasAuthority('payroll.run')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> compute(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        PayrollRunResponse data = payrollService.compute(company.getId(), id, user);
        return ResponseEntity.ok(ApiResponse.success("Paie calculée", data));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('payroll.validate')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> validate(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PayrollNoteRequest request) {

        Company company = currentUserService.requireCompany(user);
        String notes = request != null ? request.getNotes() : null;
        PayrollRunResponse data = payrollService.validate(company.getId(), id, user, notes);
        return ResponseEntity.ok(ApiResponse.success("Paie validée", data));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('payroll.approve')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> approve(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        PayrollRunResponse data = payrollService.approve(company.getId(), id, user);
        return ResponseEntity.ok(ApiResponse.success("Paie approuvée", data));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('payroll.pay')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> pay(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PayrollPayRequest request) {

        Company company = currentUserService.requireCompany(user);
        String ref = request != null ? request.getBankTransferRef() : null;
        PayrollRunResponse data = payrollService.pay(company.getId(), id, user, ref);
        return ResponseEntity.ok(ApiResponse.success("Paie marquée payée", data));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('payroll.cancel')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PayrollNoteRequest request) {

        Company company = currentUserService.requireCompany(user);
        String notes = request != null ? request.getNotes() : null;
        PayrollRunResponse data = payrollService.cancel(company.getId(), id, user, notes);
        return ResponseEntity.ok(ApiResponse.success("Paie annulée", data));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('payroll.read')")
    public ResponseEntity<ApiResponse<List<PayrollRunResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String status) {

        Company company = currentUserService.requireCompany(user);
        List<PayrollRunResponse> data = payrollService.list(company.getId(), year, month, status);
        return ResponseEntity.ok(ApiResponse.success("Liste des paies", data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll.read')")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> getById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        PayrollRunResponse data = payrollService.get(company.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Paie", data));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('payroll.read')")
    public ResponseEntity<ApiResponse<List<PayrollItemResponse>>> items(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        List<PayrollItemResponse> data = payrollService.items(company.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lignes de paie", data));
    }

    @GetMapping("/{id}/payslips")
    @PreAuthorize("hasAuthority('payslip.read')")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> payslips(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        List<PayslipResponse> data = payrollService.payslips(company.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Bulletins de paie", data));
    }
}
