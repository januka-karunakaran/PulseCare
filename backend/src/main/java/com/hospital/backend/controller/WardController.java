package com.hospital.backend.controller;

import com.hospital.backend.model.HospitalWard;
import com.hospital.backend.service.WardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
public class WardController {

    private final WardService wardService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HospitalWard> createWard(@RequestBody HospitalWard ward) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wardService.createWard(ward));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<List<HospitalWard>> getAllWards(@RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly) {
        if (activeOnly) {
            return ResponseEntity.ok(wardService.getActiveWards());
        }
        return ResponseEntity.ok(wardService.getAllWards());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'BED_MANAGER')")
    public ResponseEntity<HospitalWard> getWardById(@PathVariable String id) {
        return ResponseEntity.ok(wardService.getWardById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HospitalWard> updateWard(@PathVariable String id, @RequestBody HospitalWard ward) {
        return ResponseEntity.ok(wardService.updateWard(id, ward));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWard(@PathVariable String id) {
        wardService.deleteWard(id);
        return ResponseEntity.noContent().build();
    }
}
