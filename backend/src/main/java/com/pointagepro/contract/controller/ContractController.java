package com.pointagepro.contract.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.dto.ContractRequest;
import com.pointagepro.contract.dto.ContractResponse;
import com.pointagepro.contract.dto.SalaryComponentRequest;
import com.pointagepro.contract.dto.SalaryComponentResponse;
import com.pointagepro.contract.dto.SalaryHistoryResponse;
import com.pointagepro.contract.service.ContractService;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
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
@RequestMapping
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final CurrentUserService currentUserService;
    private final EmployeeRepository employeeRepository;

    // ------------------------------------------------------------- contracts

    @GetMapping("/employees/{employeeId}/contracts")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<List<ContractResponse>>> listContracts(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId) {
        Company company = currentUserService.requireCompany(user);
        requireEmployee(company, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Contrats de l'employé",
                contractService.listContracts(company, employeeId)));
    }

    @PostMapping("/employees/{employeeId}/contracts")
    @PreAuthorize("hasAuthority('contract.write')")
    public ResponseEntity<ApiResponse<ContractResponse>> createContract(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId,
            @Valid @RequestBody ContractRequest request) {
        Company company = currentUserService.requireCompany(user);
        Employee employee = requireEmployee(company, employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Contrat créé", contractService.createContract(company, employee, request, user)));
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<ContractResponse>> getContract(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Contrat",
                contractService.getContract(company, id)));
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('contract.write')")
    public ResponseEntity<ApiResponse<ContractResponse>> updateContract(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody ContractRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Contrat mis à jour",
                contractService.updateContract(company, id, request, user)));
    }

    @DeleteMapping("/contracts/{id}")
    @PreAuthorize("hasAuthority('contract.write')")
    public ResponseEntity<ApiResponse<Void>> deleteContract(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        contractService.deleteContract(company, id);
        return ResponseEntity.ok(ApiResponse.success("Contrat supprimé"));
    }

    // ------------------------------------------------------------- components

    @GetMapping("/contracts/{id}/components")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<List<SalaryComponentResponse>>> listComponents(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Composantes de salaire",
                contractService.listComponents(company, id)));
    }

    @PostMapping("/contracts/{id}/components")
    @PreAuthorize("hasAuthority('contract.write')")
    public ResponseEntity<ApiResponse<SalaryComponentResponse>> createComponent(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody SalaryComponentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Composante créée", contractService.createComponent(company, id, request, user)));
    }

    @GetMapping("/contracts/{id}/components/{cid}")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<SalaryComponentResponse>> getComponent(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long cid) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Composante de salaire",
                contractService.getComponent(company, id, cid)));
    }

    @PutMapping("/contracts/{id}/components/{cid}")
    @PreAuthorize("hasAuthority('contract.write')")
    public ResponseEntity<ApiResponse<SalaryComponentResponse>> updateComponent(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long cid,
            @Valid @RequestBody SalaryComponentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Composante mise à jour",
                contractService.updateComponent(company, id, cid, request, user)));
    }

    @DeleteMapping("/contracts/{id}/components/{cid}")
    @PreAuthorize("hasAuthority('contract.write')")
    public ResponseEntity<ApiResponse<Void>> deleteComponent(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long cid) {
        Company company = currentUserService.requireCompany(user);
        contractService.deleteComponent(company, id, cid);
        return ResponseEntity.ok(ApiResponse.success("Composante supprimée"));
    }

    // ------------------------------------------------------------- salary history

    @GetMapping("/employees/{employeeId}/salary-history")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<List<SalaryHistoryResponse>>> salaryHistory(
            @AuthenticationPrincipal User user, @PathVariable Long employeeId) {
        Company company = currentUserService.requireCompany(user);
        Employee employee = requireEmployee(company, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Historique des salaires",
                contractService.salaryHistory(employee)));
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
