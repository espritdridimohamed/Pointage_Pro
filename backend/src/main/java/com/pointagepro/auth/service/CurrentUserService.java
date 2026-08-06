package com.pointagepro.auth.service;

import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.company.repository.CompanyRepository;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the tenant (company) of the authenticated user from users.employee_id.
 * A user without an employee record has no tenant and is denied attendance access.
 *
 * Module 6 fallback: an ADMIN user without an employee_id is resolved to the single
 * seeded default company (companies.code = 'DEFAULT'). Non-admin users without an
 * employee record keep the AccessDeniedException (403).
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public Company requireCompany(User user) {
        if (user == null) {
            throw new AccessDeniedException("Authenticated user has no employee record");
        }
        if (user.getEmployeeId() != null) {
            return employeeRepository.findById(user.getEmployeeId())
                    .map(Employee::getCompany)
                    .orElseThrow(() -> new AccessDeniedException("Authenticated user has no employee record"));
        }
        if (isAdmin(user)) {
            return companyRepository.findByCode("DEFAULT")
                    .orElseThrow(() -> new AccessDeniedException("Default company is not configured"));
        }
        throw new AccessDeniedException("Authenticated user has no employee record");
    }

    @Transactional(readOnly = true)
    public Employee requireEmployee(User user) {
        return resolveEmployee(user);
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getCode() != null && "ADMIN".equals(role.getCode()));
    }

    private Employee resolveEmployee(User user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new AccessDeniedException("Authenticated user has no employee record");
        }
        return employeeRepository.findById(user.getEmployeeId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user has no employee record"));
    }
}
