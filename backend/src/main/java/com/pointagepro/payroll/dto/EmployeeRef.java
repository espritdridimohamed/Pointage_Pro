package com.pointagepro.payroll.dto;

import com.pointagepro.employee.entity.Employee;

public class EmployeeRef {

    private final Long id;
    private final String matricule;
    private final String firstName;
    private final String lastName;

    public EmployeeRef(Employee e) {
        this.id = e.getId();
        this.matricule = e.getMatricule();
        this.firstName = e.getFirstName();
        this.lastName = e.getLastName();
    }

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
