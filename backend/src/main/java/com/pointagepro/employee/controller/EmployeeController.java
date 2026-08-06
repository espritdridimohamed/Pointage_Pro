package com.pointagepro.employee.controller;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.service.CurrentUserService;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.dto.AssignmentResponse;
import com.pointagepro.employee.dto.BankAccountRequest;
import com.pointagepro.employee.dto.BankAccountResponse;
import com.pointagepro.employee.dto.DependentRequest;
import com.pointagepro.employee.dto.DependentResponse;
import com.pointagepro.employee.dto.DocumentRequest;
import com.pointagepro.employee.dto.DocumentResponse;
import com.pointagepro.employee.dto.EmergencyContactRequest;
import com.pointagepro.employee.dto.EmergencyContactResponse;
import com.pointagepro.employee.dto.EmployeeCountResponse;
import com.pointagepro.employee.dto.EmployeeRequest;
import com.pointagepro.employee.dto.EmployeeResponse;
import com.pointagepro.employee.dto.TaxProfileRequest;
import com.pointagepro.employee.dto.TaxProfileResponse;
import com.pointagepro.employee.service.EmployeeRecordService;
import com.pointagepro.employee.service.EmployeeService;
import com.pointagepro.shared.ApiResponse;
import com.pointagepro.shared.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRecordService employeeRecordService;
    private final CurrentUserService currentUserService;

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<String>>> departments(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Liste des départements",
                employeeService.departmentNames(company)));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<EmployeeCountResponse>> count(@AuthenticationPrincipal User user) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Nombre d'employés",
                new EmployeeCountResponse(employeeService.count(company))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department) {
        Company company = currentUserService.requireCompany(user);
        size = Math.min(Math.max(size, 1), 100);
        Page<EmployeeResponse> result = employeeService.list(company, search, department,
                PageRequest.of(Math.max(page, 0), size));
        return ResponseEntity.ok(ApiResponse.success("Liste des employés",
                new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                        result.getTotalElements(), result.getTotalPages())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> get(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Employé",
                employeeService.get(company, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(
            @AuthenticationPrincipal User user, @Valid @RequestBody EmployeeRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Employé créé", employeeService.create(company, request, user)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Employé mis à jour",
                employeeService.update(company, id, request, user)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.delete')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        employeeService.terminate(company, id);
        return ResponseEntity.ok(ApiResponse.success("Employé résilié"));
    }

    // ------------------------------------------------------------- documents

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('document.read')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> documents(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Documents de l'employé",
                employeeRecordService.listDocuments(company, id)));
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('document.write')")
    public ResponseEntity<ApiResponse<DocumentResponse>> createDocument(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody DocumentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document ajouté",
                employeeRecordService.createDocument(company, id, request)));
    }

    @GetMapping("/{id}/documents/{docId}")
    @PreAuthorize("hasAuthority('document.read')")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long docId) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Document",
                employeeRecordService.getDocument(company, id, docId)));
    }

    @PutMapping("/{id}/documents/{docId}")
    @PreAuthorize("hasAuthority('document.write')")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateDocument(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long docId,
            @RequestBody DocumentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Document mis à jour",
                employeeRecordService.updateDocument(company, id, docId, request)));
    }

    @DeleteMapping("/{id}/documents/{docId}")
    @PreAuthorize("hasAuthority('document.write')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long docId) {
        Company company = currentUserService.requireCompany(user);
        employeeRecordService.deleteDocument(company, id, docId);
        return ResponseEntity.ok(ApiResponse.success("Document supprimé"));
    }

    // ------------------------------------------------------------- bank accounts

    @GetMapping("/{id}/bank-accounts")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> bankAccounts(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Comptes bancaires de l'employé",
                employeeRecordService.listBankAccounts(company, id)));
    }

    @PostMapping("/{id}/bank-accounts")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<BankAccountResponse>> createBankAccount(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody BankAccountRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Compte bancaire ajouté",
                employeeRecordService.createBankAccount(company, id, request)));
    }

    @GetMapping("/{id}/bank-accounts/{baId}")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<BankAccountResponse>> getBankAccount(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long baId) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Compte bancaire",
                employeeRecordService.getBankAccount(company, id, baId)));
    }

    @PutMapping("/{id}/bank-accounts/{baId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<BankAccountResponse>> updateBankAccount(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long baId,
            @RequestBody BankAccountRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Compte bancaire mis à jour",
                employeeRecordService.updateBankAccount(company, id, baId, request)));
    }

    @DeleteMapping("/{id}/bank-accounts/{baId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<Void>> deleteBankAccount(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long baId) {
        Company company = currentUserService.requireCompany(user);
        employeeRecordService.deleteBankAccount(company, id, baId);
        return ResponseEntity.ok(ApiResponse.success("Compte bancaire supprimé"));
    }

    // ------------------------------------------------------------- dependents

    @GetMapping("/{id}/dependents")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<DependentResponse>>> dependents(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Personnes à charge",
                employeeRecordService.listDependents(company, id)));
    }

    @PostMapping("/{id}/dependents")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<DependentResponse>> createDependent(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody DependentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Personne à charge ajoutée",
                employeeRecordService.createDependent(company, id, request)));
    }

    @GetMapping("/{id}/dependents/{depId}")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<DependentResponse>> getDependent(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long depId) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Personne à charge",
                employeeRecordService.getDependent(company, id, depId)));
    }

    @PutMapping("/{id}/dependents/{depId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<DependentResponse>> updateDependent(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long depId,
            @RequestBody DependentRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Personne à charge mise à jour",
                employeeRecordService.updateDependent(company, id, depId, request)));
    }

    @DeleteMapping("/{id}/dependents/{depId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<Void>> deleteDependent(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long depId) {
        Company company = currentUserService.requireCompany(user);
        employeeRecordService.deleteDependent(company, id, depId);
        return ResponseEntity.ok(ApiResponse.success("Personne à charge supprimée"));
    }

    // ------------------------------------------------------------- emergency contacts

    @GetMapping("/{id}/emergency-contacts")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> emergencyContacts(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Contacts d'urgence",
                employeeRecordService.listEmergencyContacts(company, id)));
    }

    @PostMapping("/{id}/emergency-contacts")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> createEmergencyContact(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody EmergencyContactRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Contact d'urgence ajouté",
                employeeRecordService.createEmergencyContact(company, id, request)));
    }

    @GetMapping("/{id}/emergency-contacts/{ecId}")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> getEmergencyContact(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long ecId) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Contact d'urgence",
                employeeRecordService.getEmergencyContact(company, id, ecId)));
    }

    @PutMapping("/{id}/emergency-contacts/{ecId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<EmergencyContactResponse>> updateEmergencyContact(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long ecId,
            @RequestBody EmergencyContactRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Contact d'urgence mis à jour",
                employeeRecordService.updateEmergencyContact(company, id, ecId, request)));
    }

    @DeleteMapping("/{id}/emergency-contacts/{ecId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<Void>> deleteEmergencyContact(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long ecId) {
        Company company = currentUserService.requireCompany(user);
        employeeRecordService.deleteEmergencyContact(company, id, ecId);
        return ResponseEntity.ok(ApiResponse.success("Contact d'urgence supprimé"));
    }

    // ------------------------------------------------------------- tax profiles

    @GetMapping("/{id}/tax-profiles")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<TaxProfileResponse>>> taxProfiles(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Profils fiscaux de l'employé",
                employeeRecordService.listTaxProfiles(company, id)));
    }

    @PostMapping("/{id}/tax-profiles")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<TaxProfileResponse>> createTaxProfile(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestBody TaxProfileRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Profil fiscal créé",
                employeeRecordService.createTaxProfile(company, id, request)));
    }

    @PutMapping("/{id}/tax-profiles/{tpId}")
    @PreAuthorize("hasAuthority('employee.write')")
    public ResponseEntity<ApiResponse<TaxProfileResponse>> updateTaxProfile(
            @AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long tpId,
            @RequestBody TaxProfileRequest request) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Profil fiscal mis à jour",
                employeeRecordService.updateTaxProfile(company, id, tpId, request)));
    }

    // ------------------------------------------------------------- assignment history

    @GetMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> assignments(
            @AuthenticationPrincipal User user, @PathVariable Long id) {
        Company company = currentUserService.requireCompany(user);
        return ResponseEntity.ok(ApiResponse.success("Historique des affectations",
                employeeRecordService.listAssignments(company, id)));
    }
}
