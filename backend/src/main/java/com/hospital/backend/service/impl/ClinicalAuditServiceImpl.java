package com.hospital.backend.service.impl;

import com.hospital.backend.enums.ClinicalAuditAction;
import com.hospital.backend.enums.Role;
import com.hospital.backend.model.ClinicalAuditLog;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.ClinicalAuditLogRepository;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.service.ClinicalAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicalAuditServiceImpl implements ClinicalAuditService {

    private final ClinicalAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    public void audit(String patientId,
                      String resourceType,
                      String resourceId,
                      ClinicalAuditAction action,
                      String outcome,
                      String correlationId,
                      String details) {
        try {
            String actorUserId = "SYSTEM";
            String actorEmail = "system@hospital.internal";
            Role actorRole = Role.ADMIN;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                actorEmail = auth.getName();
                User user = userRepository.findByEmail(actorEmail).orElse(null);
                if (user != null) {
                    actorUserId = user.getId();
                    actorRole = user.getRole();
                }
            }

            ClinicalAuditLog auditLog = ClinicalAuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .actorUserId(actorUserId)
                    .actorEmail(actorEmail)
                    .actorRole(actorRole)
                    .patientId(patientId)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .action(action)
                    .occurredAt(Instant.now())
                    .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                    .outcome(outcome)
                    .details(sanitizeDetails(details))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist clinical audit trail record: {}", e.getMessage());
        }
    }

    private String sanitizeDetails(String details) {
        if (details == null) return "";
        // Strip newlines and cap length to avoid log flooding or inadvertent PHI injection
        String sanitized = details.replaceAll("[\\r\\n]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
