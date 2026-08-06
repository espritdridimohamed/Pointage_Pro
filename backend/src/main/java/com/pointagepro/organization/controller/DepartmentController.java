package com.pointagepro.organization.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.organization.dto.DepartmentRequest;
import com.pointagepro.organization.dto.DepartmentResponse;
import com.pointagepro.organization.service.OrganizationService;
import com.pointagepro.shared.ApiResponse;
import com.pointagepro.shared.dto.LookupItem;
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
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final OrganizationService organizationService;
    private final CurrentUserService currentUserService;

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('department.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> lookup(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des départements",
                organizationService.departmentLookup(company)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('department.read')")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> list(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des départements",
                organizationService.listDepartments(company)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department.read')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> get(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Département",
                organizationService.getDepartment(company, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('department.write')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @AuthenticationPrincipal User user, @Valid @RequestBody DepartmentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Département créé", organizationService.createDepartment(company, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department.write')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Département mis à jour",
                organizationService.updateDepartment(company, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department.write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        organizationService.deleteDepartment(company, id);
        return ResponseEntity.ok(ApiResponse.success("Département supprimé"));
    }
}
