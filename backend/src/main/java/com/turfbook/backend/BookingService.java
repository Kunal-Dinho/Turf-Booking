package com.turfbook.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final LocalTime OPERATING_OPEN = LocalTime.of(6, 0);
    private static final LocalTime OPERATING_CLOSE = LocalTime.of(23, 0);

    private final BookingRepository bookingRepository;
    private final TurfRepository turfRepository;
    private final MongoTemplate mongoTemplate;

    public BookingService(BookingRepository bookingRepository,
                          TurfRepository turfRepository,
                          MongoTemplate mongoTemplate) {
        this.bookingRepository = bookingRepository;
        this.turfRepository = turfRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Programmatically ensures database indexes exist in MongoDB Atlas.
     * The unique index on `slotKeys` guarantees double-booking prevention under concurrent requests.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            mongoTemplate.indexOps(Booking.class).ensureIndex(
                new Index().on("slotKeys", Sort.Direction.ASC).unique()
            );
            mongoTemplate.indexOps(Booking.class).ensureIndex(
                new Index().on("bookingReference", Sort.Direction.ASC).unique()
            );
            log.info("Booking unique indices initialized successfully.");
        } catch (Exception e) {
            log.warn("Notice: could not ensure MongoDB indices (or already exist): {}", e.getMessage());
        }
    }

    /**
     * Retrieves already booked time slots for a specific turf and date.
     */
    public AvailabilityResponse getAvailability(String turfId, LocalDate date) {
        if (!turfRepository.existsById(turfId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Turf not found with id: " + turfId);
        }

        List<Booking> confirmedBookings = bookingRepository
                .findByTurfIdAndBookingDateAndStatus(turfId, date, "CONFIRMED");

        Set<String> bookedSlotSet = new TreeSet<>();
        for (Booking b : confirmedBookings) {
            LocalTime cur = b.getStartTime();
            LocalTime end = b.getEndTime();
            while (cur.isBefore(end)) {
                bookedSlotSet.add(String.format("%02d:00", cur.getHour()));
                cur = cur.plusHours(1);
            }
        }

        return new AvailabilityResponse(
                turfId,
                date,
                new ArrayList<>(bookedSlotSet),
                "06:00",
                "23:00"
        );
    }

    /**
     * Creates a new booking after rigorous server-side validation and price computation.
     */
    public BookingResponse createBooking(BookingRequest request) {
        // 1. Fetch turf to verify existence and get authoritative price per hour
        Turf turf = turfRepository.findById(request.getTurfId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Turf not found with id: " + request.getTurfId()
                ));

        // 2. Validate booking date
        LocalDate today = LocalDate.now();
        if (request.getBookingDate().isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Booking date cannot be in the past"
            );
        }
        if (request.getBookingDate().isAfter(today.plusDays(60))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Bookings can only be scheduled up to 60 days in advance"
            );
        }

        // 3. Validate operating hours & minute alignment
        LocalTime startTime = request.getStartTime();
        if (startTime.getMinute() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Bookings must begin on the hour (e.g., 18:00)"
            );
        }

        if (startTime.isBefore(OPERATING_OPEN) || !startTime.isBefore(OPERATING_CLOSE)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Start time must be within operating hours (06:00 to 22:00)"
            );
        }

        int duration = request.getDurationHours();
        if (duration < 1 || duration > 6) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Duration must be between 1 and 6 hours"
            );
        }

        LocalTime endTime = startTime.plusHours(duration);
        if (endTime.isAfter(OPERATING_CLOSE) && !endTime.equals(LocalTime.MIDNIGHT)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Booking duration exceeds daily closing time (23:00)"
            );
        }

        // Reject slots in the past if booking for today
        if (request.getBookingDate().isEqual(today)) {
            LocalTime now = LocalTime.now();
            if (startTime.isBefore(now)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Selected time slot is already in the past"
                );
            }
        }

        // 4. Generate discrete slot keys for atomic collision detection
        List<String> slotKeys = new ArrayList<>();
        for (int i = 0; i < duration; i++) {
            LocalTime slot = startTime.plusHours(i);
            String key = turf.getId() + "_" + request.getBookingDate() + "_" + String.format("%02d:00", slot.getHour());
            slotKeys.add(key);
        }

        // 5. Pre-check overlap in database
        if (bookingRepository.existsBySlotKeysInAndStatus(slotKeys, "CONFIRMED")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "One or more of the selected time slots are already booked."
            );
        }

        // 6. Calculate server-side authoritative price (never trust client)
        double totalPrice = turf.getPricePerHour() * duration;

        // 7. Generate clean booking reference (e.g. TB-261012-A3F2)
        String datePart = request.getBookingDate().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String bookingReference = "TB-" + datePart + "-" + randomPart;

        // 8. Construct booking entity
        Booking booking = new Booking();
        booking.setBookingReference(bookingReference);
        booking.setTurfId(turf.getId());
        booking.setTurfName(turf.getName());
        booking.setCustomerName(request.getCustomerName().trim());
        booking.setCustomerEmail(request.getCustomerEmail().trim());
        booking.setCustomerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setDurationHours(duration);
        booking.setHourlyRate(turf.getPricePerHour());
        booking.setTotalPrice(totalPrice);
        booking.setStatus("CONFIRMED"); // Temporary guest checkout (Auth/Payment Phase will update)
        booking.setSlotKeys(slotKeys);
        booking.setCreatedAt(Instant.now());

        // 9. Persist with atomic concurrency protection
        try {
            Booking saved = bookingRepository.save(booking);
            return BookingResponse.fromEntity(saved);
        } catch (DuplicateKeyException dke) {
            log.warn("Concurrent booking collision detected for slotKeys: {}", slotKeys);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This time slot was just booked by another user. Please select a different time slot."
            );
        }
    }

    /**
     * Looks up an existing booking by booking reference.
     */
    public BookingResponse getBookingByReference(String reference) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with reference: " + reference
                ));
        return BookingResponse.fromEntity(booking);
    }
}
