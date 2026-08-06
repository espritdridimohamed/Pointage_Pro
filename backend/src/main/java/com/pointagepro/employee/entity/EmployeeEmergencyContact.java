package com.pointagepro.employee.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employee_emergency_contacts")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeEmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 50)
    private String relationship;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 255)
    private String address;
}
