package com.turfbook.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TurfRepository turfRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private BookingService bookingService;

    private Turf sampleTurf;

    @BeforeEach
    void setUp() {
        sampleTurf = new Turf("Blue Arena", "Mumbai", "Football", 1200.0);
        sampleTurf.setId("turf-123");
    }

    @Test
    void testCreateBooking_ValidInput_Success() {
        BookingRequest request = new BookingRequest(
                "turf-123",
                "Alex Hunter",
                "alex@example.com",
                "+91 9876543210",
                LocalDate.now().plusDays(2),
                LocalTime.of(18, 0),
                2
        );

        when(turfRepository.findById("turf-123")).thenReturn(Optional.of(sampleTurf));
        when(bookingRepository.existsBySlotKeysInAndStatus(anyCollection(), eq("CONFIRMED"))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId("booking-id-1");
            return b;
        });

        BookingResponse response = bookingService.createBooking(request);

        assertNotNull(response);
        assertEquals("turf-123", response.getTurfId());
        assertEquals("Blue Arena", response.getTurfName());
        assertEquals(2400.0, response.getTotalPrice(), "Total price must be 1200 * 2 = 2400.0");
        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertEquals("CREATED", response.getPaymentStatus());
        assertTrue(response.isSlotLocked());
        assertNotNull(response.getBookingReference());
        assertTrue(response.getBookingReference().startsWith("TB-"));
        assertEquals(LocalTime.of(18, 0), response.getStartTime());
        assertEquals(LocalTime.of(20, 0), response.getEndTime());
    }

    @Test
    void testCreateBooking_TurfNotFound_Throws404() {
        BookingRequest request = new BookingRequest(
                "non-existent-turf",
                "Alex Hunter",
                "alex@example.com",
                null,
                LocalDate.now().plusDays(1),
                LocalTime.of(10, 0),
                1
        );

        when(turfRepository.findById("non-existent-turf")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.createBooking(request)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testCreateBooking_PastDate_Throws400() {
        BookingRequest request = new BookingRequest(
                "turf-123",
                "Alex Hunter",
                "alex@example.com",
                null,
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                1
        );

        when(turfRepository.findById("turf-123")).thenReturn(Optional.of(sampleTurf));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.createBooking(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("past"));
    }

    @Test
    void testCreateBooking_InvalidMinute_Throws400() {
        BookingRequest request = new BookingRequest(
                "turf-123",
                "Alex Hunter",
                "alex@example.com",
                null,
                LocalDate.now().plusDays(1),
                LocalTime.of(10, 30),
                1
        );

        when(turfRepository.findById("turf-123")).thenReturn(Optional.of(sampleTurf));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.createBooking(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("hour"));
    }

    @Test
    void testCreateBooking_OverlappingBooking_Throws409() {
        BookingRequest request = new BookingRequest(
                "turf-123",
                "Alex Hunter",
                "alex@example.com",
                null,
                LocalDate.now().plusDays(1),
                LocalTime.of(18, 0),
                2
        );

        when(turfRepository.findById("turf-123")).thenReturn(Optional.of(sampleTurf));
        when(bookingRepository.existsBySlotKeysInAndStatus(anyCollection(), eq("CONFIRMED"))).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.createBooking(request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already booked"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testCreateBooking_ConcurrentRaceCondition_Throws409() {
        BookingRequest request = new BookingRequest(
                "turf-123",
                "Alex Hunter",
                "alex@example.com",
                null,
                LocalDate.now().plusDays(1),
                LocalTime.of(18, 0),
                1
        );

        when(turfRepository.findById("turf-123")).thenReturn(Optional.of(sampleTurf));
        when(bookingRepository.existsBySlotKeysInAndStatus(anyCollection(), eq("CONFIRMED"))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.createBooking(request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("just booked"));
    }

    @Test
    void testGetAvailability_ReturnsBookedSlots() {
        LocalDate date = LocalDate.now().plusDays(3);
        Booking existing = new Booking();
        existing.setStartTime(LocalTime.of(18, 0));
        existing.setEndTime(LocalTime.of(20, 0));
        existing.setStatus("CONFIRMED");

        when(turfRepository.existsById("turf-123")).thenReturn(true);
        when(bookingRepository.findByTurfIdAndBookingDateAndStatus("turf-123", date, "CONFIRMED"))
                .thenReturn(List.of(existing));

        AvailabilityResponse availability = bookingService.getAvailability("turf-123", date);

        assertNotNull(availability);
        assertEquals("turf-123", availability.getTurfId());
        assertEquals(date, availability.getDate());
        assertEquals(List.of("18:00", "19:00"), availability.getBookedSlots());
    }
}
