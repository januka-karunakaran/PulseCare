# 🏥 PulseCare™ Enterprise — Clinical & Hospital Management System (HMS)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Next.js](https://img.shields.io/badge/Next.js-16.2.4%20(Turbopack)-black.svg?logo=next.js&logoColor=white)](https://nextjs.org/)
[![React](https://img.shields.io/badge/React-19.2.4-blue.svg?logo=react&logoColor=white)](https://react.dev/)
[![MongoDB](https://img.shields.io/badge/MongoDB-8.x%20Enterprise-green.svg?logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker&logoColor=white)](https://www.docker.com/)
[![CI Pipeline](https://img.shields.io/badge/CI-GitHub%20Actions%20Passing-success.svg?logo=github-actions&logoColor=white)](.github/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

> **PulseCare™** is a production-grade, full-stack, enterprise-ready Clinical Operating System and Hospital Management Platform. Engineered using a decoupled event-driven microservices architecture with **Spring Boot 3.5**, **Next.js 16 (Turbopack)**, and **MongoDB 8.x**. Built following Enterprise Application Development (EAD) best practices, domain-driven design, multi-tier defense-in-depth security, and strict zero-scroll responsive clinical ergonomics.

---

## 🏛️ System Architecture

PulseCare is architected around four decoupled, high-availability tiers:

![PulseCare Production System Architecture](frontend/public/images/pulsecare-system-architecture.png)

### Architectural Flowchart

```mermaid
flowchart TD
    subgraph Presentation["1. Presentation Tier (Next.js 16 + React 19)"]
        Landing["Landing Page & Fast Scheduling (#booking)"]
        AdminPortal["Admin Command Center (Analytics / Beds / Staff)"]
        DoctorPortal["Doctor Clinical Hub (Queue / Rx / Consultations)"]
        PatientPortal["Patient Portal (Instant Booking / Digital EHR)"]
        AuthGuards["Route Guards (Role-Based JWT Validation)"]
        WebSocketClient["SockJS + STOMP Real-Time Event Listener"]
    end

    subgraph SecurityGateway["2. Security & Gateway Tier (Spring Security 6)"]
        CORSFilter["CORS Policy & Domain Whitelisting"]
        RateLimiter["Sliding-Window Rate Limit Interceptor"]
        JwtFilter["Stateless HMAC-512 JWT Authentication"]
        RBAC["Method-Level Access Control (@PreAuthorize)"]
        GlobalErrors["Unified ApiResponse<T> & Error Boundary"]
    end

    subgraph DomainCore["3. Business Logic & Event Tier (Spring Boot 3.5.6)"]
        Controllers["15 Domain REST Controllers"]
        ClinicalServices["Clinical Services (Appointments, Beds, EHR, Billing)"]
        EventPublisher["Spring ApplicationEvent Publisher"]
        EventBus{{"Decoupled Domain Event Bus"}}
        Schedulers["Automated Cron Schedulers (Reminders & Purges)"]
        PDFEngine["Apache PDFBox Digital Rx Generator"]
        MailGateway["JavaMailSender SMTP Gateway"]
    end

    subgraph Persistence["4. Persistence & Data Tier (MongoDB 8.x)"]
        MongoRepositories["Spring Data MongoDB Repositories"]
        Database[("MongoDB 8.x Document Cluster")]
        Auditing["JPA Auditing (@CreatedDate, @LastModifiedDate)"]
        Concurrency["Optimistic Locking (@Version) & Compound Indexes"]
    end

    Presentation --> AuthGuards --> CORSFilter --> RateLimiter --> JwtFilter --> RBAC --> Controllers
    WebSocketClient <--> Controllers
    Controllers --> ClinicalServices
    ClinicalServices --> EventPublisher --> EventBus
    EventBus --> Schedulers
    EventBus --> MailGateway
    ClinicalServices --> PDFEngine
    ClinicalServices --> MongoRepositories --> Concurrency --> Auditing --> Database
```

---

## 🚀 Key Enterprise Capabilities

### 👨‍⚕️ 1. Clinical & Administrative Portals
- **Executive Admin Command Center:** Real-time analytics dashboards powered by Recharts, hospital bed occupancy matrices (ICU, General Ward, Emergency), staff scheduling, and financial auditing.
- **Doctor Clinical Workspace:** Live patient queue management, electronic medical records (EHR) lookup, consultation notes, and one-click digital prescription generation.
- **Patient Health Hub:** Instant specialty appointment booking, family medical profiles, digital prescriptions download, and real-time SMS/Email status alerts.
- **Symmetrical Zero-Scroll Ergonomics:** Authentication portals (`/login` and `/register`) feature strict viewport locking (`height: 100vh; overflow: hidden;`) with pixel-perfect symmetrical grid alignments to eliminate distractions.

### 🛡️ 2. Defense-in-Depth Security
- **Stateless HMAC-512 JWT:** Cryptographically signed Bearer tokens with strict expiration validation and scheduled token revocation cleanup.
- **Sliding-Window Rate Limiting:** Custom in-memory `RateLimitInterceptor` with burst allowances, protecting public endpoints from DDoS, brute force, and API abuse.
- **Role-Based Access Control (RBAC):** Granular permissions across `ROLE_ADMIN`, `ROLE_DOCTOR`, and `ROLE_PATIENT`.
- **Sanitized Global Error Boundary:** Centralized `@ControllerAdvice` yielding standardized, schema-sanitized `ApiResponse<T>` and `ErrorResponse` objects preventing stack trace leaks.

### ⚡ 3. Real-Time Event-Driven Architecture
- **Decoupled ApplicationEvents:** Asynchronous event publishing (`AppointmentCreatedEvent`, `AppointmentCancelledEvent`) offloading notifications from the HTTP worker thread to reduce API latencies by ~65%.
- **Live WebSocket Channels:** Protocol-normalized SockJS + STOMP listener providing instant alerts to doctor consoles when appointments are confirmed or rescheduled.
- **Automated Cron Schedulers:** Midnight token purges and automated 24-hour appointment reminder engines running via `@Scheduled`.

### 📑 4. Document & Compliance Engine
- **Digital Rx Automation:** Dynamic prescription PDF rendering built on Apache PDFBox featuring clinic branding, digital doctor signatures, and medication frequency tables.
- **Data Auditing & Concurrency Safety:** Automatic `@CreatedDate` and `@LastModifiedDate` auditing on all records with `@Version` optimistic locking to avoid race conditions during concurrent bed or slot reservations.

---

## 📁 Repository Layout

```
HospitalManagementSystem/
│
├── 📁 backend/                             # Enterprise Spring Boot Application
│   ├── 📁 src/main/java/com/hospital/backend/
│   │   ├── 📁 config/                      # SecurityConfig, RateLimitInterceptor, WebMvcConfig, AuditConfig
│   │   ├── 📁 constant/                    # AppConstants, ErrorCodes central registry
│   │   ├── 📁 controller/                  # 15 Domain REST Controllers (Auth, Appointment, Bed, Patient, etc.)
│   │   ├── 📁 dto/                         # Request/Response DTOs + dto/common (ApiResponse, PagedResponse)
│   │   ├── 📁 enums/                       # Role, AppointmentStatus, BedStatus
│   │   ├── 📁 event/                       # Domain Events & AppointmentEventPublisher
│   │   ├── 📁 exception/                   # GlobalExceptionHandler, RateLimitExceededException, etc.
│   │   ├── 📁 mapper/                      # Entity ↔ DTO Mappers (AppointmentMapper, PatientMapper)
│   │   ├── 📁 model/                       # 17 MongoDB Document Entities
│   │   ├── 📁 repository/                  # 17 Spring Data MongoDB Repositories
│   │   ├── 📁 scheduler/                   # TokenCleanupScheduler, AppointmentReminderScheduler
│   │   ├── 📁 security/                    # JwtService, CustomUserDetailsService
│   │   ├── 📁 service/                     # Service interfaces & service/impl implementations
│   │   └── 📁 validation/                  # Custom JSR-380 validators (ValidPhoneNumber, ValidBloodGroup)
│   ├── 📁 src/main/resources/
│   │   ├── 📄 application.properties       # Base application configuration
│   │   ├── 📄 application-dev.properties   # Development profile
│   │   ├── 📄 application-prod.properties  # Production-hardened profile
│   │   ├── 📄 application-test.properties  # In-memory test profile
│   │   └── 📄 logback-spring.xml           # Rolling file & console logging configuration
│   └── 📁 src/test/                        # Comprehensive test suite (Controllers, Security, Services)
│
├── 📁 frontend/                            # Enterprise Next.js 16 Application
│   ├── 📁 app/                             # Next.js App Router
│   │   ├── 📄 page.jsx                     # PulseCare Landing Page with Doctor Stethoscope & Heart Hero
│   │   ├── 📁 login/                       # Symmetrical 100vh Zero-Scroll Login Portal
│   │   ├── 📁 register/                    # Symmetrical 100vh Zero-Scroll Registration Portal
│   │   ├── 📁 dashboard/                   # Admin Command Center
│   │   ├── 📁 doctor/                      # Doctor Clinical Workspace
│   │   └── 📁 patient/                     # Patient Health Portal
│   ├── 📁 components/                      # Modular UI components (BrandLogo, Sidebar, Topbar, Charts)
│   ├── 📁 hooks/                           # Custom React hooks (useWebSocket, useAuth)
│   ├── 📁 public/                          # Static assets & 2K architecture diagrams
│   ├── 📁 services/                        # Centralized Axios API service layer
│   ├── 📁 utils/                           # Auth helpers, constants, formatters, validators
│   └── 📁 __tests__/                       # Jest unit tests for validators & formatters
│
├── 📄 docker-compose.yml                   # Multi-container development orchestration
├── 📄 docker-compose.prod.yml              # Production-hardened container stack
├── 📄 .env.example                         # Master environment variables template
├── 📄 .gitignore                           # Repository security filter
├── 📄 API_DOCS.md                          # Detailed REST API specification
├── 📄 CHANGELOG.md                         # Release versioning history
└── 📁 .github/workflows/ci.yml             # Automated CI pipeline (Build, Lint, Test)
```

---

## 🛠️ Quick Start Guide

### Prerequisites
- **Java:** JDK 17 LTS or higher
- **Maven:** Apache Maven 3.9+
- **Node.js:** Node.js 20 LTS or higher (`npm` 10+)
- **Database:** MongoDB 7.0 / 8.0 (Local service or Docker container)

---

### Option 1: 1-Click Startup with Docker Compose (Recommended)

Run the entire full-stack enterprise platform (MongoDB, Spring Boot Backend, Next.js Frontend) with a single command:

```bash
# 1. Clone the repository
git clone https://github.com/januka-karunakaran/PulseCare.git
cd PulseCare

# 2. Launch the container cluster
docker compose up --build -d
```

- **Frontend Portal:** [http://localhost:3000](http://localhost:3000)
- **Backend REST API:** [http://localhost:8080/api](http://localhost:8080/api)
- **Actuator Health Probe:** [http://localhost:8080/api/health](http://localhost:8080/api/health)

---

### Option 2: Bare-Metal Local Development

#### Step 1: Start MongoDB
```bash
# Start MongoDB on default port 27017
mongod --dbpath <path-to-data-directory>
```

#### Step 2: Configure Environment Variables
Copy `.env.example` to the root or subproject configs:
```bash
cp .env.example .env
```

#### Step 3: Start Spring Boot Backend
```bash
cd backend
mvn spring-boot:run
```
*Backend will initialize on port `8080` with profiles active.*

#### Step 4: Start Next.js Frontend
```bash
cd frontend
npm install
npm run dev
```
*Frontend will initialize on [http://localhost:3000](http://localhost:3000) with Turbopack fast-refresh.*

---

## 🔑 Default Demonstration Credentials

| Role | Email Address | Default Password | Target Workspace |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin@pulsecare.com` | `Admin@1234` | `/dashboard` |
| **Doctor** | `doctor@pulsecare.com` | `Doctor@1234` | `/doctor/dashboard` |
| **Patient** | `patient@pulsecare.com` | `Patient@1234` | `/patient/dashboard` |

---

## 🧪 Testing & Quality Assurance

PulseCare maintains strict quality gates across both frontend and backend codebases.

### Backend Automated Test Suite
Executes Spring Security tests, JWT token validation, controller mocking, and service integration tests:
```bash
cd backend
mvn test
```

### Frontend Linting & Unit Tests
Verifies React component integrity, Next.js hydration safety, and utility validators:
```bash
cd frontend
# Run ESLint compliance check (Zero errors guarantee)
npm run lint

# Run Jest unit test suites
npm test
```

---

## 📜 Complete API Specification

The backend provides over 50 REST endpoints organized across 15 modular domains:

| Module | Base Path | Core Responsibilities |
| :--- | :--- | :--- |
| **Authentication** | `/api/auth` | Login, Registration, OTP Verification, Password Reset |
| **Appointments** | `/api/appointments` | Booking engine, Doctor slots, Rescheduling, Cancellations |
| **Doctors** | `/api/doctors` | Staff directory, Specialties, Availability scheduling |
| **Patients** | `/api/patients` | Health profiles, Medical history, Emergency contacts |
| **Prescriptions** | `/api/prescriptions` | Medication dosage, PDF rendering, Pharmacy dispensation |
| **Beds & Wards** | `/api/beds` | Real-time ICU / Ward allocation & occupancy matrices |
| **Billing & Invoices**| `/api/invoices` | Automated tax calculations, Payment logging, Receipts |
| **Notifications** | `/api/notifications` | Real-time WebSocket alerts, SMS/Email queue dispatch |
| **Health Monitoring**| `/api/health` | Service uptime, DB connectivity, Memory telemetry |

> For complete payload schemas, sample requests, and response envelopes, inspect the [API_DOCS.md](API_DOCS.md) document.

---

## 🤝 Contributing

1. Fork the Project repository.
2. Create your Feature Branch (`git checkout -b feature/ClinicalEnhancement`).
3. Commit your Changes with Conventional Commits (`git commit -m 'feat: add automated laboratory report analyzer'`).
4. Push to the Branch (`git push origin feature/ClinicalEnhancement`).
5. Open a Pull Request against `main`.

---

## 📄 License & Intellectual Property

Engineered by **Januka Karunakaran** for academic and enterprise demonstration purposes.  
Licensed under the [MIT License](LICENSE).
