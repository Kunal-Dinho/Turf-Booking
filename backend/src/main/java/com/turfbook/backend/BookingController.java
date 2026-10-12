package com.turfbook.backend;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
     * POST /api/bookings
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    /**
     * Retrieves a booking by its reference number.
     * GET /api/bookings/{reference}
     */
    @GetMapping("/{reference}")
    public BookingResponse getBookingByReference(@PathVariable String reference) {
        return bookingService.getBookingByReference(reference);
    }
}
