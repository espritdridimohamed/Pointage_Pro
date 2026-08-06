package com.pointagepro.payroll.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payslips")
@Getter
@Setter
@NoArgsConstructor
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_item_id", nullable = false, unique = true)
    private PayrollItem payrollItem;

    @Column(name = "payslip_number", length = 30)
    private String payslipNumber;

    @Column(name = "pdf_path", length = 255)
    private String pdfPath;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
