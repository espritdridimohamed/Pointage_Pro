package com.pointagepro.employee.service;

import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.dto.BankAccountRequest;
import com.pointagepro.employee.dto.DependentRequest;
import com.pointagepro.employee.dto.DocumentRequest;
import com.pointagepro.employee.dto.EmergencyContactRequest;
import com.pointagepro.employee.dto.TaxProfileRequest;
import com.pointagepro.employee.entity.Bank;
import com.pointagepro.employee.entity.DependentRelationship;
import com.pointagepro.employee.entity.DocumentType;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.entity.EmployeeAssignment;
import com.pointagepro.employee.entity.EmployeeBankAccount;
import com.pointagepro.employee.entity.EmployeeDocument;
import com.pointagepro.employee.entity.EmployeeDependent;
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
import com.pointagepro.organization.entity.Department;
import com.pointagepro.organization.entity.Location;
import com.pointagepro.organization.entity.Position;
import com.pointagepro.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeRecordServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeDocumentRepository documentRepository;
    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private EmployeeBankAccountRepository bankAccountRepository;
    @Mock private BankRepository bankRepository;
    @Mock private EmployeeDependentRepository dependentRepository;
    @Mock private DependentRelationshipRepository relationshipRepository;
    @Mock private EmployeeEmergencyContactRepository emergencyContactRepository;
    @Mock private EmployeeTaxProfileRepository taxProfileRepository;
    @Mock private TaxSituationRepository taxSituationRepository;
    @Mock private EmployeeAssignmentRepository assignmentRepository;

    @InjectMocks private EmployeeRecordService service;

    private Company company;
    private Employee employee;
    private DocumentType cin;
    private Bank bank;
    private DependentRelationship spouse;
    private TaxSituation celibataire;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        employee = new Employee();
        employee.setId(10L);
        employee.setCompany(company);

        cin = new DocumentType();
        cin.setId(2L);
        cin.setCode("CIN");
        cin.setLabel("CIN");
        bank = new Bank();
        bank.setId(3L);
        bank.setCode("BH");
        bank.setName("Banque de l'Habitat");
        spouse = new DependentRelationship();
        spouse.setId(4L);
        spouse.setCode("SPOUSE");
        spouse.setLabel("Épouse");
        celibataire = new TaxSituation();
        celibataire.setId(5L);
        celibataire.setCode("CELIBATAIRE");
        celibataire.setLabel("Single");

        lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        lenient().when(documentRepository.save(any(EmployeeDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bankAccountRepository.save(any(EmployeeBankAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dependentRepository.save(any(EmployeeDependent.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(emergencyContactRepository.save(any(EmployeeEmergencyContact.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(taxProfileRepository.save(any(EmployeeTaxProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createDocumentSavesWithResolvedType() {
        when(documentTypeRepository.findById(2L)).thenReturn(Optional.of(cin));

        DocumentRequest req = new DocumentRequest();
        req.setDocumentTypeId(2L);
        req.setFilePath("/docs/cin-ali.pdf");
        req.setDocumentNumber("12345678");

        var response = service.createDocument(company, 10L, req);

        assertThat(response.getDocumentType()).isEqualTo("CIN");
        assertThat(response.getFilePath()).isEqualTo("/docs/cin-ali.pdf");
    }

    @Test
    void createDocumentRejectsMissingFilePath() {
        DocumentRequest req = new DocumentRequest();
        req.setDocumentTypeId(2L);

        assertThatThrownBy(() -> service.createDocument(company, 10L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createBankAccountDefaultUnsetsPreviousDefault() {
        when(bankRepository.findById(3L)).thenReturn(Optional.of(bank));
        EmployeeBankAccount previous = new EmployeeBankAccount();
        previous.setId(1L);
        previous.setEmployee(employee);
        previous.setBank(bank);
        previous.setIsDefault(true);
        when(bankAccountRepository.findByEmployeeIdAndIsDefaultTrue(10L)).thenReturn(Optional.of(previous));

        BankAccountRequest req = new BankAccountRequest();
        req.setBankId(3L);
        req.setAccountNumber("0011223344");
        req.setIsDefault(true);
        req.setValidFrom(LocalDate.of(2026, 1, 1));

        var response = service.createBankAccount(company, 10L, req);

        assertThat(previous.getIsDefault()).isFalse();
        assertThat(response.getIsDefault()).isTrue();
        assertThat(response.getBankName()).isEqualTo("Banque de l'Habitat");
    }

    @Test
    void createBankAccountRejectsMissingValidFrom() {
        when(bankRepository.findById(3L)).thenReturn(Optional.of(bank));

        BankAccountRequest req = new BankAccountRequest();
        req.setBankId(3L);

        assertThatThrownBy(() -> service.createBankAccount(company, 10L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createDependentSavesWithRelationship() {
        when(relationshipRepository.findById(4L)).thenReturn(Optional.of(spouse));

        DependentRequest req = new DependentRequest();
        req.setFirstName("Salma");
        req.setLastName("Trabelsi");
        req.setRelationshipId(4L);

        var response = service.createDependent(company, 10L, req);

        assertThat(response.getRelationshipCode()).isEqualTo("SPOUSE");
        assertThat(response.getTaxDeductible()).isTrue();
    }

    @Test
    void createDependentRejectsMissingRelationship() {
        DependentRequest req = new DependentRequest();
        req.setFirstName("Salma");
        req.setLastName("Trabelsi");

        assertThatThrownBy(() -> service.createDependent(company, 10L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEmergencyContactRejectsMissingPhone() {
        EmergencyContactRequest req = new EmergencyContactRequest();
        req.setFullName("Omar Trabelsi");

        assertThatThrownBy(() -> service.createEmergencyContact(company, 10L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTaxProfileClosesCurrentOpenProfile() {
        when(taxSituationRepository.findById(5L)).thenReturn(Optional.of(celibataire));
        EmployeeTaxProfile current = new EmployeeTaxProfile();
        current.setId(1L);
        current.setEmployee(employee);
        current.setTaxSituation(celibataire);
        current.setValidFrom(LocalDate.of(2026, 1, 1));
        current.setValidTo(null);
        when(taxProfileRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(10L))
                .thenReturn(Optional.of(current));

        TaxProfileRequest req = new TaxProfileRequest();
        req.setTaxSituationId(5L);
        req.setValidFrom(LocalDate.of(2026, 6, 1));
        req.setNumberOfChildren(2);

        var response = service.createTaxProfile(company, 10L, req);

        assertThat(current.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(response.getTaxSituationCode()).isEqualTo("CELIBATAIRE");
    }

    @Test
    void createTaxProfileBackDatedOverCurrentRejected() {
        when(taxSituationRepository.findById(5L)).thenReturn(Optional.of(celibataire));
        EmployeeTaxProfile current = new EmployeeTaxProfile();
        current.setId(1L);
        current.setEmployee(employee);
        current.setTaxSituation(celibataire);
        current.setValidFrom(LocalDate.of(2026, 6, 1));
        current.setValidTo(null);
        when(taxProfileRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(10L))
                .thenReturn(Optional.of(current));

        TaxProfileRequest req = new TaxProfileRequest();
        req.setTaxSituationId(5L);
        req.setValidFrom(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.createTaxProfile(company, 10L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateTaxProfileToOpenWhenAnotherOpenRejected() {
        EmployeeTaxProfile p = new EmployeeTaxProfile();
        p.setId(1L);
        p.setEmployee(employee);
        p.setTaxSituation(celibataire);
        p.setValidFrom(LocalDate.of(2026, 1, 1));
        p.setValidTo(null);
        when(taxProfileRepository.findById(1L)).thenReturn(Optional.of(p));
        EmployeeTaxProfile other = new EmployeeTaxProfile();
        other.setId(2L);
        other.setValidFrom(LocalDate.of(2025, 1, 1));
        other.setValidTo(null);
        when(taxProfileRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(10L))
                .thenReturn(Optional.of(other));
        when(taxSituationRepository.findById(5L)).thenReturn(Optional.of(celibataire));

        TaxProfileRequest req = new TaxProfileRequest();
        req.setTaxSituationId(5L);
        req.setValidFrom(LocalDate.of(2026, 1, 1));
        req.setNumberOfChildren(1);

        assertThatThrownBy(() -> service.updateTaxProfile(company, 10L, 1L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listAssignmentsMapsRelations() {
        EmployeeAssignment a = new EmployeeAssignment();
        a.setId(1L);
        Department dept = new Department();
        dept.setId(5L);
        dept.setName("Comptabilité");
        Position pos = new Position();
        pos.setId(6L);
        pos.setName("Comptable");
        Location loc = new Location();
        loc.setId(7L);
        loc.setName("Siège");
        a.setDepartment(dept);
        a.setPosition(pos);
        a.setLocation(loc);
        a.setValidFrom(LocalDate.of(2026, 1, 1));
        a.setValidTo(null);
        when(assignmentRepository.findByEmployeeIdOrderByValidFromDesc(10L)).thenReturn(List.of(a));

        var assignments = service.listAssignments(company, 10L);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getDepartmentName()).isEqualTo("Comptabilité");
        assertThat(assignments.get(0).getPositionName()).isEqualTo("Comptable");
        assertThat(assignments.get(0).getLocationName()).isEqualTo("Siège");
    }

    @Test
    void deleteDocumentDeletesAfterScoping() {
        EmployeeDocument d = new EmployeeDocument();
        d.setId(1L);
        d.setEmployee(employee);
        d.setDocumentType(cin);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(d));

        service.deleteDocument(company, 10L, 1L);

        verify(documentRepository).deleteById(1L);
    }
}
