package com.pointagepro.shared.approval.entity;

import com.pointagepro.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "approvals",
        uniqueConstraints = @UniqueConstraint(name = "uk_approval_request_step", columnNames = {"request_type", "request_id", "step_order"}))
@Getter
@Setter
@NoArgsConstructor
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "approver_role", nullable = false, length = 20)
    private String approverRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private ApprovalStatus status;

    @Column(length = 500)
    private String comment;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
