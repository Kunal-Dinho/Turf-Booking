package com.turfbook.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingOwnershipTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TurfRepository turfRepository;

    @InjectMocks
    private BookingService bookingService;

    private Turf sampleTurf;

    @BeforeEach
    void setUp() {
        sampleTurf = new Turf("Green Field", "Bangalore", "Football", 1500.0);
        sampleTurf.setId("turf-999");
    }

    @Test
    void testCreateBooking_AuthenticatedUser_LinksCustomerId() {
        BookingRequest request = new BookingRequest(
                "turf-999",
                "Alice Smith",
                "alice@example.com",
                "+91 9999988888",
                LocalDate.now().plusDays(2),
                LocalTime.of(19, 0),
                1
        );

        when(turfRepository.findById("turf-999")).thenReturn(Optional.of(sampleTurf));
        when(bookingRepository.existsBySlotKeysInAndStatus(anyCollection(), eq("CONFIRMED"))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(request, "cust-alice-456");

        assertNotNull(response);
        assertEquals("cust-alice-456", response.getCustomerId(), "Authenticated customer ID must be linked");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertEquals("cust-alice-456", captor.getValue().getCustomerId());
    }

    @Test
    void testCreateBooking_GuestUser_CustomerIdIsNull() {
        BookingRequest request = new BookingRequest(
                "turf-999",
                "Bob Guest",
                "bob@example.com",
                null,
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0),
                1
        );

        when(turfRepository.findById("turf-999")).thenReturn(Optional.of(sampleTurf));
        when(bookingRepository.existsBySlotKeysInAndStatus(anyCollection(), eq("CONFIRMED"))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(request, null);

        assertNotNull(response);
        assertNull(response.getCustomerId(), "Guest bookings must have null customerId");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertNull(captor.getValue().getCustomerId());
    }

    @Test
    void testGetMyBookings_ReturnsOnlyCustomerBookings() {
        Booking b1 = new Booking();
        b1.setId("b-1");
        b1.setBookingReference("TB-261015-001");
        b1.setCustomerId("cust-alice-456");
        b1.setTurfName("Green Field");
        b1.setBookingDate(LocalDate.now().plusDays(2));
        b1.setStartTime(LocalTime.of(18, 0));
        b1.setEndTime(LocalTime.of(19, 0));
        b1.setTotalPrice(1500.0);
        b1.setStatus("CONFIRMED");

        when(bookingRepository.findByCustomerIdOrderByBookingDateDescStartTimeDesc("cust-alice-456"))
                .thenReturn(List.of(b1));

        List<BookingResponse> bookings = bookingService.getMyBookings("cust-alice-456");

        assertEquals(1, bookings.size());
        assertEquals("TB-261015-001", bookings.get(0).getBookingReference());
        assertEquals("cust-alice-456", bookings.get(0).getCustomerId());
    }

    @Test
    void testGetMyBookings_NullCustomerId_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.getMyBookings(null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
