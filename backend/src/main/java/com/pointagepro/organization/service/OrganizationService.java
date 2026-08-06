package com.pointagepro.organization.service;

import com.pointagepro.company.entity.Company;
import com.pointagepro.contract.repository.EmployeeContractRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeAssignmentRepository;
import com.pointagepro.employee.repository.EmployeeRepository;
import com.pointagepro.organization.dto.DepartmentRequest;
import com.pointagepro.organization.dto.DepartmentResponse;
import com.pointagepro.organization.dto.LocationRequest;
import com.pointagepro.organization.dto.LocationResponse;
import com.pointagepro.organization.dto.PositionRequest;
import com.pointagepro.organization.dto.PositionResponse;
import com.pointagepro.organization.entity.Department;
import com.pointagepro.organization.entity.Location;
import com.pointagepro.organization.entity.Position;
import com.pointagepro.organization.repository.DepartmentRepository;
import com.pointagepro.organization.repository.LocationRepository;
import com.pointagepro.organization.repository.PositionRepository;
import com.pointagepro.shared.dto.LookupItem;
import com.pointagepro.shared.exception.ConflictException;
import com.pointagepro.shared.exception.DuplicateResourceException;
import com.pointagepro.shared.exception.ResourceNotFoundException;
import com.pointagepro.terminal.repository.TerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Organization module: departments, positions, locations (full CRUD, company-scoped).
 * Delete guards are FK-RESTRICT aware: referenced rows return 409 with a hint to close
 * the row instead of deleting it. Department name uniqueness applies to active rows only
 * (V16); code uniqueness applies to all rows of the company.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final LocationRepository locationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    private final EmployeeContractRepository employeeContractRepository;
    private final TerminalRepository terminalRepository;

    // ------------------------------------------------------------------ departments

    @Transactional
    public DepartmentResponse createDepartment(Company company, DepartmentRequest req) {
        Department d = new Department();
        d.setCompany(company);
        applyDepartment(d, req, company);
        validateDepartment(company, d, null);
        return toDepartmentResponse(departmentRepository.save(d));
    }

    @Transactional
    public DepartmentResponse updateDepartment(Company company, Long id, DepartmentRequest req) {
        Department d = requireDepartment(company, id);
        applyDepartment(d, req, company);
        validateDepartment(company, d, id);
        return toDepartmentResponse(departmentRepository.save(d));
    }

    @Transactional
    public void deleteDepartment(Company company, Long id) {
        Department d = requireDepartment(company, id);
        if (employeeRepository.countByDepartmentId(id) > 0
                || positionRepository.countByDepartmentId(id) > 0
                || employeeAssignmentRepository.countByDepartmentId(id) > 0) {
            throw new ConflictException(
                    "Département référencé par des employés/postes/affectations; fermez-le "
                            + "en fixant validTo au lieu de le supprimer");
        }
        departmentRepository.delete(d);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments(Company company) {
        return departmentRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .map(this::toDepartmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartment(Company company, Long id) {
        return toDepartmentResponse(requireDepartment(company, id));
    }

    @Transactional(readOnly = true)
    public List<LookupItem> departmentLookup(Company company) {
        return departmentRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .filter(d -> d.getValidTo() == null || !d.getValidTo().isBefore(LocalDate.now()))
                .map(d -> new LookupItem(d.getId(), d.getCode(), d.getName()))
                .toList();
    }

    // ------------------------------------------------------------------ positions

    @Transactional
    public PositionResponse createPosition(Company company, PositionRequest req) {
        Position p = new Position();
        p.setCompany(company);
        applyPosition(p, req, company);
        validatePosition(company, p, null);
        return toPositionResponse(positionRepository.save(p));
    }

    @Transactional
    public PositionResponse updatePosition(Company company, Long id, PositionRequest req) {
        Position p = requirePosition(company, id);
        applyPosition(p, req, company);
        validatePosition(company, p, id);
        return toPositionResponse(positionRepository.save(p));
    }

    @Transactional
    public void deletePosition(Company company, Long id) {
        Position p = requirePosition(company, id);
        if (employeeRepository.countByPositionId(id) > 0
                || employeeAssignmentRepository.countByPositionId(id) > 0) {
            throw new ConflictException(
                    "Poste référencé par des employés/affectations; fermez-le en fixant "
                            + "validTo au lieu de le supprimer");
        }
        positionRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(Company company) {
        return positionRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .map(this::toPositionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionResponse getPosition(Company company, Long id) {
        return toPositionResponse(requirePosition(company, id));
    }

    @Transactional(readOnly = true)
    public List<LookupItem> positionLookup(Company company) {
        return positionRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .filter(p -> p.getValidTo() == null || !p.getValidTo().isBefore(LocalDate.now()))
                .map(p -> new LookupItem(p.getId(), p.getCode(), p.getName()))
                .toList();
    }

    // ------------------------------------------------------------------ locations

    @Transactional
    public LocationResponse createLocation(Company company, LocationRequest req) {
        Location l = new Location();
        l.setCompany(company);
        applyLocation(l, req);
        validateLocation(company, l, null);
        return toLocationResponse(locationRepository.save(l));
    }

    @Transactional
    public LocationResponse updateLocation(Company company, Long id, LocationRequest req) {
        Location l = requireLocation(company, id);
        applyLocation(l, req);
        validateLocation(company, l, id);
        return toLocationResponse(locationRepository.save(l));
    }

    @Transactional
    public void deleteLocation(Company company, Long id) {
        Location l = requireLocation(company, id);
        if (employeeRepository.countByLocationId(id) > 0
                || employeeContractRepository.countByLocationId(id) > 0
                || terminalRepository.countByLocationId(id) > 0) {
            throw new ConflictException(
                    "Lieu référencé par des employés/contrats/terminaux; désactivez-le "
                            + "(isActive=false) au lieu de le supprimer");
        }
        locationRepository.delete(l);
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations(Company company) {
        return locationRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .map(this::toLocationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocation(Company company, Long id) {
        return toLocationResponse(requireLocation(company, id));
    }

    @Transactional(readOnly = true)
    public List<LookupItem> locationLookup(Company company) {
        return locationRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .filter(Location::getIsActive)
                .map(l -> new LookupItem(l.getId(), l.getCode(), l.getName()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

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

    private void applyDepartment(Department d, DepartmentRequest req, Company company) {
        d.setName(requireText(req.getName(), "name"));
        d.setCode(trimToNull(req.getCode()));
        d.setManagerEmployeeId(req.getManagerEmployeeId());
        d.setValidFrom(req.getValidFrom() != null ? req.getValidFrom() : LocalDate.now());
        d.setValidTo(req.getValidTo());
    }

    private void validateDepartment(Company company, Department d, Long excludeId) {
        if (d.getValidTo() != null && d.getValidTo().isBefore(d.getValidFrom())) {
            throw new IllegalArgumentException("validTo doit être >= validFrom");
        }
        String code = d.getCode();
        if (code != null) {
            boolean dup = excludeId == null
                    ? departmentRepository.existsByCompanyIdAndCode(company.getId(), code)
                    : departmentRepository.existsByCompanyIdAndCodeAndIdNot(company.getId(), code, excludeId);
            if (dup) {
                throw new DuplicateResourceException("Department", "code", code);
            }
        }
        departmentRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .filter(o -> !o.getId().equals(excludeId))
                .filter(o -> o.getValidTo() == null)
                .filter(o -> o.getName().equalsIgnoreCase(d.getName()))
                .findAny()
                .ifPresent(o -> {
                    throw new DuplicateResourceException("Department", "name", d.getName());
                });
        if (d.getManagerEmployeeId() != null) {
            requireSameCompanyEmployee(company, d.getManagerEmployeeId());
        }
    }

    private void applyPosition(Position p, PositionRequest req, Company company) {
        p.setName(requireText(req.getName(), "name"));
        p.setCode(trimToNull(req.getCode()));
        p.setDepartment(req.getDepartmentId() != null
                ? requireDepartment(company, req.getDepartmentId())
                : null);
        p.setValidFrom(req.getValidFrom() != null ? req.getValidFrom() : LocalDate.now());
        p.setValidTo(req.getValidTo());
    }

    private void validatePosition(Company company, Position p, Long excludeId) {
        if (p.getValidTo() != null && p.getValidTo().isBefore(p.getValidFrom())) {
            throw new IllegalArgumentException("validTo doit être >= validFrom");
        }
        String code = p.getCode();
        if (code != null) {
            boolean dup = excludeId == null
                    ? positionRepository.existsByCompanyIdAndCode(company.getId(), code)
                    : positionRepository.existsByCompanyIdAndCodeAndIdNot(company.getId(), code, excludeId);
            if (dup) {
                throw new DuplicateResourceException("Position", "code", code);
            }
        }
        positionRepository.findByCompanyIdOrderByNameAsc(company.getId()).stream()
                .filter(o -> !o.getId().equals(excludeId))
                .filter(o -> o.getValidTo() == null)
                .filter(o -> o.getName().equalsIgnoreCase(p.getName()))
                .findAny()
                .ifPresent(o -> {
                    throw new DuplicateResourceException("Position", "name", p.getName());
                });
    }

    private void applyLocation(Location l, LocationRequest req) {
        l.setName(requireText(req.getName(), "name"));
        l.setCode(trimToNull(req.getCode()));
        l.setAddress(trimToNull(req.getAddress()));
        if (req.getIsActive() != null) {
            l.setIsActive(req.getIsActive());
        }
    }

    private void validateLocation(Company company, Location l, Long excludeId) {
        String code = l.getCode();
        if (code != null) {
            boolean dup = excludeId == null
                    ? locationRepository.existsByCompanyIdAndCode(company.getId(), code)
                    : locationRepository.existsByCompanyIdAndCodeAndIdNot(company.getId(), code, excludeId);
            if (dup) {
                throw new DuplicateResourceException("Location", "code", code);
            }
        }
    }

    private void requireSameCompanyEmployee(Company company, Long employeeId) {
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        if (!e.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
    }

    private DepartmentResponse toDepartmentResponse(Department d) {
        DepartmentResponse dto = new DepartmentResponse();
        dto.setId(d.getId());
        dto.setCode(d.getCode());
        dto.setName(d.getName());
        dto.setManagerEmployeeId(d.getManagerEmployeeId());
        if (d.getManagerEmployeeId() != null) {
            employeeRepository.findById(d.getManagerEmployeeId())
                    .ifPresent(e -> dto.setManagerName(e.getFirstName() + " " + e.getLastName()));
        }
        dto.setValidFrom(d.getValidFrom());
        dto.setValidTo(d.getValidTo());
        dto.setEmployeeCount(employeeRepository.countByDepartmentId(d.getId()));
        return dto;
    }

    private PositionResponse toPositionResponse(Position p) {
        PositionResponse dto = new PositionResponse();
        dto.setId(p.getId());
        dto.setCode(p.getCode());
        dto.setName(p.getName());
        if (p.getDepartment() != null) {
            dto.setDepartmentId(p.getDepartment().getId());
            dto.setDepartmentName(p.getDepartment().getName());
        }
        dto.setValidFrom(p.getValidFrom());
        dto.setValidTo(p.getValidTo());
        dto.setEmployeeCount(employeeRepository.countByPositionId(p.getId()));
        return dto;
    }

    private LocationResponse toLocationResponse(Location l) {
        LocationResponse dto = new LocationResponse();
        dto.setId(l.getId());
        dto.setCode(l.getCode());
        dto.setName(l.getName());
        dto.setAddress(l.getAddress());
        dto.setIsActive(l.getIsActive());
        dto.setEmployeeCount(employeeRepository.countByLocationId(l.getId()));
        dto.setTerminalCount(terminalRepository.countByLocationId(l.getId()));
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
