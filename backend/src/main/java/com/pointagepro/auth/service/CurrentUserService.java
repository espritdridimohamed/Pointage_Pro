package com.pointagepro.auth.service;

import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the tenant (company) of the authenticated user from users.employee_id.
 * A user without an employee record has no tenant and is denied attendance access.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public Company requireCompany(User user) {
        Employee employee = resolveEmployee(user);
        return employee.getCompany();
    }

    @Transactional(readOnly = true)
    public Employee requireEmployee(User user) {
        return resolveEmployee(user);
    }

    private Employee resolveEmployee(User user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new AccessDeniedException("Authenticated user has no employee record");
        }
        return employeeRepository.findById(user.getEmployeeId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user has no employee record"));
    }
}
