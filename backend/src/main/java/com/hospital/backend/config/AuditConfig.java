package com.hospital.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables Spring Data MongoDB auditing for @CreatedDate and @LastModifiedDate.
 */
@Configuration
@EnableMongoAuditing
public class AuditConfig {
}
