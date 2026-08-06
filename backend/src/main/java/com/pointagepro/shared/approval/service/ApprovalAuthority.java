package com.pointagepro.shared.approval.service;

import com.pointagepro.auth.entity.User;
import com.pointagepro.employee.entity.Employee;
import com.pointagepro.organization.entity.Department;
import org.springframework.stereotype.Component;

/**
 * Shared approval authority for the adjustment and leave workflows (§12 alignment):
 * ADMIN is a universal approver (never on their own request - the request-level guard
 * stays in each workflow), MANAGER decides MANAGER steps of their own department,
 * HR decides HR steps. Company resolution still flows through the actor's employee
 * record, so an ADMIN user must be linked to an employee to resolve a tenant.
 */
@Component
public class ApprovalAuthority {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_HR = "HR";
    public static final String ROLE_MANAGER = "MANAGER";

    public boolean hasRole(User user, String roleCode) {
        return user.getRoles().stream().anyMatch(role -> roleCode.equals(role.getCode()));
    }

    public boolean isAdmin(User user) {
        return hasRole(user, ROLE_ADMIN);
    }

    public boolean isHr(User user) {
        return hasRole(user, ROLE_HR);
    }

    public boolean isDepartmentManagerOf(User actor, Employee employee) {
        Long actorEmployeeId = actor.getEmployeeId();
        if (actorEmployeeId == null) {
            return false;
        }
        Department department = employee.getDepartment();
        return department != null && actorEmployeeId.equals(department.getManagerEmployeeId());
    }

    /**
     * Can the actor decide a step whose assigned role is {@code approverRole}
     * for the given target employee? ADMIN overrides; MANAGER steps need the
     * department manager of the target; HR steps need the HR role.
     */
    public boolean canDecideStep(User actor, String approverRole, Employee targetEmployee) {
        if (isAdmin(actor)) {
            return true;
        }
        if (ROLE_HR.equals(approverRole)) {
            return isHr(actor);
        }
        if (ROLE_MANAGER.equals(approverRole)) {
            return isDepartmentManagerOf(actor, targetEmployee);
        }
        return false;
    }
}
