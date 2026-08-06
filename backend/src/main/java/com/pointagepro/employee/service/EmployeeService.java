package com.pointagepro.employee.service;

import com.pointagepro.attendance.entity.EmployeeSchedule;
import com.pointagepro.attendance.entity.WorkSchedule;
import com.pointagepro.attendance.repository.EmployeeScheduleRepository;
import com.pointagepro.attendance.repository.WorkScheduleRepository;
import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.entity.EmployeeContract;
import com.pointagepro.contract.entity.SalaryComponent;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.contract.repository.SalaryComponentRepository;
import com.pointagepro.contract.service.ContractService;
import com.pointagepro.employee.dto.EmployeeRequest;
import com.pointagepro.employee.dto.EmployeeResponse;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.entity.EmployeeAssignment;
import com.pointagepro.employee.entity.EmployeeStatus;
import com.pointagepro.employee.entity.Gender;
import com.pointagepro.employee.entity.MaritalStatus;
import com.pointagepro.employee.repository.EmployeeAssignmentRepository;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.employee.repository.EmployeeStatusRepository;
import com.pointagepro.employee.repository.GenderRepository;
import com.pointagepro.employee.repository.MaritalStatusRepository;
import com.pointagepro.leave.entity.LeaveBalance;
import com.pointagepro.leave.entity.LeaveType;
import com.pointagepro.leave.repository.LeaveBalanceRepository;
import com.pointagepro.leave.repository.LeaveTypeRepository;
import com.pointagepro.organization.entity.Department;
import com.pointagepro.organization.entity.Location;
import com.pointagepro.organization.entity.Position;
import com.pointagepro.organization.repository.DepartmentRepository;
import com.pointagepro.organization.repository.LocationRepository;
import com.pointagepro.organization.repository.PositionRepository;
import com.pointagepro.schedule.service.ScheduleService;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Employee module: identity CRUD with the flat legacy-compatible shape. The flat request is
 * translated into contract / salary / schedule / leave-balance / assignment rows (see
 * EMPLOYEE_BUSINESS_RULES §4). Delete = termination: TERMINATED + exit_date today and the
 * open ACTIVE contract auto-closed by ContractService.closeActiveContract.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_TERMINATED = "TERMINATED";

    private static final Map<String, String> FR_TO_EN_STATUS = Map.of(
            "ACTIF", "ACTIVE",
            "CONGE", "ON_LEAVE",
            "INACTIF", "SUSPENDED");
    private static final Set<String> EN_STATUSES = Set.of(
            "ACTIVE", "SUSPENDED", "ON_LEAVE", "TERMINATED", "RESIGNED", "RETIRED");

    private final EmployeeRepository employeeRepository;
    private final EmployeeStatusRepository statusRepository;
    private final GenderRepository genderRepository;
    private final MaritalStatusRepository maritalStatusRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final LocationRepository locationRepository;
    private final EmployeeAssignmentRepository assignmentRepository;
    private final EmployeeContractRepository contractRepository;
    private final SalaryComponentRepository componentRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final ContractService contractService;
    private final ScheduleService scheduleService;

    // ------------------------------------------------------------- create

    @Transactional
    public EmployeeResponse create(Company company, EmployeeRequest req, User actor) {
        Employee e = new Employee();
        e.setCompany(company);
        applyIdentity(e, req);
        e.setMatricule(req.getMatricule() != null && !req.getMatricule().isBlank()
                ? req.getMatricule().trim() : nextMatricule(company.getId()));
        if (employeeRepository.existsByMatriculeAndCompanyId(e.getMatricule(), company.getId())) {
            throw new DuplicateResourceException("Employee", "matricule", e.getMatricule());
        }
        if (req.getRfidUid() != null && !req.getRfidUid().isBlank()) {
            String rfid = req.getRfidUid().trim();
            if (employeeRepository.existsByRfidUid(rfid)) {
                throw new DuplicateResourceException("Employee", "rfidUid", rfid);
            }
            e.setRfidUid(rfid);
        }
        e.setGender(resolveGender(req.getGender()));
        e.setMaritalStatus(resolveMaritalStatus(req.getMaritalStatus()));
        e.setStatus(statusRepository.findByCode(resolveStatus(req.getStatus(), STATUS_ACTIVE))
                .orElseThrow(() -> new IllegalStateException("Statut " + STATUS_ACTIVE + " non configuré")));
        Department dept = resolveDepartment(company, req);
        Position pos = resolvePosition(company, req);
        Location loc = resolveLocation(company, req);
        e.setDepartment(dept);
        e.setPosition(pos);
        e.setLocation(loc);
        Employee saved = employeeRepository.save(e);

        if (dept != null || pos != null || loc != null) {
            writeAssignment(saved, dept, pos, loc, saved.getHiringDate());
        }

        boolean wantsContract = req.getContractType() != null && !req.getContractType().isBlank()
                || (req.getBaseSalary() != null && req.getBaseSalary().signum() > 0);
        if (wantsContract) {
            String typeCode = req.getContractType() != null && !req.getContractType().isBlank()
                    ? req.getContractType().trim() : "CDI";
            contractService.createAutoContract(company, saved, typeCode, saved.getHiringDate(), actor);
            contractService.applyFlatSalary(company, saved, req.getBaseSalary(),
                    req.getPrimeTransport(), req.getPrimePerformance(), req.getPrimeOther(),
                    saved.getHiringDate(), actor);
        }

        if (req.getWeeklySchedule() != null && !req.getWeeklySchedule().isBlank()) {
            scheduleService.assignByCode(company, saved, req.getWeeklySchedule().trim(),
                    saved.getHiringDate());
        }

        seedLeaveBalance(saved, "ANNUAL", req.getAnnualLeaveDays());
        seedLeaveBalance(saved, "MATERNITY", req.getMaternityLeaveDays());

        return toEmployeeResponse(saved);
    }

    // ------------------------------------------------------------- update

    @Transactional
    public EmployeeResponse update(Company company, Long id, EmployeeRequest req, User actor) {
        Employee e = requireEmployee(company, id);

        String newMatricule = req.getMatricule() != null && !req.getMatricule().isBlank()
                ? req.getMatricule().trim() : e.getMatricule();
        if (!newMatricule.equals(e.getMatricule())
                && employeeRepository.existsByMatriculeAndCompanyIdAndIdNot(newMatricule, company.getId(), id)) {
            throw new DuplicateResourceException("Employee", "matricule", newMatricule);
        }
        String newRfid = req.getRfidUid() != null && !req.getRfidUid().isBlank()
                ? req.getRfidUid().trim() : e.getRfidUid();
        if (newRfid != null && !Objects.equals(newRfid, e.getRfidUid())
                && employeeRepository.existsByRfidUidAndIdNot(newRfid, id)) {
            throw new DuplicateResourceException("Employee", "rfidUid", newRfid);
        }

        String statusCode = resolveStatus(req.getStatus(), e.getStatus().getCode());
        e.setMatricule(newMatricule);
        e.setRfidUid(newRfid);
        e.setGender(overwriteLookup(req.getGender(), e.getGender() != null ? e.getGender().getCode() : null,
                this::resolveGender));
        e.setMaritalStatus(overwriteLookup(req.getMaritalStatus(),
                e.getMaritalStatus() != null ? e.getMaritalStatus().getCode() : null,
                this::resolveMaritalStatus));
        e.setStatus(statusRepository.findByCode(statusCode)
                .orElseThrow(() -> new IllegalStateException("Statut " + statusCode + " non configuré")));
        if (STATUS_ACTIVE.equals(statusCode)) {
            e.setExitDate(null);
        }
        applyIdentity(e, req);

        Department newDept = hasPlacement(req, "department") ? resolveDepartment(company, req) : e.getDepartment();
        Position newPos = hasPlacement(req, "position") ? resolvePosition(company, req) : e.getPosition();
        Location newLoc = req.getLocationId() != null ? resolveLocation(company, req) : e.getLocation();
        boolean placementChanged = !Objects.equals(deptId(e.getDepartment()), deptId(newDept))
                || !Objects.equals(posId(e.getPosition()), posId(newPos))
                || !Objects.equals(locId(e.getLocation()), locId(newLoc));
        e.setDepartment(newDept);
        e.setPosition(newPos);
        e.setLocation(newLoc);
        employeeRepository.save(e);
        if (placementChanged) {
            writePlacementChange(e, newDept, newPos, newLoc);
        }

        LocalDate today = LocalDate.now();
        EmployeeContract active = activeContract(e);
        boolean wantsContract = req.getContractType() != null && !req.getContractType().isBlank()
                || (req.getBaseSalary() != null && req.getBaseSalary().signum() > 0);
        if (wantsContract && active == null) {
            String typeCode = req.getContractType() != null && !req.getContractType().isBlank()
                    ? req.getContractType().trim() : "CDI";
            contractService.createAutoContract(company, e, typeCode, today, actor);
        }
        if (req.getBaseSalary() != null || req.getPrimeTransport() != null
                || req.getPrimePerformance() != null || req.getPrimeOther() != null) {
            contractService.applyFlatSalary(company, e, req.getBaseSalary(),
                    req.getPrimeTransport(), req.getPrimePerformance(), req.getPrimeOther(),
                    today, actor);
        }

        if (req.getWeeklySchedule() != null && !req.getWeeklySchedule().isBlank()) {
            String code = req.getWeeklySchedule().trim();
            String current = currentScheduleCode(e);
            if (!code.equals(current)) {
                scheduleService.closeOpenAssignment(e, today.minusDays(1));
                scheduleService.assignByCode(company, e, code, today);
            }
        }

        return toEmployeeResponse(e);
    }

    // ------------------------------------------------------------- delete / get / list

    @Transactional
    public void terminate(Company company, Long id) {
        Employee e = requireEmployee(company, id);
        e.setStatus(statusRepository.findByCode(STATUS_TERMINATED)
                .orElseThrow(() -> new IllegalStateException("Statut TERMINATED non configuré")));
        LocalDate today = LocalDate.now();
        e.setExitDate(today);
        employeeRepository.save(e);
        contractService.closeActiveContract(e, today);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Company company, Long id) {
        return toEmployeeResponse(requireEmployee(company, id));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(Company company, String search, String departmentName, Pageable pageable) {
        Long deptId = null;
        if (departmentName != null && !departmentName.isBlank()) {
            deptId = departmentRepository
                    .findByCompanyIdAndNameAndValidToIsNull(company.getId(), departmentName.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Département inconnu: " + departmentName))
                    .getId();
        }
        String s = search == null || search.isBlank() ? null : search.trim();
        return employeeRepository.searchEmployees(company.getId(), s, deptId, pageable)
                .map(this::toEmployeeResponse);
    }

    @Transactional(readOnly = true)
    public List<String> departmentNames(Company company) {
        return employeeRepository.findDistinctDepartmentNames(company.getId());
    }

    @Transactional(readOnly = true)
    public long count(Company company) {
        return employeeRepository.countByCompanyId(company.getId());
    }

    // ------------------------------------------------------------- private helpers

    private void applyIdentity(Employee e, EmployeeRequest req) {
        if (req.getFirstName() != null) {
            e.setFirstName(requireText(req.getFirstName(), "firstName"));
        }
        if (req.getLastName() != null) {
            e.setLastName(requireText(req.getLastName(), "lastName"));
        }
        if (req.getHiringDate() != null) {
            e.setHiringDate(req.getHiringDate());
        }
        e.setCin(trimToNull(req.getCin()));
        e.setPassportNumber(trimToNull(req.getPassportNumber()));
        if (req.getBirthDate() != null) {
            e.setBirthDate(req.getBirthDate());
        }
        if (req.getNationality() != null && !req.getNationality().isBlank()) {
            e.setNationality(req.getNationality().trim());
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String email = req.getEmail().trim();
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new IllegalArgumentException("email invalide: " + email);
            }
            e.setEmail(email);
        }
        e.setPhone(trimToNull(req.getPhone()));
        e.setAddress(trimToNull(req.getAddress()));
        e.setCity(trimToNull(req.getCity()));
        if (req.getPhoto() != null && !req.getPhoto().isBlank()) {
            e.setPhotoPath(req.getPhoto().trim());
        }
    }

    private String nextMatricule(Long companyId) {
        long n = employeeRepository.countByCompanyId(companyId) + 1;
        String m;
        do {
            m = String.format("EMP-%03d", n++);
        } while (employeeRepository.existsByMatriculeAndCompanyId(m, companyId));
        return m;
    }

    private String resolveStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String s = status.trim().toUpperCase();
        String mapped = FR_TO_EN_STATUS.getOrDefault(s, s);
        if (!EN_STATUSES.contains(mapped)) {
            throw new IllegalArgumentException("Statut inconnu: " + status);
        }
        return mapped;
    }

    private Gender resolveGender(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return genderRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Genre inconnu: " + code));
    }

    private MaritalStatus resolveMaritalStatus(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return maritalStatusRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Situation familiale inconnue: " + code));
    }

    private Department resolveDepartment(Company company, EmployeeRequest req) {
        if (req.getDepartmentId() != null) {
            return requireDepartment(company, req.getDepartmentId());
        }
        if (req.getDepartment() != null && !req.getDepartment().isBlank()) {
            return departmentRepository
                    .findByCompanyIdAndNameAndValidToIsNull(company.getId(), req.getDepartment().trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Département inconnu: " + req.getDepartment() + "; créez-le d'abord"));
        }
        return null;
    }

    private Position resolvePosition(Company company, EmployeeRequest req) {
        if (req.getPositionId() != null) {
            return requirePosition(company, req.getPositionId());
        }
        if (req.getPosition() != null && !req.getPosition().isBlank()) {
            return positionRepository
                    .findByCompanyIdAndNameAndValidToIsNull(company.getId(), req.getPosition().trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Poste inconnu: " + req.getPosition() + "; créez-le d'abord"));
        }
        return null;
    }

    private Location resolveLocation(Company company, EmployeeRequest req) {
        if (req.getLocationId() == null) {
            return null;
        }
        return requireLocation(company, req.getLocationId());
    }

    private boolean hasPlacement(EmployeeRequest req, String kind) {
        if ("department".equals(kind)) {
            return req.getDepartmentId() != null || (req.getDepartment() != null && !req.getDepartment().isBlank());
        }
        if ("position".equals(kind)) {
            return req.getPositionId() != null || (req.getPosition() != null && !req.getPosition().isBlank());
        }
        return false;
    }

    private <T> T overwriteLookup(String code, String currentCode, java.util.function.Function<String, T> resolver) {
        if (code == null || code.isBlank()) {
            return currentCode == null ? null : resolver.apply(currentCode);
        }
        return resolver.apply(code);
    }

    private void writePlacementChange(Employee e, Department dept, Position pos, Location loc) {
        LocalDate today = LocalDate.now();
        assignmentRepository.findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(e.getId())
                .ifPresentOrElse(open -> {
                    if (open.getValidFrom().equals(today)) {
                        open.setDepartment(dept);
                        open.setPosition(pos);
                        open.setLocation(loc);
                        assignmentRepository.save(open);
                    } else {
                        open.setValidTo(today.minusDays(1));
                        assignmentRepository.save(open);
                        writeAssignment(e, dept, pos, loc, today);
                    }
                }, () -> writeAssignment(e, dept, pos, loc, today));
    }

    private void writeAssignment(Employee e, Department dept, Position pos, Location loc, LocalDate validFrom) {
        EmployeeAssignment a = new EmployeeAssignment();
        a.setEmployee(e);
        a.setDepartment(dept);
        a.setPosition(pos);
        a.setLocation(loc);
        a.setValidFrom(validFrom);
        assignmentRepository.save(a);
    }

    private void seedLeaveBalance(Employee e, String typeCode, BigDecimal days) {
        if (days == null) {
            return;
        }
        LeaveType type = leaveTypeRepository.findByCode(typeCode).orElse(null);
        if (type == null || type.getDefaultDaysPerYear() == null) {
            return;
        }
        int year = LocalDate.now().getYear();
        if (balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(e.getId(), type.getId(), year).isPresent()) {
            return;
        }
        LeaveBalance b = new LeaveBalance();
        b.setEmployee(e);
        b.setLeaveType(type);
        b.setYear(year);
        b.setEntitlementDays(days);
        b.setTakenDays(BigDecimal.ZERO);
        b.setCarriedOverDays(BigDecimal.ZERO);
        b.setAdjustedDays(BigDecimal.ZERO);
        balanceRepository.save(b);
    }

    // ------------------------------------------------------------- response mapping

    private EmployeeResponse toEmployeeResponse(Employee e) {
        EmployeeResponse dto = new EmployeeResponse();
        dto.setId(e.getId());
        dto.setMatricule(e.getMatricule());
        dto.setFirstName(e.getFirstName());
        dto.setLastName(e.getLastName());
        dto.setCin(e.getCin());
        dto.setPassportNumber(e.getPassportNumber());
        dto.setBirthDate(e.getBirthDate());
        dto.setGender(e.getGender() != null ? e.getGender().getCode() : null);
        dto.setMaritalStatus(e.getMaritalStatus() != null ? e.getMaritalStatus().getCode() : null);
        dto.setNationality(e.getNationality());
        dto.setEmail(e.getEmail());
        dto.setPhone(e.getPhone());
        dto.setAddress(e.getAddress());
        dto.setCity(e.getCity());
        if (e.getDepartment() != null) {
            dto.setDepartment(e.getDepartment().getName());
            dto.setDepartmentId(e.getDepartment().getId());
        }
        if (e.getPosition() != null) {
            dto.setPosition(e.getPosition().getName());
            dto.setPositionId(e.getPosition().getId());
        }
        if (e.getLocation() != null) {
            dto.setLocationId(e.getLocation().getId());
        }
        EmployeeContract contract = activeContract(e);
        if (contract != null) {
            dto.setContractType(contract.getContractType() != null ? contract.getContractType().getCode() : null);
            SalaryComponent base = activeBase(contract);
            dto.setBaseSalary(base != null ? base.getAmount() : null);
            dto.setPrimeTransport(bonusAmount(contract, "PRIME_TRANSPORT"));
            dto.setPrimePerformance(bonusAmount(contract, "PRIME_RENDEMENT"));
            dto.setPrimeOther(bonusAmount(contract, "AUTRE"));
            dto.setTotalPrimes(totalBonuses(contract));
        }
        dto.setRfidUid(e.getRfidUid());
        dto.setPhoto(e.getPhotoPath());
        dto.setWeeklySchedule(currentScheduleCode(e));
        dto.setAnnualLeaveDays(leaveDays(e, "ANNUAL"));
        dto.setMaternityLeaveDays(leaveDays(e, "MATERNITY"));
        dto.setPaternityLeaveDays(leaveDays(e, "PATERNITY"));
        dto.setHiringDate(e.getHiringDate());
        dto.setExitDate(e.getExitDate());
        dto.setStatus(statusToFrench(e.getStatus() != null ? e.getStatus().getCode() : null));
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private EmployeeContract activeContract(Employee e) {
        return contractRepository
                .findFirstByEmployeeIdAndStatusCodeOrderByStartDateDesc(e.getId(), STATUS_ACTIVE)
                .orElse(null);
    }

    private SalaryComponent activeBase(EmployeeContract c) {
        return componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(c.getId()).stream()
                .filter(sc -> isBaseType(sc.getComponentType()))
                .filter(sc -> sc.getEndDate() == null || !sc.getEndDate().isBefore(LocalDate.now()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal bonusAmount(EmployeeContract c, String code) {
        return componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(c.getId()).stream()
                .filter(sc -> sc.getComponentType() != null && code.equals(sc.getComponentType().getCode()))
                .findFirst()
                .map(SalaryComponent::getAmount)
                .orElse(null);
    }

    private BigDecimal totalBonuses(EmployeeContract c) {
        return componentRepository.findByContractIdAndIsActiveTrueOrderByStartDateDesc(c.getId()).stream()
                .filter(sc -> sc.getComponentType() != null
                        && "BONUS".equalsIgnoreCase(sc.getComponentType().getCategory()))
                .filter(sc -> sc.getAmount() != null)
                .map(SalaryComponent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isBaseType(com.pointagepro.contract.entity.SalaryComponentType type) {
        return type != null && ("BASE_SALARY".equals(type.getCode())
                || "BASE".equalsIgnoreCase(type.getCategory()));
    }

    private String currentScheduleCode(Employee e) {
        EmployeeSchedule current = employeeScheduleRepository
                .findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(e.getId())
                .orElse(null);
        if (current != null && current.getSchedule() != null) {
            return current.getSchedule().getCode();
        }
        return scheduleRepository.findFirstByCompanyIdAndIsDefaultTrue(e.getCompany().getId())
                .map(WorkSchedule::getCode)
                .orElse(null);
    }

    private BigDecimal leaveDays(Employee e, String typeCode) {
        LeaveType type = leaveTypeRepository.findByCode(typeCode).orElse(null);
        if (type == null) {
            return null;
        }
        int year = LocalDate.now().getYear();
        return balanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(e.getId(), type.getId(), year)
                .map(LeaveBalance::getEntitlementDays)
                .orElse(type.getDefaultDaysPerYear());
    }

    private static String statusToFrench(String code) {
        if ("ACTIVE".equals(code)) {
            return "ACTIF";
        }
        if ("ON_LEAVE".equals(code)) {
            return "CONGE";
        }
        return "INACTIF";
    }

    // ------------------------------------------------------------- scoping

    private Employee requireEmployee(Company company, Long id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
        if (!e.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee", "id", id);
        }
        return e;
    }

    private Department requireDepartment(Company company, Long id) {
        Department d = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
        if (!d.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Department", "id", id);
        }
        return d;
    }

    private Position requirePosition(Company company, Long id) {
        Position p = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position", "id", id));
        if (!p.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Position", "id", id);
        }
        return p;
    }

    private Location requireLocation(Company company, Long id) {
        Location l = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", id));
        if (!l.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Location", "id", id);
        }
        return l;
    }

    private static Long deptId(Department d) {
        return d != null ? d.getId() : null;
    }

    private static Long posId(Position p) {
        return p != null ? p.getId() : null;
    }

    private static Long locId(Location l) {
        return l != null ? l.getId() : null;
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
