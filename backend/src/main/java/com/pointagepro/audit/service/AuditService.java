package com.pointagepro.audit.service;

import com.pointagepro.audit.entity.AuditAction;
import com.pointagepro.audit.entity.AuditLog;
import com.pointagepro.audit.repository.AuditActionRepository;
import com.pointagepro.audit.repository.AuditLogRepository;
import com.pointagepro.auth.repository.UserRepository;
import com.pointagepro.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit writer (business rules §5.4). All references are resolved to
 * managed proxies so the caller's security-context (detached) entities are never
 * attached to the persistence context.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditActionRepository auditActionRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    @Transactional
    public void log(String actionCode, Long companyId, Long userId, String entityType, Long entityId,
                    String oldValue, String newValue) {
        AuditAction action = auditActionRepository.findByCode(actionCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown audit action: " + actionCode));
        AuditLog auditLog = new AuditLog();
        auditLog.setCompany(companyId != null ? companyRepository.getReferenceById(companyId) : null);
        auditLog.setUser(userId != null ? userRepository.getReferenceById(userId) : null);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditLogRepository.save(auditLog);
    }
}
