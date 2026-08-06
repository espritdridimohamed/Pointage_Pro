package com.pointagepro.payroll.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.payroll.dto.PayslipResponse;
import com.pointagepro.payroll.service.PayrollService;
import com.pointagepro.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payslip detail reads (PAYROLL_API_CONTRACT.md §12). Tenant scoping matches the
 * payroll module: a payslip of another company → 404.
 */
@RestController
@RequestMapping("/payslips")
@RequiredArgsConstructor
public class PayslipController {

    private final PayrollService payrollService;
    private final CurrentUserService currentUserService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payslip.read')")
    public ResponseEntity<ApiResponse<PayslipResponse>> getById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Company company = currentUserService.requireCompany(user);
        PayslipResponse data = payrollService.getPayslip(company.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Bulletin de paie", data));
    }
}
