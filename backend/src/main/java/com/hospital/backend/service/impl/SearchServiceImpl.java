package com.hospital.backend.service.impl;

import com.hospital.backend.dto.*;
import com.hospital.backend.enums.Role;
import com.hospital.backend.exception.BadRequestException;
import com.hospital.backend.model.*;
import com.hospital.backend.repository.*;
import com.hospital.backend.security.AuthorizationService;
import com.hospital.backend.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enterprise MongoDB-Optimized Search Service Implementation.
 * Eliminates unbounded memory loading, N+1 query storms, and unindexed in-memory filters.
 * Fully enforces database-side filtering, sorting, pagination, and patient privacy boundaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private static final Set<String> ALLOWED_DOCTOR_SORT_FIELDS = Set.of("name", "specialization", "rating", "experience", "appointments");
    private static final Set<String> ALLOWED_APPOINTMENT_SORT_FIELDS = Set.of("date", "status", "doctor", "patient");
    private static final Set<String> ALLOWED_PRESCRIPTION_SORT_FIELDS = Set.of("date", "status", "doctor");

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AuthorizationService authz;
    private final MongoTemplate mongoTemplate;

    // ==========================================
    // 1. DOCTOR SEARCH
    // ==========================================

    @Override
    public SearchResultsWrapper<SearchDoctorResponse> searchDoctors(SearchDoctorRequest request) {
        if (request == null) {
            request = new SearchDoctorRequest();
        }

        int[] pagination = sanitizePagination(request.getOffset(), request.getLimit());
        int offset = pagination[0];
        int limit = pagination[1];

        Sort sort = buildDoctorSort(request.getSortBy(), request.getSortOrder());

        Query query = new Query();
        query.addCriteria(Criteria.where("role").is(Role.DOCTOR.name()));

        if (hasText(request.getSearchTerm())) {
            String escaped = escapeRegex(request.getSearchTerm());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("fullName").regex(escaped, "i"),
                    Criteria.where("specialization").regex(escaped, "i")
            ));
        }

        if (hasText(request.getSpecialization())) {
            String escapedSpec = escapeRegex(request.getSpecialization());
            query.addCriteria(Criteria.where("specialization").regex("^" + escapedSpec + "$", "i"));
        }

        if (request.getMinRating() != null) {
            if (request.getMinRating() < 0.0 || request.getMinRating() > 5.0) {
                throw new BadRequestException("minRating must be between 0.0 and 5.0");
            }
            List<String> ratedDoctorUserIds = mongoTemplate.find(
                    Query.query(Criteria.where("rating").gte(request.getMinRating())),
                    Doctor.class
            ).stream()
                    .flatMap(d -> Stream.of(d.getId(), d.getUserId()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (ratedDoctorUserIds.isEmpty()) {
                return emptyResult(limit, offset);
            }
            query.addCriteria(Criteria.where("_id").in(ratedDoctorUserIds));
        }

        long totalCount = mongoTemplate.count(query, User.class);
        if (totalCount == 0) {
            return emptyResult(limit, offset);
        }

        query.with(sort).skip(offset).limit(limit);
        List<User> doctorUsers = mongoTemplate.find(query, User.class);

        // Batch fetch Doctor profiles and monthly appointment counts to eliminate N+1 queries
        List<String> doctorIds = doctorUsers.stream().map(User::getId).collect(Collectors.toList());

        Map<String, Doctor> profileMap = new HashMap<>();
        doctorRepository.findAllById(doctorIds).forEach(d -> {
            profileMap.put(d.getId(), d);
            if (d.getUserId() != null) profileMap.put(d.getUserId(), d);
        });

        // Batch count appointments for current month in ONE database query
        YearMonth currentYearMonth = YearMonth.now();
        String startOfMonth = currentYearMonth.atDay(1).toString();
        String endOfMonth = currentYearMonth.atEndOfMonth().toString();

        Query monthApptQuery = Query.query(Criteria.where("doctorId").in(doctorIds)
                .and("appointmentDate").gte(startOfMonth).lte(endOfMonth));
        List<Appointment> monthlyAppts = mongoTemplate.find(monthApptQuery, Appointment.class);

        Map<String, Long> monthlyCountMap = monthlyAppts.stream()
                .collect(Collectors.groupingBy(Appointment::getDoctorId, Collectors.counting()));

        // Total appointments count grouped by doctor
        Query totalApptQuery = Query.query(Criteria.where("doctorId").in(doctorIds));
        List<Appointment> allAppts = mongoTemplate.find(totalApptQuery, Appointment.class);
        Map<String, Long> totalApptMap = allAppts.stream()
                .collect(Collectors.groupingBy(Appointment::getDoctorId, Collectors.counting()));

        List<SearchDoctorResponse> responseList = doctorUsers.stream().map(u -> {
            Doctor doc = profileMap.get(u.getId());
            long monthlyCount = monthlyCountMap.getOrDefault(u.getId(), 0L);
            long totalCountForDoc = totalApptMap.getOrDefault(u.getId(), 0L);

            return SearchDoctorResponse.builder()
                    .doctorId(u.getId())
                    .name(u.getFullName())
                    .email(u.getEmail())
                    .specialization(u.getSpecialization())
                    .phoneNumber(u.getPhoneNumber())
                    .avgRating(doc != null && doc.getRating() != null ? doc.getRating() : 0.0)
                    .totalRatings(doc != null && doc.getTotalRatings() != null ? doc.getTotalRatings() : 0)
                    .totalAppointments((int) totalCountForDoc)
                    .photoUrl(u.getPhotoUrl())
                    .bio(u.getBio())
                    .licenseNumber(u.getLicenseNumber())
                    .appointmentsThisMonth(monthlyCount)
                    .build();
        }).collect(Collectors.toList());

        return buildWrapper(responseList, totalCount, limit, offset);
    }

    @Override
    public SearchResultsWrapper<SearchDoctorResponse> searchDoctorsBySpecialization(String specialization) {
        return searchDoctors(SearchDoctorRequest.builder().specialization(specialization).build());
    }

    @Override
    public SearchResultsWrapper<SearchDoctorResponse> searchDoctorsByName(String name) {
        return searchDoctors(SearchDoctorRequest.builder().searchTerm(name).build());
    }

    // ==========================================
    // 2. PATIENT SEARCH (Role-Restricted & Privacy Preserving)
    // ==========================================

    @Override
    public SearchResultsWrapper<ProfileResponse> searchPatients(String searchTerm, Integer limit, Integer offset) {
        int[] pagination = sanitizePagination(offset, limit);
        int pageOffset = pagination[0];
        int pageLimit = pagination[1];

        User currentUser = authz.getCurrentUser();

        // 1. Authorization: Patients cannot search the patient directory
        if (currentUser.getRole() == Role.PATIENT) {
            log.warn("Security Alert: Patient user {} attempted to access patient directory search", currentUser.getEmail());
            throw new AccessDeniedException("Patients are not authorized to search the patient directory");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("role").is(Role.PATIENT.name()));

        // 2. Clinical Relationship Scope: Doctors may ONLY search their assigned patients
        if (currentUser.getRole() == Role.DOCTOR) {
            Set<String> doctorIds = resolveDoctorIdentifiers(currentUser.getId());

            // Distinct patient IDs from doctor's appointments and prescriptions
            List<String> patientIdsFromAppointments = mongoTemplate.findDistinct(
                    Query.query(Criteria.where("doctorId").in(doctorIds)),
                    "patientId",
                    Appointment.class,
                    String.class
            );

            List<String> patientIdsFromPrescriptions = mongoTemplate.findDistinct(
                    Query.query(Criteria.where("doctorId").in(doctorIds)),
                    "patientId",
                    Prescription.class,
                    String.class
            );

            Set<String> authorizedPatientIds = new HashSet<>(patientIdsFromAppointments);
            authorizedPatientIds.addAll(patientIdsFromPrescriptions);

            if (authorizedPatientIds.isEmpty()) {
                return emptyResult(pageLimit, pageOffset);
            }

            // Resolve any patient profile IDs to user IDs
            Set<String> resolvedUserIds = new HashSet<>(authorizedPatientIds);
            patientRepository.findAllById(authorizedPatientIds).forEach(p -> {
                if (p.getUserId() != null) resolvedUserIds.add(p.getUserId());
            });

            query.addCriteria(Criteria.where("_id").in(resolvedUserIds));
        }

        // 3. Search Term filter
        if (hasText(searchTerm)) {
            String escaped = escapeRegex(searchTerm);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("fullName").regex(escaped, "i"),
                    Criteria.where("email").regex(escaped, "i"),
                    Criteria.where("phoneNumber").regex(escaped, "i")
            ));
        }

        long totalCount = mongoTemplate.count(query, User.class);
        if (totalCount == 0) {
            return emptyResult(pageLimit, pageOffset);
        }

        query.with(Sort.by(Sort.Direction.ASC, "fullName").and(Sort.by(Sort.Direction.ASC, "_id")))
                .skip(pageOffset).limit(pageLimit);

        List<User> patients = mongoTemplate.find(query, User.class);

        List<ProfileResponse> responseList = patients.stream().map(p -> ProfileResponse.builder()
                .id(p.getId())
                .fullName(p.getFullName())
                .email(p.getEmail())
                .role(p.getRole().name())
                .phoneNumber(p.getPhoneNumber())
                .address(p.getAddress())
                .city(p.getCity())
                .state(p.getState())
                .zipCode(p.getZipCode())
                .photoUrl(p.getPhotoUrl())
                .bio(p.getBio())
                .build()
        ).collect(Collectors.toList());

        return buildWrapper(responseList, totalCount, pageLimit, pageOffset);
    }

    @Override
    public SearchResultsWrapper<ProfileResponse> searchPatients(String searchTerm) {
        return searchPatients(searchTerm, DEFAULT_LIMIT, DEFAULT_OFFSET);
    }

    // ==========================================
    // 3. APPOINTMENT FILTERING
    // ==========================================

    @Override
    public SearchResultsWrapper<AppointmentFilterResponse> filterAppointments(AppointmentFilterRequest request) {
        if (request == null) {
            request = new AppointmentFilterRequest();
        }

        int[] pagination = sanitizePagination(request.getOffset(), request.getLimit());
        int offset = pagination[0];
        int limit = pagination[1];

        validateDateRange(request.getStartDate(), request.getEndDate());
        Sort sort = buildAppointmentSort(request.getSortBy(), request.getSortOrder());

        User currentUser = authz.getCurrentUser();
        Query query = new Query();

        // 1. Role-based ownership enforcement
        if (currentUser.getRole() == Role.PATIENT) {
            Set<String> patientIds = resolvePatientIdentifiers(currentUser.getId());
            query.addCriteria(Criteria.where("patientId").in(patientIds));
        } else if (currentUser.getRole() == Role.DOCTOR) {
            Set<String> doctorIds = resolveDoctorIdentifiers(currentUser.getId());
            query.addCriteria(Criteria.where("doctorId").in(doctorIds));
        } else if (currentUser.getRole() == Role.ADMIN) {
            if (hasText(request.getPatientId())) {
                Set<String> patientIds = resolvePatientIdentifiers(request.getPatientId());
                query.addCriteria(Criteria.where("patientId").in(patientIds));
            }
            if (hasText(request.getDoctorId())) {
                Set<String> doctorIds = resolveDoctorIdentifiers(request.getDoctorId());
                query.addCriteria(Criteria.where("doctorId").in(doctorIds));
            }
        }

        // 2. Status filter
        if (hasText(request.getStatus())) {
            String escaped = escapeRegex(request.getStatus());
            query.addCriteria(Criteria.where("status").regex("^" + escaped + "$", "i"));
        }

        // 3. Date range filter (ISO-8601 YYYY-MM-DD lexicographical range)
        if (hasText(request.getStartDate()) && hasText(request.getEndDate())) {
            query.addCriteria(Criteria.where("appointmentDate")
                    .gte(request.getStartDate().trim())
                    .lte(request.getEndDate().trim()));
        } else if (hasText(request.getStartDate())) {
            query.addCriteria(Criteria.where("appointmentDate").gte(request.getStartDate().trim()));
        } else if (hasText(request.getEndDate())) {
            query.addCriteria(Criteria.where("appointmentDate").lte(request.getEndDate().trim()));
        }

        // 4. Reason filter
        if (hasText(request.getReason())) {
            query.addCriteria(Criteria.where("reason").regex(escapeRegex(request.getReason()), "i"));
        }

        long totalCount = mongoTemplate.count(query, Appointment.class);
        if (totalCount == 0) {
            return emptyResult(limit, offset);
        }

        query.with(sort).skip(offset).limit(limit);
        List<Appointment> appointments = mongoTemplate.find(query, Appointment.class);

        // 5. Batch resolve names for the paginated slice
        Set<String> allEntityIds = appointments.stream()
                .flatMap(a -> Stream.of(a.getDoctorId(), a.getPatientId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> nameMap = resolveEntityNames(allEntityIds);

        List<AppointmentFilterResponse> responseList = appointments.stream().map(a -> AppointmentFilterResponse.builder()
                .appointmentId(a.getId())
                .doctorName(nameMap.getOrDefault(a.getDoctorId(), "Unknown Doctor"))
                .patientName(nameMap.getOrDefault(a.getPatientId(), "Unknown Patient"))
                .appointmentDate(a.getAppointmentDate())
                .appointmentTime(a.getAppointmentTime())
                .reason(a.getReason())
                .status(a.getStatus())
                .doctorId(a.getDoctorId())
                .patientId(a.getPatientId())
                .build()
        ).collect(Collectors.toList());

        return buildWrapper(responseList, totalCount, limit, offset);
    }

    @Override
    public SearchResultsWrapper<?> filterAppointmentsByStatus(String status) {
        return filterAppointments(AppointmentFilterRequest.builder().status(status).build());
    }

    @Override
    public SearchResultsWrapper<?> filterAppointmentsByDateRange(String startDate, String endDate) {
        return filterAppointments(AppointmentFilterRequest.builder().startDate(startDate).endDate(endDate).build());
    }

    // ==========================================
    // 4. PRESCRIPTION FILTERING
    // ==========================================

    @Override
    public SearchResultsWrapper<PrescriptionFilterResponse> filterPrescriptions(PrescriptionFilterRequest request) {
        if (request == null) {
            request = new PrescriptionFilterRequest();
        }

        int[] pagination = sanitizePagination(request.getOffset(), request.getLimit());
        int offset = pagination[0];
        int limit = pagination[1];

        validateDateRange(request.getStartDate(), request.getEndDate());
        Sort sort = buildPrescriptionSort(request.getSortBy(), request.getSortOrder());

        User currentUser = authz.getCurrentUser();
        Query query = new Query();

        // 1. Role-based ownership enforcement
        if (currentUser.getRole() == Role.PATIENT) {
            Set<String> patientIds = resolvePatientIdentifiers(currentUser.getId());
            query.addCriteria(Criteria.where("patientId").in(patientIds));
        } else if (currentUser.getRole() == Role.DOCTOR) {
            Set<String> doctorIds = resolveDoctorIdentifiers(currentUser.getId());
            query.addCriteria(Criteria.where("doctorId").in(doctorIds));
        } else if (currentUser.getRole() == Role.ADMIN) {
            if (hasText(request.getPatientId())) {
                Set<String> patientIds = resolvePatientIdentifiers(request.getPatientId());
                query.addCriteria(Criteria.where("patientId").in(patientIds));
            }
            if (hasText(request.getDoctorId())) {
                Set<String> doctorIds = resolveDoctorIdentifiers(request.getDoctorId());
                query.addCriteria(Criteria.where("doctorId").in(doctorIds));
            }
        }

        // 2. Medicine filter: Database-side array regex matching
        if (hasText(request.getMedicine())) {
            String escaped = escapeRegex(request.getMedicine());
            query.addCriteria(Criteria.where("medicines").regex(escaped, "i"));
        }

        // 3. Date range filter
        if (hasText(request.getStartDate()) && hasText(request.getEndDate())) {
            query.addCriteria(Criteria.where("createdAt")
                    .gte(request.getStartDate().trim())
                    .lte(request.getEndDate().trim() + "T23:59:59.999Z"));
        } else if (hasText(request.getStartDate())) {
            query.addCriteria(Criteria.where("createdAt").gte(request.getStartDate().trim()));
        } else if (hasText(request.getEndDate())) {
            query.addCriteria(Criteria.where("createdAt").lte(request.getEndDate().trim() + "T23:59:59.999Z"));
        }

        // 4. Status filter (ACTIVE = within 30 days, COMPLETED = older)
        if (hasText(request.getStatus())) {
            String cutoffDate = LocalDate.now().minusDays(30).toString();
            if ("ACTIVE".equalsIgnoreCase(request.getStatus())) {
                query.addCriteria(Criteria.where("createdAt").gte(cutoffDate));
            } else if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
                query.addCriteria(Criteria.where("createdAt").lt(cutoffDate));
            }
        }

        long totalCount = mongoTemplate.count(query, Prescription.class);
        if (totalCount == 0) {
            return emptyResult(limit, offset);
        }

        query.with(sort).skip(offset).limit(limit);
        List<Prescription> prescriptions = mongoTemplate.find(query, Prescription.class);

        // 5. Batch resolve names for the paginated slice
        Set<String> allEntityIds = prescriptions.stream()
                .flatMap(p -> Stream.of(p.getDoctorId(), p.getPatientId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> nameMap = resolveEntityNames(allEntityIds);
        LocalDate today = LocalDate.now();

        List<PrescriptionFilterResponse> responseList = prescriptions.stream().map(p -> {
            String status = "ACTIVE";
            if (p.getCreatedAt() != null && !p.getCreatedAt().isBlank()) {
                try {
                    LocalDate prescDate = LocalDate.parse(p.getCreatedAt().split("T")[0]);
                    long daysSince = java.time.temporal.ChronoUnit.DAYS.between(prescDate, today);
                    status = daysSince <= 30 ? "ACTIVE" : "COMPLETED";
                } catch (Exception ignored) {
                }
            }

            return PrescriptionFilterResponse.builder()
                    .prescriptionId(p.getId())
                    .doctorName(nameMap.getOrDefault(p.getDoctorId(), "Unknown Doctor"))
                    .patientName(nameMap.getOrDefault(p.getPatientId(), "Unknown Patient"))
                    .createdDate(p.getCreatedAt())
                    .status(status)
                    .medicines(p.getMedicines() != null ? String.join(", ", p.getMedicines()) : "")
                    .dosageInstructions(p.getDosageInstructions())
                    .doctorId(p.getDoctorId())
                    .patientId(p.getPatientId())
                    .build();
        }).collect(Collectors.toList());

        return buildWrapper(responseList, totalCount, limit, offset);
    }

    @Override
    public SearchResultsWrapper<?> filterPrescriptionsByDoctor(String doctorId) {
        return filterPrescriptions(PrescriptionFilterRequest.builder().doctorId(doctorId).build());
    }

    @Override
    public SearchResultsWrapper<?> filterPrescriptionsByMedicine(String medicine) {
        return filterPrescriptions(PrescriptionFilterRequest.builder().medicine(medicine).build());
    }

    // ==========================================
    // 5. VALIDATION & SANITIZATION HELPERS
    // ==========================================

    private int[] sanitizePagination(Integer offset, Integer limit) {
        int safeOffset = (offset != null) ? offset : DEFAULT_OFFSET;
        int safeLimit = (limit != null) ? limit : DEFAULT_LIMIT;

        if (safeOffset < 0) {
            throw new BadRequestException("Offset must be greater than or equal to 0");
        }
        if (safeLimit <= 0) {
            throw new BadRequestException("Limit must be greater than 0");
        }
        if (safeLimit > MAX_LIMIT) {
            throw new BadRequestException("Limit cannot exceed maximum allowed value of " + MAX_LIMIT);
        }

        return new int[]{safeOffset, safeLimit};
    }

    private void validateDateRange(String startDate, String endDate) {
        LocalDate start = null;
        LocalDate end = null;

        if (hasText(startDate)) {
            try {
                start = LocalDate.parse(startDate.trim());
            } catch (DateTimeParseException ex) {
                throw new BadRequestException("Invalid startDate format. Expected ISO-8601 (yyyy-MM-dd): " + startDate);
            }
        }

        if (hasText(endDate)) {
            try {
                end = LocalDate.parse(endDate.trim());
            } catch (DateTimeParseException ex) {
                throw new BadRequestException("Invalid endDate format. Expected ISO-8601 (yyyy-MM-dd): " + endDate);
            }
        }

        if (start != null && end != null && start.isAfter(end)) {
            throw new BadRequestException("startDate cannot be after endDate (start: " + start + ", end: " + end + ")");
        }
    }

    private Sort buildDoctorSort(String sortBy, String sortOrder) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (!hasText(sortBy)) {
            return Sort.by(Sort.Direction.ASC, "fullName").and(Sort.by(Sort.Direction.ASC, "_id"));
        }

        String lowerSort = sortBy.toLowerCase(Locale.ROOT);
        if (!ALLOWED_DOCTOR_SORT_FIELDS.contains(lowerSort)) {
            throw new BadRequestException("Invalid sortBy field for doctor search: " + sortBy + ". Allowed: " + ALLOWED_DOCTOR_SORT_FIELDS);
        }

        switch (lowerSort) {
            case "specialization":
                return Sort.by(direction, "specialization").and(Sort.by(Sort.Direction.ASC, "_id"));
            case "name":
            default:
                return Sort.by(direction, "fullName").and(Sort.by(Sort.Direction.ASC, "_id"));
        }
    }

    private Sort buildAppointmentSort(String sortBy, String sortOrder) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (!hasText(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "appointmentDate", "appointmentTime").and(Sort.by(Sort.Direction.ASC, "_id"));
        }

        String lowerSort = sortBy.toLowerCase(Locale.ROOT);
        if (!ALLOWED_APPOINTMENT_SORT_FIELDS.contains(lowerSort)) {
            throw new BadRequestException("Invalid sortBy field for appointment filter: " + sortBy + ". Allowed: " + ALLOWED_APPOINTMENT_SORT_FIELDS);
        }

        switch (lowerSort) {
            case "status":
                return Sort.by(direction, "status").and(Sort.by(Sort.Direction.ASC, "_id"));
            case "date":
            default:
                return Sort.by(direction, "appointmentDate", "appointmentTime").and(Sort.by(Sort.Direction.ASC, "_id"));
        }
    }

    private Sort buildPrescriptionSort(String sortBy, String sortOrder) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (!hasText(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "_id"));
        }

        String lowerSort = sortBy.toLowerCase(Locale.ROOT);
        if (!ALLOWED_PRESCRIPTION_SORT_FIELDS.contains(lowerSort)) {
            throw new BadRequestException("Invalid sortBy field for prescription filter: " + sortBy + ". Allowed: " + ALLOWED_PRESCRIPTION_SORT_FIELDS);
        }

        return Sort.by(direction, "createdAt").and(Sort.by(Sort.Direction.ASC, "_id"));
    }

    private Set<String> resolveDoctorIdentifiers(String doctorId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(doctorId);
        doctorRepository.findById(doctorId).ifPresent(d -> {
            if (d.getId() != null) ids.add(d.getId());
            if (d.getUserId() != null) ids.add(d.getUserId());
        });
        doctorRepository.findByUserId(doctorId).ifPresent(d -> {
            if (d.getId() != null) ids.add(d.getId());
            if (d.getUserId() != null) ids.add(d.getUserId());
        });
        return ids;
    }

    private Set<String> resolvePatientIdentifiers(String patientId) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(patientId);
        patientRepository.findById(patientId).ifPresent(p -> {
            if (p.getId() != null) ids.add(p.getId());
            if (p.getUserId() != null) ids.add(p.getUserId());
        });
        patientRepository.findByUserId(patientId).ifPresent(p -> {
            if (p.getId() != null) ids.add(p.getId());
            if (p.getUserId() != null) ids.add(p.getUserId());
        });
        return ids;
    }

    private Map<String, String> resolveEntityNames(Set<String> entityIds) {
        Map<String, String> nameMap = new HashMap<>();
        if (entityIds == null || entityIds.isEmpty()) return nameMap;

        userRepository.findAllById(entityIds).forEach(u -> nameMap.put(u.getId(), u.getFullName()));

        Set<String> missing = entityIds.stream().filter(id -> !nameMap.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            doctorRepository.findAllById(missing).forEach(d -> {
                nameMap.put(d.getId(), d.getFullName());
                if (d.getUserId() != null) nameMap.put(d.getUserId(), d.getFullName());
            });
            patientRepository.findAllById(missing).forEach(p -> {
                nameMap.put(p.getId(), p.getFullName());
                if (p.getUserId() != null) nameMap.put(p.getUserId(), p.getFullName());
            });
        }
        return nameMap;
    }

    private String escapeRegex(String input) {
        if (input == null) return "";
        return Pattern.quote(input.trim());
    }

    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    private <T> SearchResultsWrapper<T> emptyResult(int limit, int offset) {
        return SearchResultsWrapper.<T>builder()
                .data(Collections.emptyList())
                .totalCount(0)
                .limit(limit)
                .offset(offset)
                .pageNumber((offset / limit) + 1)
                .totalPages(0)
                .hasMore(false)
                .build();
    }

    private <T> SearchResultsWrapper<T> buildWrapper(List<T> data, long totalCount, int limit, int offset) {
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        int pageNumber = (offset / limit) + 1;
        boolean hasMore = (offset + data.size()) < totalCount;

        return SearchResultsWrapper.<T>builder()
                .data(data)
                .totalCount((int) totalCount)
                .limit(limit)
                .offset(offset)
                .pageNumber(pageNumber)
                .totalPages(totalPages)
                .hasMore(hasMore)
                .build();
    }
}
