package com.hospital.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class BloodGroupValidator implements ConstraintValidator<ValidBloodGroup, String> {

    private static final Set<String> VALID_BLOOD_GROUPS = Set.of(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    @Override
    public boolean isValid(String bloodGroup, ConstraintValidatorContext context) {
        if (bloodGroup == null || bloodGroup.trim().isEmpty()) {
            return true;
        }
        return VALID_BLOOD_GROUPS.contains(bloodGroup.trim().toUpperCase());
    }
}
