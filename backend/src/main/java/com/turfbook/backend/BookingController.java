package com.turfbook.backend;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Checks turf availability for a specific date.
     * GET /api/bookings/availability?turfId=...&date=2026-10-15
     */
    @GetMapping("/availability")
    public AvailabilityResponse getAvailability(
            @RequestParam String turfId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.getAvailability(turfId, date);
    }

    /**
     * Creates a new booking reservation.
     * Accessible to guests (unauthenticated) and customers (authenticated).
     * If authenticated, securely links customer ID from security context (never from request body).
     * POST /api/bookings
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request,
                                         Authentication authentication) {
        String customerId = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            customerId = principal.getId();
        }
        return bookingService.createBooking(request, customerId);
    }

    /**
     * Protected endpoint returning all bookings belonging strictly to the logged-in customer.
     * GET /api/bookings/my
     */
    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return bookingService.getMyBookings(principal.getId());
    }

    /**
     * Retrieves a booking by its reference number.
     * Accessible to guests and customers, but redacts private contact details for unauthorized callers.
     * GET /api/bookings/{reference}
     */
    @GetMapping("/{reference}")
    public BookingResponse getBookingByReference(@PathVariable String reference, Authentication authentication) {
        String requesterId = null;
        boolean isAdmin = false;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            requesterId = principal.getId();
            isAdmin = "ADMIN".equalsIgnoreCase(principal.getRole());
        }
        return bookingService.getBookingByReference(reference, requesterId, isAdmin);
    }

    /**
     * Cancels an existing booking reservation.
     * Only the customer who owns the booking or an administrator may cancel it.
     * Never trusts customer ID supplied by frontend.
     * PATCH /api/bookings/{reference}/cancel
     */
    @PatchMapping("/{reference}/cancel")
    public BookingResponse cancelBooking(@PathVariable String reference, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to cancel a booking");
        }
        return bookingService.cancelBooking(reference, principal);
    }
}
