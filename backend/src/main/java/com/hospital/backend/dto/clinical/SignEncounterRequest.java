package com.hospital.backend.dto.clinical;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignEncounterRequest {
    private SaveSoapNoteDraftRequest noteData;
    private Long expectedEncounterVersion;
}
