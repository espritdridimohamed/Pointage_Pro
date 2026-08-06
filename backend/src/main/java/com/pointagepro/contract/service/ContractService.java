package com.pointagepro.contract.service;

import com.pointagepro.auth.entity.User;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.dto.ContractRequest;
import com.pointagepro.contract.dto.ContractResponse;
import com.pointagepro.contract.dto.SalaryComponentRequest;
import com.pointagepro.contract.dto.SalaryComponentResponse;
import com.pointagepro.contract.dto.SalaryHistoryResponse;
import com.pointagepro.contract.entity.ContractStatus;
import com.pointagepro.contract.entity.ContractType;
import com.pointagepro.contract.entity.EmployeeContract;
import com.pointagepro.contract.entity.SalaryComponent;
import com.pointagepro.contract.entity.SalaryComponentType;
import com.pointagepro.contract.entity.SalaryHistory;
import com.pointagepro.contract.repository.ContractStatusRepository;
import com.pointagepro.contract.repository.ContractTypeRepository;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.contract.repository.SalaryComponentRepository;
import com.pointagepro.contract.repository.SalaryComponentTypeRepository;
import com.pointagepro.contract.repository.SalaryHistoryRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.organization.entity.Location;
import com.pointagepro.organization.repository.LocationRepository;
import com.pointagepro.payroll.repository.PayrollItemRepository;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contract module: employee_contracts CRUD + salary_components CRUD + auto salary_history.
 *
 * Invariants preserved for payroll determinism (PayrollService.resolveContext picks the
 * single ACTIVE contract overlapping the period with an active BASE component):
 * - single ACTIVE contract per employee (newer ACTIVE contract auto-closes the older one;
 *   back-dating over an existing ACTIVE contract is rejected with 409);
 * - at most one effective BASE component per contract at any date (new BASE auto-closes
 *   the previous one and writes salary_history);
 * - salary_history records every BASE creation/amount change.
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String BASE_TYPE_CODE = "BASE_SALARY";

    private final EmployeeContractRepository contractRepository;
    private final SalaryComponentRepository componentRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final ContractStatusRepository contractStatusRepository;
    private final SalaryComponentTypeRepository componentTypeRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;

    // ------------------------------------------------------------- contracts

    @Transactional
    public ContractResponse createContract(Company company, Employee employee, ContractRequest req, User actor) {
        EmployeeContract c = new EmployeeContract();
        c.setEmployee(employee);
        c.setCompany(company);
        applyContract(c, company, req);
        enforceSingleActive(company, employee, c);
        return toContractResponse(contractRepository.save(c));
    }

    @Transactional
    public ContractResponse updateContract(Company company, Long id, ContractRequest req, User actor) {
        EmployeeContract c = requireContract(company, id);
        applyContract(c, company, req);
        enforceSingleActive(company, c.getEmployee(), c);
        return toContractResponse(contractRepository.save(c));
    }

    @Transactional
    public void deleteContract(Company company, Long id) {
        EmployeeContract c = requireContract(company, id);
        if (componentRepository.countByContractId(id) > 0 || payrollItemRepository.countByContractId(id) > 0) {
            throw new ConflictException(
                    "Contrat référencé par des composantes de salaire ou des éléments de paie; "
                            + "terminez-le (statut EXPIRED) au lieu de le supprimer");
        }
        contractRepository.delete(c);
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> listContracts(Company company, Long employeeId) {
        return contractRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                .filter(c -> c.getCompany().getId().equals(company.getId()))
                .map(this::toContractResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse getContract(Company company, Long id) {
        return toContractResponse(requireContract(company, id));
    }

    // ------------------------------------------------------------- components

    @Transactional(readOnly = true)
    public List<SalaryComponentResponse> listComponents(Company company, Long contractId) {
        requireContract(company, contractId);
        return componentRepository.findByContractIdOrderByStartDateDesc(contractId).stream()
                .map(this::toComponentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalaryComponentResponse getComponent(Company company, Long contractId, Long componentId) {
        return toComponentResponse(requireComponent(company, contractId, componentId));
    }

    @Transactional
    public SalaryComponentResponse createComponent(Company company, Long contractId,
                                                   SalaryComponentRequest req, User actor) {
        EmployeeContract c = requireContract(company, contractId);
        SalaryComponent sc = new SalaryComponent();
        sc.setContract(c);
        applyComponent(sc, req);
        saveWithBaseRule(c, sc, actor);
        return toComponentResponse(sc);
    }

    @Transactional
    public SalaryComponentResponse updateComponent(Company company, Long contractId, Long componentId,
                                                   SalaryComponentRequest req, User actor) {
        EmployeeContract c = requireContract(company, contractId);
        SalaryComponent sc = requireComponent(company, contractId, componentId);
        BigDecimal oldAmount = sc.getAmount();
        boolean wasBase = isBaseType(sc.getComponentType());
        applyComponent(sc, req);
        if (wasBase && isBaseType(sc.getComponentType())) {
            boolean changed = oldAmount == null ? sc.getAmount() != null
                    : oldAmount.compareTo(sc.getAmount()) != 0;
            if (changed) {
                writeSalaryHistory(c, oldAmount, sc.getAmount(), "Mise à jour du salaire de base", actor);
            }
            runSiblingClose(c, sc, actor);
        }
        return toComponentResponse(componentRepository.save(sc));
    }

    @Transactional
    public void deleteComponent(Company company, Long contractId, Long componentId) {
        EmployeeContract c = requireContract(company, contractId);
        requireComponent(company, contractId, componentId);
        if (payrollItemRepository.countByContractId(contractId) > 0) {
            throw new ConflictException(
                    "Le contrat a déjà été passé en paie; désactivez la composante "
                            + "(endDate/isActive) au lieu de la supprimer");
        }
        componentRepository.deleteById(componentId);
    }

    // ------------------------------------------------------------- salary history

    @Transactional(readOnly = true)
    public List<SalaryHistoryResponse> salaryHistory(Employee employee) {
        return salaryHistoryRepository.findByEmployeeIdOrderByChangeDateDesc(employee.getId()).stream()
                .map(h -> {
                    SalaryHistoryResponse dto = new SalaryHistoryResponse();
                    dto.setId(h.getId());
                    dto.setEmployeeId(h.getEmployee().getId());
                    dto.setContractId(h.getContract() != null ? h.getContract().getId() : null);
                    dto.setOldAmount(h.getOldAmount());
                    dto.setNewAmount(h.getNewAmount());
                    dto.setChangeDate(h.getChangeDate());
                    dto.setReason(h.getReason());
                    dto.setChangedBy(h.getChangedBy() != null ? h.getChangedBy().getId() : null);
                    dto.setCreatedAt(h.getCreatedAt());
                    return dto;
                })
                .toList();
    }

    // ------------------------------------------------------------- flat employee helpers

    /**
     * Creates an ACTIVE contract starting at {@code startDate} (used by the flat employee
     * create/update flows). Does not run the sibling close: the employee flow ensures a
     * single ACTIVE contract before calling this.
     */
    @Transactional
    public EmployeeContract createAutoContract(Company company, Employee employee,
                                               String contractTypeCode, LocalDate startDate, User actor) {
        ContractType type = contractTypeRepository.findByCode(contractTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Contrat type inconnu: " + contractTypeCode));
        ContractStatus active = contractStatusRepository.findByCode(STATUS_ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Statut de contrat ACTIVE non configuré"));
        EmployeeContract c = new EmployeeContract();
        c.setEmployee(employee);
        c.setCompany(company);
        c.setContractType(type);
        c.setStatus(active);
        c.setStartDate(startDate);
        c.setWorkingHoursPerDay(new BigDecimal("8.00"));
        c.setWorkingDaysPerWeek(5);
        return contractRepository.save(c);
    }

    /**
     * Applies the flat salary fields to the employee's ACTIVE contract (creating it if the
     * base is positive and no ACTIVE contract exists). BASE changes close the previous BASE
     * and write salary_history. {@code startDate} is hiringDate on create, today on update.
     */
    @Transactional
    public void applyFlatSalary(Company company, Employee employee,
                                BigDecimal baseSalary, BigDecimal primeTransport,
                                BigDecimal primePerformance, BigDecimal primeOther,
                                LocalDate startDate, User actor) {
        EmployeeContract contract = contractRepository
                .findFirstByEmployeeIdAndStatusCodeOrderByStartDateDesc(employee.getId(), STATUS_ACTIVE)
                .orElse(null);
        if (contract == null && baseSalary != null && baseSalary.signum() > 0) {
            contract = createAutoContract(company, employee, "CDI", startDate, actor);
        }
        if (contract == null) {
            return;
        }
        if (baseSalary != null && baseSalary.signum() > 0) {
            setBase(contract, baseSalary, startDate, actor);
        }
        setPrime(contract, "PRIME_TRANSPORT", primeTransport, startDate, actor);
        setPrime(contract, "PRIME_RENDEMENT", primePerformance, startDate, actor);
        setPrime(contract, "AUTRE", primeOther, startDate, actor);
    }

    /** Auto-closes the employee's open ACTIVE contract on termination (end = exit date). */
    @Transactional
    public void closeActiveContract(Employee employee, LocalDate exitDate) {
        contractRepository.findByEmployeeIdOrderByStartDateDesc(employee.getId()).stream()
                .filter(c -> c.getStatus() != null && STATUS_ACTIVE.equals(c.getStatus().getCode()))
                .findFirst()
                .ifPresent(c -> {
                    c.setEndDate(exitDate);
                    c.setStatus(contractStatusRepository.findByCode(STATUS_EXPIRED).orElse(c.getStatus()));
                    contractRepository.save(c);
                });
    }

    // ------------------------------------------------------------- private

    private void applyContract(EmployeeContract c, Company company, ContractRequest req) {
        ContractType type = contractTypeRepository.findByCode(req.getContractTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Contrat type inconnu: " + req.getContractTypeCode()));
        String statusCode = req.getStatusCode() != null ? req.getStatusCode() : STATUS_ACTIVE;
        ContractStatus status = contractStatusRepository.findByCode(statusCode)
                .orElseThrow(() -> new IllegalArgumentException("Statut de contrat inconnu: " + statusCode));
        LocalDate start = req.getStartDate();
        if (start == null) {
            throw new IllegalArgumentException("startDate is required");
        }
        if (req.getEndDate() != null && req.getEndDate().isBefore(start)) {
            throw new IllegalArgumentException("endDate doit être >= startDate");
        }
        if (req.getProbationEndDate() != null && req.getProbationEndDate().isBefore(start)) {
            throw new IllegalArgumentException("probationEndDate doit être >= startDate");
        }
        if (req.getProbationEndDate() != null && req.getEndDate() != null
                && req.getProbationEndDate().isAfter(req.getEndDate())) {
            throw new IllegalArgumentException("probationEndDate doit être <= endDate");
        }
        BigDecimal whpd = req.getWorkingHoursPerDay() != null ? req.getWorkingHoursPerDay()
                : new BigDecimal("8.00");
        if (whpd.compareTo(new BigDecimal("0.5")) < 0 || whpd.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("workingHoursPerDay doit être entre 0.5 et 24");
        }
        Integer wdpw = req.getWorkingDaysPerWeek() != null ? req.getWorkingDaysPerWeek() : 5;
        if (wdpw < 1 || wdpw > 7) {
            throw new IllegalArgumentException("workingDaysPerWeek doit être entre 1 et 7");
        }
        if (req.getLocationId() != null) {
            Location l = locationRepository.findById(req.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location", "id", req.getLocationId()));
            if (!l.getCompany().getId().equals(company.getId())) {
                throw new ResourceNotFoundException("Location", "id", req.getLocationId());
            }
            c.setLocation(l);
        } else {
            c.setLocation(null);
        }
        c.setContractType(type);
        c.setStatus(status);
        c.setStartDate(start);
        c.setEndDate(req.getEndDate());
        c.setProbationEndDate(req.getProbationEndDate());
        c.setWorkingHoursPerDay(whpd);
        c.setWorkingDaysPerWeek(wdpw);
        c.setNoticePeriodDays(req.getNoticePeriodDays());
        c.setAttachmentPath(trimToNull(req.getAttachmentPath()));
    }

    private void enforceSingleActive(Company company, Employee employee, EmployeeContract updated) {
        if (updated.getStatus() == null || !STATUS_ACTIVE.equals(updated.getStatus().getCode())) {
            return;
        }
        for (EmployeeContract ex : contractRepository.findByEmployeeIdOrderByStartDateDesc(employee.getId())) {
            if (updated.getId() != null && updated.getId().equals(ex.getId())) {
                continue;
            }
            if (ex.getStatus() == null || !STATUS_ACTIVE.equals(ex.getStatus().getCode())) {
                continue;
            }
            if (updated.getStartDate().isBefore(ex.getStartDate())) {
                throw new ConflictException("Un contrat actif couvre déjà cette période depuis "
                        + ex.getStartDate() + "; impossible de rétro-dater");
            }
            if (overlaps(updated.getStartDate(), updated.getEndDate(), ex.getStartDate(), ex.getEndDate())) {
                ex.setEndDate(updated.getStartDate().minusDays(1));
                ex.setStatus(contractStatusRepository.findByCode(STATUS_EXPIRED).orElse(ex.getStatus()));
                contractRepository.save(ex);
            }
        }
    }

    private void applyComponent(SalaryComponent sc, SalaryComponentRequest req) {
        SalaryComponentType type = componentTypeRepository.findByCode(req.getComponentTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Type de composante inconnu: " + req.getComponentTypeCode()));
        if (req.getLabel() == null || req.getLabel().isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        LocalDate start = req.getStartDate();
        if (start == null) {
            throw new IllegalArgumentException("startDate is required");
        }
        if (req.getEndDate() != null && req.getEndDate().isBefore(start)) {
            throw new IllegalArgumentException("endDate doit être >= startDate");
        }
        boolean pct = Boolean.TRUE.equals(req.getIsPercentage());
        if (pct) {
            if (req.getPercentageValue() == null) {
                throw new IllegalArgumentException("percentageValue is required for percentage components");
            }
            if (req.getPercentageValue().compareTo(BigDecimal.ZERO) < 0
                    || req.getPercentageValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("percentageValue doit être entre 0 et 100");
            }
        } else if (req.getAmount() == null || req.getAmount().signum() < 0) {
            throw new IllegalArgumentException("amount is required (>= 0) for fixed components");
        }
        sc.setComponentType(type);
        sc.setLabel(req.getLabel().trim());
        sc.setAmount(req.getAmount());
        sc.setIsPercentage(pct);
        sc.setPercentageValue(pct ? req.getPercentageValue() : null);
        sc.setStartDate(start);
        sc.setEndDate(req.getEndDate());
        sc.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
    }

    private void saveWithBaseRule(EmployeeContract contract, SalaryComponent sc, User actor) {
        if (!isBaseType(sc.getComponentType())) {
            componentRepository.save(sc);
            return;
        }
        boolean closed = runSiblingClose(contract, sc, actor);
        if (!closed) {
            writeSalaryHistory(contract, null, sc.getAmount(), "Salaire de base initial", actor);
        }
        componentRepository.save(sc);
    }

    private boolean runSiblingClose(EmployeeContract contract, SalaryComponent sc, User actor) {
        boolean closed = false;
        for (SalaryComponent ex : componentRepository
                .findByContractIdAndIsActiveTrueOrderByStartDateDesc(contract.getId())) {
            if (sc.getId() != null && sc.getId().equals(ex.getId())) {
                continue;
            }
            if (!isBaseType(ex.getComponentType())) {
                continue;
            }
            if (sc.getStartDate().isBefore(ex.getStartDate())) {
                throw new ConflictException("Impossible de rétro-dater le salaire de base "
                        + "(base actuelle depuis " + ex.getStartDate() + ")");
            }
            if (overlaps(sc.getStartDate(), sc.getEndDate(), ex.getStartDate(), ex.getEndDate())) {
                ex.setEndDate(sc.getStartDate().minusDays(1));
                componentRepository.save(ex);
                writeSalaryHistory(contract, ex.getAmount(), sc.getAmount(),
                        "Remplacement du salaire de base", actor);
                closed = true;
            }
        }
        return closed;
    }

    private void setBase(EmployeeContract contract, BigDecimal amount, LocalDate startDate, User actor) {
        SalaryComponent current = currentBase(contract);
        if (current == null) {
            componentRepository.save(baseComponent(contract, amount, startDate));
            writeSalaryHistory(contract, null, amount, "Salaire de base initial", actor);
        } else if (current.getAmount().compareTo(amount) != 0) {
            current.setEndDate(startDate.minusDays(1));
            componentRepository.save(current);
            componentRepository.save(baseComponent(contract, amount, startDate));
            writeSalaryHistory(contract, current.getAmount(), amount,
                    "Mise à jour du salaire de base", actor);
        }
    }

    private void setPrime(EmployeeContract contract, String code, BigDecimal value,
                          LocalDate startDate, User actor) {
        if (value == null) {
            return;
        }
        SalaryComponentType type = componentTypeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Type de composante " + code + " non configuré"));
        SalaryComponent existing = componentRepository
                .findByContractIdOrderByStartDateDesc(contract.getId()).stream()
                .filter(sc -> sc.getComponentType() != null && code.equals(sc.getComponentType().getCode()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setAmount(value);
            existing.setIsActive(true);
            componentRepository.save(existing);
        } else {
            SalaryComponent sc = new SalaryComponent();
            sc.setContract(contract);
            sc.setComponentType(type);
            sc.setLabel(type.getLabel());
            sc.setAmount(value);
            sc.setIsPercentage(false);
            sc.setStartDate(startDate);
            sc.setEndDate(null);
            sc.setIsActive(true);
            componentRepository.save(sc);
        }
    }

    private SalaryComponent baseComponent(EmployeeContract contract, BigDecimal amount, LocalDate startDate) {
        SalaryComponentType type = componentTypeRepository.findByCode(BASE_TYPE_CODE)
                .orElseThrow(() -> new IllegalStateException("Type de composante BASE_SALARY non configuré"));
        SalaryComponent sc = new SalaryComponent();
        sc.setContract(contract);
        sc.setComponentType(type);
        sc.setLabel("Salaire de base");
        sc.setAmount(amount);
        sc.setIsPercentage(false);
        sc.setStartDate(startDate);
        sc.setEndDate(null);
        sc.setIsActive(true);
        return sc;
    }

    private SalaryComponent currentBase(EmployeeContract contract) {
        return componentRepository
                .findByContractIdAndIsActiveTrueOrderByStartDateDesc(contract.getId()).stream()
                .filter(sc -> isBaseType(sc.getComponentType()))
                .filter(sc -> sc.getEndDate() == null || !sc.getEndDate().isBefore(LocalDate.now()))
                .findFirst()
                .orElse(null);
    }

    private void writeSalaryHistory(EmployeeContract contract, BigDecimal oldAmount,
                                    BigDecimal newAmount, String reason, User actor) {
        SalaryHistory h = new SalaryHistory();
        h.setEmployee(contract.getEmployee());
        h.setContract(contract);
        h.setOldAmount(oldAmount);
        h.setNewAmount(newAmount);
        h.setChangeDate(LocalDate.now());
        h.setReason(reason);
        h.setChangedBy(actor != null ? userRepository.getReferenceById(actor.getId()) : null);
        salaryHistoryRepository.save(h);
    }

    private EmployeeContract requireContract(Company company, Long id) {
        EmployeeContract c = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeContract", "id", id));
        if (!c.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("EmployeeContract", "id", id);
        }
        return c;
    }

    private SalaryComponent requireComponent(Company company, Long contractId, Long componentId) {
        requireContract(company, contractId);
        SalaryComponent sc = componentRepository.findById(componentId)
                .orElseThrow(() -> new ResourceNotFoundException("SalaryComponent", "id", componentId));
        if (!sc.getContract().getId().equals(contractId)) {
            throw new ResourceNotFoundException("SalaryComponent", "id", componentId);
        }
        return sc;
    }

    private ContractResponse toContractResponse(EmployeeContract c) {
        ContractResponse dto = new ContractResponse();
        dto.setId(c.getId());
        dto.setEmployeeId(c.getEmployee().getId());
        dto.setEmployeeName(c.getEmployee().getFirstName() + " " + c.getEmployee().getLastName());
        dto.setContractTypeCode(c.getContractType().getCode());
        dto.setContractType(c.getContractType().getLabel());
        dto.setStatusCode(c.getStatus().getCode());
        dto.setStatus(c.getStatus().getLabel());
        dto.setStartDate(c.getStartDate());
        dto.setEndDate(c.getEndDate());
        dto.setProbationEndDate(c.getProbationEndDate());
        if (c.getLocation() != null) {
            dto.setLocationId(c.getLocation().getId());
            dto.setLocationName(c.getLocation().getName());
        }
        dto.setWorkingHoursPerDay(c.getWorkingHoursPerDay());
        dto.setWorkingDaysPerWeek(c.getWorkingDaysPerWeek());
        dto.setNoticePeriodDays(c.getNoticePeriodDays());
        dto.setAttachmentPath(c.getAttachmentPath());
        SalaryComponent base = currentBase(c);
        dto.setBaseSalary(base != null ? base.getAmount() : null);
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    private SalaryComponentResponse toComponentResponse(SalaryComponent sc) {
        SalaryComponentResponse dto = new SalaryComponentResponse();
        dto.setId(sc.getId());
        dto.setComponentTypeCode(sc.getComponentType() != null ? sc.getComponentType().getCode() : null);
        dto.setComponentType(sc.getComponentType() != null ? sc.getComponentType().getLabel() : null);
        dto.setCategory(sc.getComponentType() != null ? sc.getComponentType().getCategory() : null);
        dto.setLabel(sc.getLabel());
        dto.setAmount(sc.getAmount());
        dto.setIsPercentage(sc.getIsPercentage());
        dto.setPercentageValue(sc.getPercentageValue());
        dto.setStartDate(sc.getStartDate());
        dto.setEndDate(sc.getEndDate());
        dto.setIsActive(sc.getIsActive());
        return dto;
    }

    private static boolean isBaseType(SalaryComponentType type) {
        return type != null && (BASE_TYPE_CODE.equals(type.getCode())
                || "BASE".equalsIgnoreCase(type.getCategory()));
    }

    private static boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        LocalDate aEnd = aTo == null ? LocalDate.MAX : aTo;
        LocalDate bEnd = bTo == null ? LocalDate.MAX : bTo;
        return !aFrom.isAfter(bEnd) && !bFrom.isAfter(aEnd);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
