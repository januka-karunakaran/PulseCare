package com.hospital.backend.service;

import com.hospital.backend.exception.BedAlreadyOccupiedException;
import com.hospital.backend.exception.InvalidBookingTransitionException;
import com.hospital.backend.exception.ResourceNotFoundException;
import com.hospital.backend.model.Bed;
import com.hospital.backend.model.BedBooking;
import com.hospital.backend.repository.BedBookingRepository;
import com.hospital.backend.repository.BedRepository;
import com.hospital.backend.service.impl.BedServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BedServiceTest {

    @Mock
    private BedRepository bedRepository;

    @Mock
    private BedBookingRepository bedBookingRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private BedServiceImpl bedService;

    @Test
    public void bookBed_WhenBedAvailable_ClaimsBedAndCreatesBooking() {
        String bedId = "bed-101";
        Bed availableBed = Bed.builder()
                .id(bedId)
                .bedNumber("B101")
                .status("AVAILABLE")
                .type("GENERAL")
                .ward("Ward A")
                .build();

        Bed claimedBed = Bed.builder()
                .id(bedId)
                .bedNumber("B101")
                .status("OCCUPIED")
                .type("GENERAL")
                .ward("Ward A")
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Bed.class))).thenReturn(claimedBed);

        BedBooking request = BedBooking.builder()
                .bedId(bedId)
                .patientId("patient-1")
                .doctorId("doctor-1")
                .notes("Admitted for surgery")
                .build();

        when(bedBookingRepository.save(any(BedBooking.class))).thenAnswer(inv -> {
            BedBooking b = inv.getArgument(0);
            b.setId("booking-999");
            return b;
        });

        BedBooking result = bedService.bookBed(request);

        assertNotNull(result);
        assertEquals("booking-999", result.getId());
        assertEquals("ACTIVE", result.getStatus());
        assertNotNull(result.getAdmissionDate());

        verify(mongoTemplate).updateFirst(any(), any(), eq(Bed.class));
    }

    @Test
    public void bookBed_WhenBedAlreadyOccupied_ThrowsBedAlreadyOccupiedException() {
        String bedId = "bed-102";
        Bed occupiedBed = Bed.builder()
                .id(bedId)
                .bedNumber("B102")
                .status("OCCUPIED")
                .build();

        // findAndModify returns null when condition (status == AVAILABLE) is not met
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Bed.class))).thenReturn(null);
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(occupiedBed));

        BedBooking request = BedBooking.builder()
                .bedId(bedId)
                .patientId("patient-2")
                .doctorId("doctor-2")
                .build();

        BedAlreadyOccupiedException ex = assertThrows(
                BedAlreadyOccupiedException.class,
                () -> bedService.bookBed(request)
        );

        assertEquals("BED_ALREADY_OCCUPIED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("already been allocated"));
    }

    @Test
    public void dischargePatient_WhenBookingActive_DischargesAndReleasesBed() {
        String bookingId = "booking-555";
        BedBooking dischargedBooking = BedBooking.builder()
                .id(bookingId)
                .bedId("bed-555")
                .patientId("patient-5")
                .status("DISCHARGED")
                .admissionDate("2026-05-01")
                .dischargeDate("2026-05-05")
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(BedBooking.class)))
                .thenReturn(dischargedBooking);

        BedBooking result = bedService.dischargePatient(bookingId);

        assertNotNull(result);
        assertEquals("DISCHARGED", result.getStatus());
        verify(mongoTemplate).updateFirst(any(), any(), eq(Bed.class));
    }

    @Test
    public void dischargePatient_WhenBookingAlreadyDischarged_ThrowsInvalidBookingTransition() {
        String bookingId = "booking-already-discharged";
        BedBooking existing = BedBooking.builder()
                .id(bookingId)
                .bedId("bed-666")
                .status("DISCHARGED")
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(BedBooking.class))).thenReturn(null);
        when(bedBookingRepository.findById(bookingId)).thenReturn(Optional.of(existing));

        InvalidBookingTransitionException ex = assertThrows(
                InvalidBookingTransitionException.class,
                () -> bedService.dischargePatient(bookingId)
        );

        assertEquals("INVALID_BOOKING_TRANSITION", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("already been discharged"));
    }

    @Test
    public void updateBed_WhenBedOccupiedWithActiveBooking_PreventsManualOverride() {
        String bedId = "bed-override";
        Bed occupiedBed = Bed.builder()
                .id(bedId)
                .status("OCCUPIED")
                .build();

        when(bedRepository.findById(bedId)).thenReturn(Optional.of(occupiedBed));
        when(bedBookingRepository.findAll()).thenReturn(List.of(
                BedBooking.builder().bedId(bedId).status("ACTIVE").build()
        ));

        Bed updateRequest = Bed.builder()
                .id(bedId)
                .status("AVAILABLE")
                .build();

        assertThrows(
                InvalidBookingTransitionException.class,
                () -> bedService.updateBed(bedId, updateRequest)
        );
    }

    @Test
    public void deleteBed_WhenBedOccupied_ThrowsException() {
        String bedId = "bed-delete-test";
        Bed occupiedBed = Bed.builder().id(bedId).status("OCCUPIED").build();
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(occupiedBed));

        assertThrows(
                InvalidBookingTransitionException.class,
                () -> bedService.deleteBed(bedId)
        );
    }
}
