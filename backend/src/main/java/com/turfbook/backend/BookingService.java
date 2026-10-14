package com.turfbook.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * Uses a partial unique index on `slotKeys` filtering on status="CONFIRMED"
     * so that cancelled bookings automatically release their time slots at the storage level.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(Booking.class);
            List<IndexInfo> indexInfoList = indexOps.getIndexInfo();
            for (IndexInfo info : indexInfoList) {
                if ("slotKeys_1".equals(info.getName()) || "slotKeys".equals(info.getName())) {
                    String partialFilter = info.getPartialFilterExpression();
                    if (partialFilter == null || !partialFilter.contains("CONFIRMED")) {
                        log.info("Migrating legacy slotKeys index to partial unique index (status=CONFIRMED)...");
                        indexOps.dropIndex(info.getName());
                        break;
                    }
                }
            }

            indexOps.ensureIndex(
                new Index()
                    .on("slotKeys", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("status").is("CONFIRMED")))
            );
            indexOps.ensureIndex(
                new Index().on("bookingReference", Sort.Direction.ASC).unique()
            );
            indexOps.ensureIndex(
                new Index().on("customerId", Sort.Direction.ASC).on("bookingDate", Sort.Direction.DESC).on("startTime", Sort.Direction.DESC)
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
     * Creates a new booking reservation (guest or authenticated).
     */
    public BookingResponse createBooking(BookingRequest request) {
        return createBooking(request, null);
    }

    /**
     * Creates a new booking reservation with authenticated customer linkage.
     */
    public BookingResponse createBooking(BookingRequest request, String authenticatedCustomerId) {
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

        // Securely link customer ID if authenticated (never from untrusted request body)
        if (authenticatedCustomerId != null && !authenticatedCustomerId.isBlank()) {
            booking.setCustomerId(authenticatedCustomerId.trim());
        }

        booking.setCustomerName(request.getCustomerName().trim());
        booking.setCustomerEmail(request.getCustomerEmail().trim());
        booking.setCustomerPhone(request.getCustomerPhone() != null ? request.getCustomerPhone().trim() : null);
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setDurationHours(duration);
        booking.setHourlyRate(turf.getPricePerHour());
        booking.setTotalPrice(totalPrice);
        booking.setStatus("CONFIRMED");
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
     * Retrieves all bookings for the specified authenticated customer.
     */
    public List<BookingResponse> getMyBookings(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return bookingRepository.findByCustomerIdOrderByBookingDateDescStartTimeDesc(customerId)
                .stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    /**
     * Looks up an existing booking by booking reference (public fallback without user context).
     */
    public BookingResponse getBookingByReference(String reference) {
        return getBookingByReference(reference, null, false);
    }

    /**
     * Looks up an existing booking by booking reference.
     * Protects private booking details (customer email, phone, and customerId)
     * if the requester is neither the booking owner nor an administrator.
     */
    public BookingResponse getBookingByReference(String reference, String requesterId, boolean isAdmin) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with reference: " + reference
                ));

        BookingResponse response = BookingResponse.fromEntity(booking);

        boolean isOwner = requesterId != null && booking.getCustomerId() != null
                && requesterId.equals(booking.getCustomerId());

        if (!isOwner && !isAdmin) {
            response.setCustomerEmail(maskEmail(booking.getCustomerEmail()));
            response.setCustomerPhone(maskPhone(booking.getCustomerPhone()));
            response.setCustomerId(null);
        }

        return response;
    }

    /**
     * Cancels an existing booking subject to server-side authorization and cancellation rules.
     *
     * Rules:
     * 1. Only the customer who owns the booking or an administrator can cancel it.
     * 2. Guest bookings cannot be cancelled by unrelated authenticated customer accounts.
     * 3. A booking cannot be cancelled after its scheduled start time.
     * 4. A booking that is already cancelled cannot be cancelled again.
     * 5. Atomically transitions status to CANCELLED and sets cancelledAt, safely releasing
     *    the reserved slots at the MongoDB storage engine level via the partial unique index.
     */
    public BookingResponse cancelBooking(String reference, UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to cancel a booking");
        }

        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with reference: " + reference
                ));

        boolean isAdmin = principal.getRole() != null && principal.getRole().equalsIgnoreCase("ADMIN");

        // Guest booking check
        if (booking.getCustomerId() == null) {
            if (!isAdmin) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Guest reservations cannot be cancelled by customer accounts. Please contact turf management."
                );
            }
        } else if (!isAdmin && !principal.getId().equals(booking.getCustomerId())) {
            // Customer mismatch check
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to cancel this booking."
            );
        }

        // Check if already cancelled
        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking is already cancelled."
            );
        }

        // Check start time against current local date and time
        LocalDateTime bookingStartDateTime = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
        LocalDateTime now = LocalDateTime.now();
        if (!bookingStartDateTime.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A booking cannot be cancelled after its start time."
            );
        }

        // Perform atomic conditional update: only if currently CONFIRMED
        Query query = new Query(Criteria.where("bookingReference").is(reference).and("status").is("CONFIRMED"));
        Update update = new Update()
                .set("status", "CANCELLED")
                .set("cancelledAt", Instant.now());

        Booking updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Booking.class
        );

        if (updated == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking is already cancelled."
            );
        }

        log.info("Booking {} successfully cancelled by user {}", reference, principal.getId());
        return BookingResponse.fromEntity(updated);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        String namePart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        if (namePart.length() <= 2) {
            return namePart.charAt(0) + "***" + domainPart;
        }
        return namePart.charAt(0) + "***" + namePart.charAt(namePart.length() - 1) + domainPart;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        if (phone.length() <= 4) return "****";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
