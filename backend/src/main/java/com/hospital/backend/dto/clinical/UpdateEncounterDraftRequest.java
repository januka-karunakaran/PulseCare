package com.hospital.backend.dto.clinical;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEncounterDraftRequest {
    private String chiefComplaint;
    private String historyOfPresentIllness;
    private String clinicalSummary;
    private Long expectedVersion;
}
