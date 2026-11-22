package com.hospital.backend.dto.clinical;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmendEncounterRequest {

    @NotBlank(message = "Amendment reason is required")
    private String amendmentReason;

    private SaveSoapNoteDraftRequest noteData;
    private Long expectedEncounterVersion;
}
