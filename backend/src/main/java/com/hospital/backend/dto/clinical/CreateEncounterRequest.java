package com.hospital.backend.dto.clinical;

import com.hospital.backend.enums.EncounterType;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEncounterRequest {

    @NotBlank(message = "appointmentId is required")
    private String appointmentId;

    private EncounterType encounterType;
    private String chiefComplaint;
    private String historyOfPresentIllness;
}
