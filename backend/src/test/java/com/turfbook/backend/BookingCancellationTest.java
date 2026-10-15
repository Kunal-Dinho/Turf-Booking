package com.turfbook.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCancellationTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TurfRepository turfRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private BookingService bookingService;

    private Turf sampleTurf;
    private Booking activeBooking;
    private UserPrincipal ownerPrincipal;
    private UserPrincipal otherPrincipal;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        sampleTurf = new Turf("Premier Arena", "Bangalore", "Football", 1000.0);
        sampleTurf.setId("turf-100");

        ownerPrincipal = new UserPrincipal("cust-alice-123", "alice@example.com", "CUSTOMER");
        otherPrincipal = new UserPrincipal("cust-bob-456", "bob@example.com", "CUSTOMER");
        adminPrincipal = new UserPrincipal("admin-charlie-789", "admin@turfbook.com", "ADMIN");

        activeBooking = new Booking();
        activeBooking.setId("bkg-001");
        activeBooking.setBookingReference("TB-261015-TEST");
        activeBooking.setTurfId("turf-100");
        activeBooking.setTurfName("Premier Arena");
        activeBooking.setCustomerId("cust-alice-123");
        activeBooking.setCustomerName("Alice Smith");
        activeBooking.setCustomerEmail("alice@example.com");
        activeBooking.setCustomerPhone("+91 9999988888");
        activeBooking.setBookingDate(LocalDate.now().plusDays(2));
        activeBooking.setStartTime(LocalTime.of(18, 0));
        activeBooking.setEndTime(LocalTime.of(19, 0));
        activeBooking.setDurationHours(1);
        activeBooking.setHourlyRate(1000.0);
        activeBooking.setTotalPrice(1000.0);
        activeBooking.setStatus("CONFIRMED");
        activeBooking.setSlotKeys(List.of("turf-100_" + LocalDate.now().plusDays(2) + "_18:00"));
        activeBooking.setCreatedAt(Instant.now());
    }

    @Test
    void testCancelBooking_Success_ByOwner() {
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        Booking cancelledBooking = new Booking();
        cancelledBooking.setId(activeBooking.getId());
        cancelledBooking.setBookingReference(activeBooking.getBookingReference());
        cancelledBooking.setTurfId(activeBooking.getTurfId());
        cancelledBooking.setTurfName(activeBooking.getTurfName());
        cancelledBooking.setCustomerId(activeBooking.getCustomerId());
        cancelledBooking.setStatus("CANCELLED");
        cancelledBooking.setCancelledAt(Instant.now());

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class)))
                .thenReturn(cancelledBooking);

        BookingResponse response = bookingService.cancelBooking("TB-261015-TEST", ownerPrincipal);

        assertNotNull(response);
        assertEquals("CANCELLED", response.getStatus());
        assertNotNull(response.getCancelledAt());
        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class));
    }

    @Test
    void testCancelBooking_Success_ByAdmin() {
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        Booking cancelledBooking = new Booking();
        cancelledBooking.setId(activeBooking.getId());
        cancelledBooking.setBookingReference(activeBooking.getBookingReference());
        cancelledBooking.setStatus("CANCELLED");
        cancelledBooking.setCancelledAt(Instant.now());

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class)))
                .thenReturn(cancelledBooking);

        BookingResponse response = bookingService.cancelBooking("TB-261015-TEST", adminPrincipal);

        assertNotNull(response);
        assertEquals("CANCELLED", response.getStatus());
    }

    @Test
    void testCancelBooking_Rejected_ByDifferentCustomer_Throws403() {
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.cancelBooking("TB-261015-TEST", otherPrincipal)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not authorized"));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class));
    }

    @Test
    void testCancelBooking_GuestBooking_RejectedForUnrelatedCustomer_Throws403() {
        Booking guestBooking = new Booking();
        guestBooking.setBookingReference("TB-261015-GUEST");
        guestBooking.setCustomerId(null); // Guest
        guestBooking.setBookingDate(LocalDate.now().plusDays(2));
        guestBooking.setStartTime(LocalTime.of(15, 0));
        guestBooking.setStatus("CONFIRMED");

        when(bookingRepository.findByBookingReference("TB-261015-GUEST"))
                .thenReturn(Optional.of(guestBooking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.cancelBooking("TB-261015-GUEST", ownerPrincipal)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Guest reservations cannot be cancelled"));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class));
    }

    @Test
    void testCancelBooking_AlreadyCancelled_Throws400() {
        activeBooking.setStatus("CANCELLED");
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.cancelBooking("TB-261015-TEST", ownerPrincipal)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already cancelled"));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class));
    }

    @Test
    void testCancelBooking_PastStartTime_Throws400() {
        activeBooking.setBookingDate(LocalDate.now().minusDays(1)); // yesterday
        activeBooking.setStartTime(LocalTime.of(10, 0));

        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.cancelBooking("TB-261015-TEST", ownerPrincipal)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("start time"));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class));
    }

    @Test
    void testCancelBooking_ConcurrentRaceCondition_Throws400() {
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        // findAndModify returns null when another thread has already transitioned status to CANCELLED
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class)))
                .thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.cancelBooking("TB-261015-TEST", ownerPrincipal)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already cancelled"));
    }

    @Test
    void testCancelBooking_NonExistentBooking_Throws404() {
        when(bookingRepository.findByBookingReference("TB-NONEXISTENT"))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.cancelBooking("TB-NONEXISTENT", ownerPrincipal)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testAvailability_SlotBecomesFree_AfterCancellation() {
        LocalDate date = LocalDate.now().plusDays(2);

        // Booking 1: 18:00 to 19:00 (CANCELLED)
        Booking cancelled = new Booking();
        cancelled.setStartTime(LocalTime.of(18, 0));
        cancelled.setEndTime(LocalTime.of(19, 0));
        cancelled.setStatus("CANCELLED");

        // Booking 2: 20:00 to 21:00 (CONFIRMED)
        Booking confirmed = new Booking();
        confirmed.setStartTime(LocalTime.of(20, 0));
        confirmed.setEndTime(LocalTime.of(21, 0));
        confirmed.setStatus("CONFIRMED");

        // getAvailability queries only CONFIRMED bookings
        when(turfRepository.existsById("turf-100")).thenReturn(true);
        when(bookingRepository.findByTurfIdAndBookingDateAndStatus("turf-100", date, "CONFIRMED"))
                .thenReturn(List.of(confirmed));

        AvailabilityResponse availability = bookingService.getAvailability("turf-100", date);

        assertNotNull(availability);
        // Only 20:00 should be booked; 18:00 was released by cancellation
        assertEquals(List.of("20:00"), availability.getBookedSlots());
        assertFalse(availability.getBookedSlots().contains("18:00"), "Cancelled slot 18:00 must be available");
    }

    @Test
    void testGetBookingByReference_RedactsPII_ForUnauthorizedUser() {
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        // When requester is another customer
        BookingResponse response = bookingService.getBookingByReference("TB-261015-TEST", "cust-bob-456", false);

        assertNotNull(response);
        assertEquals("TB-261015-TEST", response.getBookingReference());
        assertEquals("Premier Arena", response.getTurfName());
        assertNull(response.getCustomerId(), "CustomerId must be redacted for non-owner");
        assertTrue(response.getCustomerEmail().contains("***"), "Customer email must be masked");
        assertTrue(response.getCustomerPhone().contains("*"), "Customer phone must be masked");
    }

    @Test
    void testGetBookingByReference_PreservesFullPII_ForOwner() {
        when(bookingRepository.findByBookingReference("TB-261015-TEST"))
                .thenReturn(Optional.of(activeBooking));

        // When requester is the actual owner
        BookingResponse response = bookingService.getBookingByReference("TB-261015-TEST", "cust-alice-123", false);

        assertNotNull(response);
        assertEquals("cust-alice-123", response.getCustomerId());
        assertEquals("alice@example.com", response.getCustomerEmail());
        assertEquals("+91 9999988888", response.getCustomerPhone());
    }
}
