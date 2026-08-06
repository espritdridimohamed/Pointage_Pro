package com.pointagepro.company.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.dto.CompanyResponse;
import com.pointagepro.company.entity.Company;
import com.pointagepro.company.service.CompanyService;
import com.pointagepro.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('company.read')")
    public ResponseEntity<ApiResponse<CompanyResponse>> me(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Société", companyService.get(company)));
    }
}
