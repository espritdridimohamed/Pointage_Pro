package com.pointagepro.lookup.controller;

import com.pointagepro.contract.entity.ContractStatus;
import com.pointagepro.contract.entity.ContractType;
import com.pointagepro.contract.entity.SalaryComponentType;
import com.pointagepro.contract.repository.ContractStatusRepository;
import com.pointagepro.contract.repository.ContractTypeRepository;
import com.pointagepro.contract.repository.SalaryComponentTypeRepository;
import com.pointagepro.employee.entity.Bank;
import com.pointagepro.employee.entity.DependentRelationship;
import com.pointagepro.employee.entity.DocumentType;
import com.pointagepro.employee.entity.EmployeeStatus;
import com.pointagepro.employee.entity.Gender;
import com.pointagepro.employee.entity.MaritalStatus;
import com.pointagepro.employee.entity.TaxSituation;
import com.pointagepro.employee.repository.BankRepository;
import com.pointagepro.employee.repository.DependentRelationshipRepository;
import com.pointagepro.employee.repository.DocumentTypeRepository;
import com.pointagepro.employee.repository.EmployeeStatusRepository;
import com.pointagepro.employee.repository.GenderRepository;
import com.pointagepro.employee.repository.MaritalStatusRepository;
import com.pointagepro.employee.repository.TaxSituationRepository;
import com.pointagepro.lookup.dto.SalaryComponentTypeItem;
import com.pointagepro.shared.ApiResponse;
import com.pointagepro.shared.dto.LookupItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only lookup endpoints for dropdowns (EMPLOYEE_API_CONTRACT §3). All are global
 * reference data; company-scoping is not required but the endpoints still require an
 * authenticated user with the matching module read permission.
 */
@RestController
@RequestMapping("/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final EmployeeStatusRepository employeeStatusRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final ContractStatusRepository contractStatusRepository;
    private final GenderRepository genderRepository;
    private final MaritalStatusRepository maritalStatusRepository;
    private final BankRepository bankRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DependentRelationshipRepository dependentRelationshipRepository;
    private final TaxSituationRepository taxSituationRepository;
    private final SalaryComponentTypeRepository salaryComponentTypeRepository;

    @GetMapping("/employee-statuses")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> employeeStatuses() {
        return ResponseEntity.ok(ApiResponse.success("Statuts employé",
                employeeStatusRepository.findAll().stream()
                        .sorted(Comparator.comparing(EmployeeStatus::getCode))
                        .map(s -> new LookupItem(null, s.getCode(), s.getLabel()))
                        .toList()));
    }

    @GetMapping("/contract-types")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> contractTypes() {
        return ResponseEntity.ok(ApiResponse.success("Types de contrat",
                contractTypeRepository.findAll().stream()
                        .sorted(Comparator.comparing(ContractType::getCode))
                        .map(t -> new LookupItem(null, t.getCode(), t.getLabel()))
                        .toList()));
    }

    @GetMapping("/contract-statuses")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> contractStatuses() {
        return ResponseEntity.ok(ApiResponse.success("Statuts de contrat",
                contractStatusRepository.findAll().stream()
                        .sorted(Comparator.comparing(ContractStatus::getCode))
                        .map(s -> new LookupItem(null, s.getCode(), s.getLabel()))
                        .toList()));
    }

    @GetMapping("/genders")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> genders() {
        return ResponseEntity.ok(ApiResponse.success("Genres",
                genderRepository.findAll().stream()
                        .sorted(Comparator.comparing(Gender::getCode))
                        .map(g -> new LookupItem(null, g.getCode(), g.getLabel()))
                        .toList()));
    }

    @GetMapping("/marital-statuses")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> maritalStatuses() {
        return ResponseEntity.ok(ApiResponse.success("Situations familiales",
                maritalStatusRepository.findAll().stream()
                        .sorted(Comparator.comparing(MaritalStatus::getCode))
                        .map(m -> new LookupItem(null, m.getCode(), m.getLabel()))
                        .toList()));
    }

    @GetMapping("/banks")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> banks() {
        return ResponseEntity.ok(ApiResponse.success("Banques",
                bankRepository.findAll().stream()
                        .sorted(Comparator.comparing(Bank::getCode))
                        .map(b -> new LookupItem(null, b.getCode(), b.getName()))
                        .toList()));
    }

    @GetMapping("/document-types")
    @PreAuthorize("hasAuthority('document.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> documentTypes() {
        return ResponseEntity.ok(ApiResponse.success("Types de document",
                documentTypeRepository.findAll().stream()
                        .sorted(Comparator.comparing(DocumentType::getCode))
                        .map(d -> new LookupItem(null, d.getCode(), d.getLabel()))
                        .toList()));
    }

    @GetMapping("/dependent-relationships")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> dependentRelationships() {
        return ResponseEntity.ok(ApiResponse.success("Liens de parenté",
                dependentRelationshipRepository.findAll().stream()
                        .sorted(Comparator.comparing(DependentRelationship::getCode))
                        .map(r -> new LookupItem(null, r.getCode(), r.getLabel()))
                        .toList()));
    }

    @GetMapping("/tax-situations")
    @PreAuthorize("hasAuthority('employee.read')")
    public ResponseEntity<ApiResponse<List<LookupItem>>> taxSituations() {
        return ResponseEntity.ok(ApiResponse.success("Situations fiscales",
                taxSituationRepository.findAll().stream()
                        .sorted(Comparator.comparing(TaxSituation::getCode))
                        .map(t -> new LookupItem(null, t.getCode(), t.getLabel()))
                        .toList()));
    }

    @GetMapping("/salary-component-types")
    @PreAuthorize("hasAuthority('contract.read')")
    public ResponseEntity<ApiResponse<List<SalaryComponentTypeItem>>> salaryComponentTypes() {
        return ResponseEntity.ok(ApiResponse.success("Types de composante de salaire",
                salaryComponentTypeRepository.findAll().stream()
                        .sorted(Comparator.comparing(SalaryComponentType::getCode))
                        .map(t -> new SalaryComponentTypeItem(t.getCode(), t.getLabel(), t.getCategory()))
                        .toList()));
    }
}
