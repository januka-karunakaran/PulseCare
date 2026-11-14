# 📋 Enterprise Changelog — Hospital Management System

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-11-14

### 🚀 Architecture & Enterprise Restructure (EAD Standard)
- **Architecture Refactoring:** Layered architecture aligned with Domain-Driven Design (DDD) principles.
- **DTO Layer Standardization:** Added `ApiResponse<T>`, `PagedResponse<T>`, and `ErrorResponse` common payload wrappers.
- **Validation Framework:** Introduced custom JSR-380 validators (`@ValidPhoneNumber`, `@ValidBloodGroup`).
- **Security & Protection:**
  - Integrated `RateLimitInterceptor` with sliding window request throttling per client IP.
  - Hardened Spring Security with distinct method-level `@EnableMethodSecurity`.
  - Added dedicated `/api/health` actuator probe for container orchestration.
- **Background Tasks & Jobs:** Enabled `@EnableScheduling` with automated midnight `TokenCleanupScheduler` and `AppointmentReminderScheduler`.
- **Domain Events:** Implemented Spring Application Events (`AppointmentCreatedEvent`, `AppointmentCancelledEvent`, `AppointmentEventPublisher`).
- **Logging & Observability:** Added `logback-spring.xml` with colorized console logs and rolling log files, along with environment-specific profiles (`application-dev.properties`, `application-prod.properties`, `application-test.properties`).
- **Frontend Modernization:**
  - Consolidated duplicate components and utilities.
  - Added backward-compatible facades for `@/app/components` and `@/components`.
  - Added unit test configurations for Jest and React Testing Library.
- **DevOps & Containers:**
  - Added Docker Compose for development (`docker-compose.yml`) and production (`docker-compose.prod.yml`).
  - Added GitHub Actions Continuous Integration pipeline (`.github/workflows/ci.yml`).

---

## [1.0.0] - Baseline Release
- Initial release featuring patient booking, doctor dashboard, role-based JWT auth, real-time STOMP WebSockets, and prescription PDF generation.
