package com.pointagepro.employee.service;

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
import com.pointagepro.employee.dto.TaxProfileRequest;
import com.pointagepro.employee.dto.TaxProfileResponse;
import com.pointagepro.employee.entity.Bank;
import com.pointagepro.employee.entity.DependentRelationship;
import com.pointagepro.employee.entity.DocumentType;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.entity.EmployeeAssignment;
import com.pointagepro.employee.entity.EmployeeBankAccount;
import com.pointagepro.employee.entity.EmployeeDependent;
import com.pointagepro.employee.entity.EmployeeDocument;
import com.pointagepro.employee.entity.EmployeeEmergencyContact;
import com.pointagepro.employee.entity.EmployeeTaxProfile;
import com.pointagepro.employee.entity.TaxSituation;
import com.pointagepro.employee.repository.BankRepository;
import com.pointagepro.employee.repository.DependentRelationshipRepository;
import com.pointagepro.employee.repository.DocumentTypeRepository;
import com.pointagepro.employee.repository.EmployeeAssignmentRepository;
import com.pointagepro.employee.repository.EmployeeBankAccountRepository;
import com.pointagepro.employee.repository.EmployeeDependentRepository;
import com.pointagepro.employee.repository.EmployeeDocumentRepository;
import com.pointagepro.employee.repository.EmployeeEmergencyContactRepository;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.employee.repository.EmployeeTaxProfileRepository;
import com.pointagepro.employee.repository.TaxSituationRepository;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Employee child records (Phase B): documents, bank accounts, dependents, emergency
 * contacts and tax profiles. Company-scoped via the employee. Tax profile creation keeps
 * a single open row per employee (creating closes the current one; back-dating over the
 * current profile is rejected). Bank account creation/update keeps a single default.
 */
@Service
@RequiredArgsConstructor
public class EmployeeRecordService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final EmployeeBankAccountRepository bankAccountRepository;
    private final BankRepository bankRepository;
    private final EmployeeDependentRepository dependentRepository;
    private final DependentRelationshipRepository relationshipRepository;
    private final EmployeeEmergencyContactRepository emergencyContactRepository;
    private final EmployeeTaxProfileRepository taxProfileRepository;
    private final TaxSituationRepository taxSituationRepository;
    private final EmployeeAssignmentRepository assignmentRepository;

    // ------------------------------------------------------------- documents

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(Company company, Long employeeId) {
        requireEmployee(company, employeeId);
        return documentRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Company company, Long employeeId, Long docId) {
        return toDocumentResponse(requireDocument(company, employeeId, docId));
    }

    @Transactional
    public DocumentResponse createDocument(Company company, Long employeeId, DocumentRequest req) {
        Employee e = requireEmployee(company, employeeId);
        if (req.getFilePath() == null || req.getFilePath().isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (req.getDocumentTypeId() == null) {
            throw new IllegalArgumentException("documentTypeId is required");
        }
        DocumentType type = documentTypeRepository.findById(req.getDocumentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("DocumentType", "id", req.getDocumentTypeId()));
        if (req.getExpiryDate() != null && req.getIssueDate() != null
                && req.getExpiryDate().isBefore(req.getIssueDate())) {
            throw new IllegalArgumentException("expiryDate doit être >= issueDate");
        }
        EmployeeDocument d = new EmployeeDocument();
        d.setEmployee(e);
        d.setDocumentType(type);
        d.setFilePath(req.getFilePath().trim());
        d.setDocumentNumber(trimToNull(req.getDocumentNumber()));
        d.setIssueDate(req.getIssueDate());
        d.setExpiryDate(req.getExpiryDate());
        d.setNotes(trimToNull(req.getNotes()));
        return toDocumentResponse(documentRepository.save(d));
    }

    @Transactional
    public DocumentResponse updateDocument(Company company, Long employeeId, Long docId, DocumentRequest req) {
        EmployeeDocument d = requireDocument(company, employeeId, docId);
        if (req.getDocumentTypeId() != null) {
            d.setDocumentType(documentTypeRepository.findById(req.getDocumentTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("DocumentType", "id", req.getDocumentTypeId())));
        }
        if (req.getFilePath() != null && !req.getFilePath().isBlank()) {
            d.setFilePath(req.getFilePath().trim());
        }
        d.setDocumentNumber(trimToNull(req.getDocumentNumber()));
        d.setIssueDate(req.getIssueDate());
        d.setExpiryDate(req.getExpiryDate());
        d.setNotes(trimToNull(req.getNotes()));
        if (d.getExpiryDate() != null && d.getIssueDate() != null
                && d.getExpiryDate().isBefore(d.getIssueDate())) {
            throw new IllegalArgumentException("expiryDate doit être >= issueDate");
        }
        return toDocumentResponse(documentRepository.save(d));
    }

    @Transactional
    public void deleteDocument(Company company, Long employeeId, Long docId) {
        requireDocument(company, employeeId, docId);
        documentRepository.deleteById(docId);
    }

    // ------------------------------------------------------------- bank accounts

    @Transactional(readOnly = true)
    public List<BankAccountResponse> listBankAccounts(Company company, Long employeeId) {
        requireEmployee(company, employeeId);
        return bankAccountRepository.findByEmployeeIdOrderByValidFromDesc(employeeId).stream()
                .map(this::toBankAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BankAccountResponse getBankAccount(Company company, Long employeeId, Long baId) {
        return toBankAccountResponse(requireBankAccount(company, employeeId, baId));
    }

    @Transactional
    public BankAccountResponse createBankAccount(Company company, Long employeeId, BankAccountRequest req) {
        Employee e = requireEmployee(company, employeeId);
        BankAccountHolder holder = validateBankAccount(req);
        EmployeeBankAccount acc = new EmployeeBankAccount();
        acc.setEmployee(e);
        acc.setBank(holder.bank());
        acc.setAccountNumber(trimToNull(req.getAccountNumber()));
        acc.setIban(trimToNull(req.getIban()));
        acc.setAccountHolder(trimToNull(req.getAccountHolder()));
        acc.setIsDefault(holder.isDefault());
        acc.setValidFrom(holder.validFrom());
        acc.setValidTo(req.getValidTo());
        enforceSingleDefault(e, acc);
        return toBankAccountResponse(bankAccountRepository.save(acc));
    }

    @Transactional
    public BankAccountResponse updateBankAccount(Company company, Long employeeId, Long baId,
                                                 BankAccountRequest req) {
        EmployeeBankAccount acc = requireBankAccount(company, employeeId, baId);
        if (req.getBankId() != null) {
            acc.setBank(bankRepository.findById(req.getBankId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bank", "id", req.getBankId())));
        }
        acc.setAccountNumber(trimToNull(req.getAccountNumber()));
        acc.setIban(trimToNull(req.getIban()));
        acc.setAccountHolder(trimToNull(req.getAccountHolder()));
        if (req.getIsDefault() != null) {
            acc.setIsDefault(req.getIsDefault());
        }
        if (req.getValidFrom() != null) {
            acc.setValidFrom(req.getValidFrom());
        }
        acc.setValidTo(req.getValidTo());
        if (acc.getValidTo() != null && acc.getValidTo().isBefore(acc.getValidFrom())) {
            throw new IllegalArgumentException("validTo doit être >= validFrom");
        }
        enforceSingleDefault(acc.getEmployee(), acc);
        return toBankAccountResponse(bankAccountRepository.save(acc));
    }

    @Transactional
    public void deleteBankAccount(Company company, Long employeeId, Long baId) {
        requireBankAccount(company, employeeId, baId);
        bankAccountRepository.deleteById(baId);
    }

    // ------------------------------------------------------------- dependents

    @Transactional(readOnly = true)
    public List<DependentResponse> listDependents(Company company, Long employeeId) {
        requireEmployee(company, employeeId);
        return dependentRepository.findByEmployeeId(employeeId).stream()
                .map(this::toDependentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DependentResponse getDependent(Company company, Long employeeId, Long depId) {
        return toDependentResponse(requireDependent(company, employeeId, depId));
    }

    @Transactional
    public DependentResponse createDependent(Company company, Long employeeId, DependentRequest req) {
        Employee e = requireEmployee(company, employeeId);
        DependentRelationship rel = resolveRelationship(req.getRelationshipId());
        EmployeeDependent d = new EmployeeDependent();
        d.setEmployee(e);
        d.setFirstName(requireText(req.getFirstName(), "firstName"));
        d.setLastName(requireText(req.getLastName(), "lastName"));
        d.setCin(trimToNull(req.getCin()));
        d.setBirthDate(req.getBirthDate());
        d.setRelationship(rel);
        d.setTaxDeductible(req.getTaxDeductible() != null ? req.getTaxDeductible() : true);
        return toDependentResponse(dependentRepository.save(d));
    }

    @Transactional
    public DependentResponse updateDependent(Company company, Long employeeId, Long depId, DependentRequest req) {
        EmployeeDependent d = requireDependent(company, employeeId, depId);
        if (req.getFirstName() != null) {
            d.setFirstName(requireText(req.getFirstName(), "firstName"));
        }
        if (req.getLastName() != null) {
            d.setLastName(requireText(req.getLastName(), "lastName"));
        }
        d.setCin(trimToNull(req.getCin()));
        d.setBirthDate(req.getBirthDate());
        if (req.getRelationshipId() != null) {
            d.setRelationship(resolveRelationship(req.getRelationshipId()));
        }
        if (req.getTaxDeductible() != null) {
            d.setTaxDeductible(req.getTaxDeductible());
        }
        return toDependentResponse(dependentRepository.save(d));
    }

    @Transactional
    public void deleteDependent(Company company, Long employeeId, Long depId) {
        requireDependent(company, employeeId, depId);
        dependentRepository.deleteById(depId);
    }

    // ------------------------------------------------------------- emergency contacts

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> listEmergencyContacts(Company company, Long employeeId) {
        requireEmployee(company, employeeId);
        return emergencyContactRepository.findByEmployeeId(employeeId).stream()
                .map(this::toEmergencyContactResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmergencyContactResponse getEmergencyContact(Company company, Long employeeId, Long ecId) {
        return toEmergencyContactResponse(requireEmergencyContact(company, employeeId, ecId));
    }

    @Transactional
    public EmergencyContactResponse createEmergencyContact(Company company, Long employeeId,
                                                           EmergencyContactRequest req) {
        Employee e = requireEmployee(company, employeeId);
        EmployeeEmergencyContact c = new EmployeeEmergencyContact();
        c.setEmployee(e);
        c.setFullName(requireText(req.getFullName(), "fullName"));
        c.setRelationship(trimToNull(req.getRelationship()));
        c.setPhone(requireText(req.getPhone(), "phone"));
        c.setAddress(trimToNull(req.getAddress()));
        return toEmergencyContactResponse(emergencyContactRepository.save(c));
    }

    @Transactional
    public EmergencyContactResponse updateEmergencyContact(Company company, Long employeeId, Long ecId,
                                                           EmergencyContactRequest req) {
        EmployeeEmergencyContact c = requireEmergencyContact(company, employeeId, ecId);
        if (req.getFullName() != null) {
            c.setFullName(requireText(req.getFullName(), "fullName"));
        }
        c.setRelationship(trimToNull(req.getRelationship()));
        if (req.getPhone() != null) {
            c.setPhone(requireText(req.getPhone(), "phone"));
        }
        c.setAddress(trimToNull(req.getAddress()));
        return toEmergencyContactResponse(emergencyContactRepository.save(c));
    }

    @Transactional
    public void deleteEmergencyContact(Company company, Long employeeId, Long ecId) {
        requireEmergencyContact(company, employeeId, ecId);
        emergencyContactRepository.deleteById(ecId);
    }

    // ------------------------------------------------------------- tax profiles

    @Transactional(readOnly = true)
    public List<TaxProfileResponse> listTaxProfiles(Company company, Long employeeId) {
        requireEmployee(company, employeeId);
        return taxProfileRepository.findByEmployeeIdOrderByValidFromDesc(employeeId).stream()
                .map(this::toTaxProfileResponse)
                .toList();
    }

    @Transactional
    public TaxProfileResponse createTaxProfile(Company company, Long employeeId, TaxProfileRequest req) {
        Employee e = requireEmployee(company, employeeId);
        validateTaxProfile(req);
        EmployeeTaxProfile p = new EmployeeTaxProfile();
        p.setEmployee(e);
        p.setTaxSituation(taxSituationRepository.findById(req.getTaxSituationId())
                .orElseThrow(() -> new ResourceNotFoundException("TaxSituation", "id", req.getTaxSituationId())));
        p.setSpouseIsWorking(Boolean.TRUE.equals(req.getSpouseIsWorking()));
        p.setNumberOfChildren(req.getNumberOfChildren() != null ? req.getNumberOfChildren() : 0);
        p.setNumberOfDisabledChildren(req.getNumberOfDisabledChildren() != null
                ? req.getNumberOfDisabledChildren() : 0);
        p.setValidFrom(req.getValidFrom());
        p.setValidTo(req.getValidTo());
        closeCurrentTaxProfile(e, p);
        return toTaxProfileResponse(taxProfileRepository.save(p));
    }

    @Transactional
    public TaxProfileResponse updateTaxProfile(Company company, Long employeeId, Long tpId,
                                               TaxProfileRequest req) {
        EmployeeTaxProfile p = requireTaxProfile(company, employeeId, tpId);
        validateTaxProfile(req);
        if (req.getTaxSituationId() != null) {
            p.setTaxSituation(taxSituationRepository.findById(req.getTaxSituationId())
                    .orElseThrow(() -> new ResourceNotFoundException("TaxSituation", "id", req.getTaxSituationId())));
        }
        if (req.getSpouseIsWorking() != null) {
            p.setSpouseIsWorking(req.getSpouseIsWorking());
        }
        if (req.getNumberOfChildren() != null) {
            p.setNumberOfChildren(req.getNumberOfChildren());
        }
        if (req.getNumberOfDisabledChildren() != null) {
            p.setNumberOfDisabledChildren(req.getNumberOfDisabledChildren());
        }
        if (req.getValidFrom() != null) {
            p.setValidFrom(req.getValidFrom());
        }
        p.setValidTo(req.getValidTo());
        assertSingleOpen(employeeId, p);
        return toTaxProfileResponse(taxProfileRepository.save(p));
    }

    // ------------------------------------------------------------- assignment history

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listAssignments(Company company, Long employeeId) {
        requireEmployee(company, employeeId);
        return assignmentRepository.findByEmployeeIdOrderByValidFromDesc(employeeId).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    // ------------------------------------------------------------- private

    private record BankAccountHolder(Bank bank, Boolean isDefault, LocalDate validFrom) {
    }

    private BankAccountHolder validateBankAccount(BankAccountRequest req) {
        if (req.getBankId() == null) {
            throw new IllegalArgumentException("bankId is required");
        }
        Bank bank = bankRepository.findById(req.getBankId())
                .orElseThrow(() -> new ResourceNotFoundException("Bank", "id", req.getBankId()));
        LocalDate from = req.getValidFrom();
        if (from == null) {
            throw new IllegalArgumentException("validFrom is required");
        }
        if (req.getValidTo() != null && req.getValidTo().isBefore(from)) {
            throw new IllegalArgumentException("validTo doit être >= validFrom");
        }
        return new BankAccountHolder(bank, Boolean.TRUE.equals(req.getIsDefault()), from);
    }

    private void enforceSingleDefault(Employee employee, EmployeeBankAccount updated) {
        if (!Boolean.TRUE.equals(updated.getIsDefault())) {
            return;
        }
        bankAccountRepository.findByEmployeeIdAndIsDefaultTrue(employee.getId())
                .filter(o -> updated.getId() == null || !updated.getId().equals(o.getId()))
                .ifPresent(o -> {
                    o.setIsDefault(false);
                    bankAccountRepository.save(o);
                });
    }

    private void validateTaxProfile(TaxProfileRequest req) {
        if (req.getTaxSituationId() == null) {
            throw new IllegalArgumentException("taxSituationId is required");
        }
        if (req.getValidFrom() == null) {
            throw new IllegalArgumentException("validFrom is required");
        }
        if (req.getValidTo() != null && req.getValidTo().isBefore(req.getValidFrom())) {
            throw new IllegalArgumentException("validTo doit être >= validFrom");
        }
        int children = req.getNumberOfChildren() != null ? req.getNumberOfChildren() : 0;
        int disabled = req.getNumberOfDisabledChildren() != null ? req.getNumberOfDisabledChildren() : 0;
        if (children < 0 || disabled < 0) {
            throw new IllegalArgumentException("numberOfChildren/numberOfDisabledChildren doivent être >= 0");
        }
        if (disabled > children) {
            throw new IllegalArgumentException("numberOfDisabledChildren doit être <= numberOfChildren");
        }
    }

    private void closeCurrentTaxProfile(Employee e, EmployeeTaxProfile created) {
        taxProfileRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(e.getId())
                .ifPresent(current -> {
                    if (created.getValidFrom().isBefore(current.getValidFrom())) {
                        throw new ConflictException("Un profil fiscal actif couvre déjà cette période depuis "
                                + current.getValidFrom() + "; impossible de rétro-dater");
                    }
                    current.setValidTo(created.getValidFrom().minusDays(1));
                    taxProfileRepository.save(current);
                });
    }

    private void assertSingleOpen(Long employeeId, EmployeeTaxProfile updated) {
        if (updated.getValidTo() != null) {
            return;
        }
        taxProfileRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(employeeId)
                .filter(o -> updated.getId() == null || !updated.getId().equals(o.getId()))
                .ifPresent(o -> {
                    throw new ConflictException(
                            "Un profil fiscal ouvert existe déjà; fermez-le d'abord");
                });
    }

    // ------------------------------------------------------------- scoping

    private Employee requireEmployee(Company company, Long employeeId) {
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        if (!e.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
        return e;
    }

    private EmployeeDocument requireDocument(Company company, Long employeeId, Long docId) {
        EmployeeDocument d = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeDocument", "id", docId));
        if (!d.getEmployee().getId().equals(employeeId) || !d.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeDocument", "id", docId);
        }
        return d;
    }

    private EmployeeBankAccount requireBankAccount(Company company, Long employeeId, Long baId) {
        EmployeeBankAccount a = bankAccountRepository.findById(baId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeBankAccount", "id", baId));
        if (!a.getEmployee().getId().equals(employeeId) || !a.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeBankAccount", "id", baId);
        }
        return a;
    }

    private EmployeeDependent requireDependent(Company company, Long employeeId, Long depId) {
        EmployeeDependent d = dependentRepository.findById(depId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeDependent", "id", depId));
        if (!d.getEmployee().getId().equals(employeeId) || !d.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeDependent", "id", depId);
        }
        return d;
    }

    private EmployeeEmergencyContact requireEmergencyContact(Company company, Long employeeId, Long ecId) {
        EmployeeEmergencyContact c = emergencyContactRepository.findById(ecId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeEmergencyContact", "id", ecId));
        if (!c.getEmployee().getId().equals(employeeId) || !c.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeEmergencyContact", "id", ecId);
        }
        return c;
    }

    private EmployeeTaxProfile requireTaxProfile(Company company, Long employeeId, Long tpId) {
        EmployeeTaxProfile p = taxProfileRepository.findById(tpId)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeTaxProfile", "id", tpId));
        if (!p.getEmployee().getId().equals(employeeId) || !p.getEmployee().getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeTaxProfile", "id", tpId);
        }
        return p;
    }

    private DependentRelationship resolveRelationship(Long relationshipId) {
        if (relationshipId == null) {
            throw new IllegalArgumentException("relationshipId is required");
        }
        return relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new ResourceNotFoundException("DependentRelationship", "id", relationshipId));
    }

    // ------------------------------------------------------------- response mapping

    private DocumentResponse toDocumentResponse(EmployeeDocument d) {
        DocumentResponse dto = new DocumentResponse();
        dto.setId(d.getId());
        dto.setDocumentTypeId(d.getDocumentType().getId());
        dto.setDocumentType(d.getDocumentType().getLabel());
        dto.setFilePath(d.getFilePath());
        dto.setDocumentNumber(d.getDocumentNumber());
        dto.setIssueDate(d.getIssueDate());
        dto.setExpiryDate(d.getExpiryDate());
        dto.setNotes(d.getNotes());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    private BankAccountResponse toBankAccountResponse(EmployeeBankAccount a) {
        BankAccountResponse dto = new BankAccountResponse();
        dto.setId(a.getId());
        dto.setBankId(a.getBank().getId());
        dto.setBankCode(a.getBank().getCode());
        dto.setBankName(a.getBank().getName());
        dto.setAccountNumber(a.getAccountNumber());
        dto.setIban(a.getIban());
        dto.setAccountHolder(a.getAccountHolder());
        dto.setIsDefault(a.getIsDefault());
        dto.setValidFrom(a.getValidFrom());
        dto.setValidTo(a.getValidTo());
        return dto;
    }

    private DependentResponse toDependentResponse(EmployeeDependent d) {
        DependentResponse dto = new DependentResponse();
        dto.setId(d.getId());
        dto.setFirstName(d.getFirstName());
        dto.setLastName(d.getLastName());
        dto.setCin(d.getCin());
        dto.setBirthDate(d.getBirthDate());
        dto.setRelationshipId(d.getRelationship().getId());
        dto.setRelationshipCode(d.getRelationship().getCode());
        dto.setTaxDeductible(d.getTaxDeductible());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    private EmergencyContactResponse toEmergencyContactResponse(EmployeeEmergencyContact c) {
        EmergencyContactResponse dto = new EmergencyContactResponse();
        dto.setId(c.getId());
        dto.setFullName(c.getFullName());
        dto.setRelationship(c.getRelationship());
        dto.setPhone(c.getPhone());
        dto.setAddress(c.getAddress());
        return dto;
    }

    private TaxProfileResponse toTaxProfileResponse(EmployeeTaxProfile p) {
        TaxProfileResponse dto = new TaxProfileResponse();
        dto.setId(p.getId());
        dto.setTaxSituationId(p.getTaxSituation().getId());
        dto.setTaxSituationCode(p.getTaxSituation().getCode());
        dto.setSpouseIsWorking(p.getSpouseIsWorking());
        dto.setNumberOfChildren(p.getNumberOfChildren());
        dto.setNumberOfDisabledChildren(p.getNumberOfDisabledChildren());
        dto.setValidFrom(p.getValidFrom());
        dto.setValidTo(p.getValidTo());
        return dto;
    }

    private AssignmentResponse toAssignmentResponse(EmployeeAssignment a) {
        AssignmentResponse dto = new AssignmentResponse();
        dto.setId(a.getId());
        if (a.getDepartment() != null) {
            dto.setDepartmentId(a.getDepartment().getId());
            dto.setDepartmentName(a.getDepartment().getName());
        }
        if (a.getPosition() != null) {
            dto.setPositionId(a.getPosition().getId());
            dto.setPositionName(a.getPosition().getName());
        }
        if (a.getLocation() != null) {
            dto.setLocationId(a.getLocation().getId());
            dto.setLocationName(a.getLocation().getName());
        }
        dto.setValidFrom(a.getValidFrom());
        dto.setValidTo(a.getValidTo());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
