package com.turfbook.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
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
    private final RazorpayService razorpayService;
    private final ObjectMapper objectMapper;

    public BookingService(BookingRepository bookingRepository,
                          TurfRepository turfRepository,
                          MongoTemplate mongoTemplate) {
        this(bookingRepository, turfRepository, mongoTemplate, null, new ObjectMapper());
    }

    @Autowired
    public BookingService(BookingRepository bookingRepository,
                          TurfRepository turfRepository,
                          MongoTemplate mongoTemplate,
                          RazorpayService razorpayService,
                          ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.turfRepository = turfRepository;
        this.mongoTemplate = mongoTemplate;
        this.razorpayService = razorpayService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Programmatically ensures database indexes exist in MongoDB Atlas.
     * Uses a partial unique index on `slotKeys` filtering on slotLocked=true
     * so that pending reservations and confirmed bookings prevent double booking,
     * while cancelled or expired bookings automatically release their time slots.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            // 1. Data Backfill: Ensure existing documents have the slotLocked field
            mongoTemplate.updateMulti(
                    new Query(Criteria.where("status").is("CONFIRMED").and("slotLocked").exists(false)),
                    new Update().set("slotLocked", true),
                    Booking.class
            );
            mongoTemplate.updateMulti(
                    new Query(Criteria.where("status").ne("CONFIRMED").and("slotLocked").exists(false)),
                    new Update().set("slotLocked", false),
                    Booking.class
            );

            IndexOperations indexOps = mongoTemplate.indexOps(Booking.class);
            List<IndexInfo> indexInfoList = indexOps.getIndexInfo();

            // 2. Safe index migration: if legacy index on status=CONFIRMED exists, drop it
            for (IndexInfo info : indexInfoList) {
                if ("slotKeys_1".equals(info.getName()) || "slotKeys".equals(info.getName()) || info.getName().startsWith("slotKeys")) {
                    String partialFilter = info.getPartialFilterExpression();
                    if (partialFilter == null || !partialFilter.contains("slotLocked")) {
                        log.info("Migrating legacy slotKeys index '{}' to partial unique index (slotLocked=true)...", info.getName());
                        try {
                            indexOps.dropIndex(info.getName());
                        } catch (Exception dropEx) {
                            log.warn("Could not drop legacy index '{}': {}", info.getName(), dropEx.getMessage());
                        }
                    }
                }
            }

            // 3. Ensure the new partial unique index on slotLocked=true
            indexOps.ensureIndex(
                new Index()
                    .on("slotKeys", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("slotLocked").is(true)))
                    .named("slotKeys_slotLocked_unique")
            );

            indexOps.ensureIndex(
                new Index().on("bookingReference", Sort.Direction.ASC).unique()
            );
            indexOps.ensureIndex(
                new Index().on("razorpayOrderId", Sort.Direction.ASC)
            );
            indexOps.ensureIndex(
                new Index().on("customerId", Sort.Direction.ASC).on("bookingDate", Sort.Direction.DESC).on("startTime", Sort.Direction.DESC)
            );
            log.info("Booking unique indices initialized successfully with slotLocked partial filter.");
        } catch (Exception e) {
            log.warn("Notice: could not ensure MongoDB indices (or already exist): {}", e.getMessage());
        }
    }

    /**
     * Retrieves already booked time slots for a specific turf and date.
     * Excludes expired pending reservations.
     */
    public AvailabilityResponse getAvailability(String turfId, LocalDate date) {
        if (!turfRepository.existsById(turfId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Turf not found with id: " + turfId);
        }

        // Clean expired reservations lazily
        cleanExpiredReservationsInternal();

        List<Booking> activeBookings = bookingRepository
                .findByTurfIdAndBookingDateAndSlotLockedTrue(turfId, date);
        if (activeBookings == null || activeBookings.isEmpty()) {
            activeBookings = bookingRepository.findByTurfIdAndBookingDateAndStatus(turfId, date, "CONFIRMED");
        }
        if (activeBookings == null) {
            activeBookings = Collections.emptyList();
        }

        Set<String> bookedSlotSet = new TreeSet<>();
        for (Booking b : activeBookings) {
            // Filter out any booking that may have expired in the current millisecond
            if ("PENDING_PAYMENT".equalsIgnoreCase(b.getStatus()) && b.getExpiresAt() != null && b.getExpiresAt().isBefore(Instant.now())) {
                continue;
            }
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
     * Creates a new booking reservation with authenticated customer linkage and Razorpay order.
     * Transitions slot to PENDING_PAYMENT with an active slotLock for 10 minutes.
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

        // Clean expired bookings before pre-check
        cleanExpiredReservationsInternal();

        // 5. Pre-check overlap in database
        boolean slotTaken = bookingRepository.existsBySlotKeysInAndSlotLockedTrue(slotKeys);
        if (!slotTaken) {
            slotTaken = bookingRepository.existsBySlotKeysInAndStatus(slotKeys, "CONFIRMED");
        }
        if (slotTaken) {
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

        // 8. Create Razorpay Order
        String orderId;
        int windowMinutes = 10;
        if (razorpayService != null) {
            RazorpayService.RazorpayOrderResult order = razorpayService.createOrder(bookingReference, totalPrice);
            orderId = order != null ? order.orderId() : "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            windowMinutes = razorpayService.getPaymentWindowMinutes();
        } else {
            orderId = "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }

        // 9. Construct booking entity
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

        // Payment lifecycle initialization: slot is locked during payment window
        booking.setStatus("PENDING_PAYMENT");
        booking.setPaymentStatus("CREATED");
        booking.setSlotLocked(true);
        booking.setSlotKeys(slotKeys);
        booking.setRazorpayOrderId(orderId);
        booking.setExpiresAt(Instant.now().plus(Duration.ofMinutes(windowMinutes)));
        booking.setCreatedAt(Instant.now());

        // 10. Persist with atomic concurrency protection
        try {
            Booking saved = bookingRepository.save(booking);
            return enrichResponse(BookingResponse.fromEntity(saved));
        } catch (DuplicateKeyException dke) {
            log.warn("Concurrent booking collision detected for slotKeys: {}", slotKeys);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This time slot was just booked by another user. Please select a different time slot."
            );
        }
    }

    /**
     * Verifies payment signature and confirms booking.
     * Enforces that client callbacks alone cannot confirm a booking without valid HMAC signature.
     */
    public BookingResponse verifyPayment(PaymentVerificationRequest request, UserPrincipal principal) {
        Booking booking = bookingRepository.findByBookingReference(request.getBookingReference())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with reference: " + request.getBookingReference()
                ));

        // Order association verification
        if (booking.getRazorpayOrderId() == null || !booking.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Razorpay order ID does not match booking reservation"
            );
        }

        // Customer ownership verification (for authenticated bookings)
        if (booking.getCustomerId() != null && principal != null && !"ADMIN".equalsIgnoreCase(principal.getRole())) {
            if (!principal.getId().equals(booking.getCustomerId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You are not authorized to verify this booking payment"
                );
            }
        }

        // Idempotency: if already confirmed with matching payment ID, return existing confirmed booking
        if ("CONFIRMED".equalsIgnoreCase(booking.getStatus()) && "SUCCESS".equalsIgnoreCase(booking.getPaymentStatus())) {
            if (request.getRazorpayPaymentId().equals(booking.getRazorpayPaymentId())) {
                log.info("Idempotent payment verification received for confirmed booking {}", booking.getBookingReference());
                return enrichResponse(BookingResponse.fromEntity(booking));
            }
        }

        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Booking has already been cancelled"
            );
        }

        // Check if reservation expired
        Instant now = Instant.now();
        if ("EXPIRED".equalsIgnoreCase(booking.getStatus()) ||
                (booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(now) && !"CONFIRMED".equalsIgnoreCase(booking.getStatus()))) {
            // Attempt automatic refund if payment was already made
            try {
                razorpayService.createRefund(request.getRazorpayPaymentId(), booking.getTotalPrice());
            } catch (Exception refEx) {
                log.warn("Could not issue immediate refund for expired booking: {}", refEx.getMessage());
            }
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Reservation expired before payment could be confirmed. Slot has been released and any deduction will be refunded."
            );
        }

        // Verify Razorpay HMAC signature
        boolean validSignature = razorpayService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (!validSignature) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid payment signature"
            );
        }

        // Perform atomic transition from PENDING_PAYMENT to CONFIRMED
        Query query = new Query(Criteria.where("bookingReference").is(request.getBookingReference())
                .and("status").is("PENDING_PAYMENT"));
        Update update = new Update()
                .set("status", "CONFIRMED")
                .set("paymentStatus", "SUCCESS")
                .set("razorpayPaymentId", request.getRazorpayPaymentId())
                .set("razorpaySignature", request.getRazorpaySignature())
                .set("confirmedAt", now)
                .set("slotLocked", true);

        Booking updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Booking.class
        );

        if (updated == null) {
            Booking latest = bookingRepository.findByBookingReference(request.getBookingReference())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
            if ("CONFIRMED".equalsIgnoreCase(latest.getStatus())) {
                return enrichResponse(BookingResponse.fromEntity(latest));
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Could not confirm booking. Reservation was already finalized or expired."
            );
        }

        log.info("Booking {} successfully verified and confirmed. Payment ID: {}", updated.getBookingReference(), request.getRazorpayPaymentId());
        return enrichResponse(BookingResponse.fromEntity(updated));
    }

    /**
     * Webhook handler for asynchronous Razorpay events.
     * Uses raw-body signature verification with webhook secret.
     */
    public void handleWebhook(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || !razorpayService.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Rejected Razorpay webhook: invalid signature");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText("");
            log.info("Processing verified Razorpay webhook event: {}", event);

            if ("payment.captured".equals(event) || "order.paid".equals(event)) {
                JsonNode paymentNode = root.path("payload").path("payment").path("entity");
                String orderId = paymentNode.path("order_id").asText();
                String paymentId = paymentNode.path("id").asText();
                long amount = paymentNode.path("amount").asLong();

                if (orderId.isBlank()) {
                    orderId = root.path("payload").path("order").path("entity").path("id").asText();
                }

                Optional<Booking> opt = bookingRepository.findByRazorpayOrderId(orderId);
                if (opt.isPresent()) {
                    Booking b = opt.get();
                    if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                        log.info("Webhook duplicate ignored: booking {} is already CONFIRMED", b.getBookingReference());
                        return;
                    }

                    // Handle late payment after expiration: release slot & initiate refund
                    if (b.getExpiresAt() != null && b.getExpiresAt().isBefore(Instant.now())) {
                        log.warn("Late payment received for expired booking {}. Initiating refund...", b.getBookingReference());
                        razorpayService.createRefund(paymentId, b.getTotalPrice());
                        b.setPaymentStatus("REFUND_INITIATED");
                        bookingRepository.save(b);
                        return;
                    }

                    // Verify expected amount in paise
                    long expectedPaise = Math.round(b.getTotalPrice() * 100);
                    if (amount > 0 && amount != expectedPaise) {
                        log.error("Payment amount mismatch for order {}: expected {} paise, received {} paise", orderId, expectedPaise, amount);
                        return;
                    }

                    b.setStatus("CONFIRMED");
                    b.setPaymentStatus("SUCCESS");
                    b.setRazorpayPaymentId(paymentId);
                    b.setConfirmedAt(Instant.now());
                    b.setSlotLocked(true);
                    bookingRepository.save(b);
                    log.info("Booking {} successfully confirmed via webhook event {}", b.getBookingReference(), event);
                }
            } else if ("payment.failed".equals(event)) {
                JsonNode paymentNode = root.path("payload").path("payment").path("entity");
                String orderId = paymentNode.path("order_id").asText();
                Optional<Booking> opt = bookingRepository.findByRazorpayOrderId(orderId);
                if (opt.isPresent()) {
                    Booking b = opt.get();
                    if ("PENDING_PAYMENT".equalsIgnoreCase(b.getStatus())) {
                        b.setStatus("FAILED");
                        b.setPaymentStatus("FAILED");
                        b.setSlotLocked(false);
                        bookingRepository.save(b);
                        log.info("Booking {} marked FAILED via webhook payment.failed", b.getBookingReference());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error processing webhook payload");
        }
    }

    /**
     * Dismisses or cancels a pending payment attempt, releasing reserved slots immediately.
     */
    public void dismissOrCancelPendingPayment(String reference, UserPrincipal principal) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with reference: " + reference
                ));

        if (booking.getCustomerId() != null && principal != null && !"ADMIN".equalsIgnoreCase(principal.getRole())) {
            if (!principal.getId().equals(booking.getCustomerId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not authorized to dismiss this reservation"
                );
            }
        }

        if ("PENDING_PAYMENT".equalsIgnoreCase(booking.getStatus())) {
            booking.setStatus("FAILED");
            booking.setPaymentStatus("FAILED");
            booking.setSlotLocked(false);
            bookingRepository.save(booking);
            log.info("Booking {} pending payment dismissed and slot released", reference);
        }
    }

    /**
     * Periodic background task running every 30 seconds to release slots of expired reservations.
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanExpiredReservations() {
        cleanExpiredReservationsInternal();
    }

    private void cleanExpiredReservationsInternal() {
        try {
            Instant now = Instant.now();
            List<Booking> expiredList = bookingRepository.findBySlotLockedTrueAndStatusAndExpiresAtBefore("PENDING_PAYMENT", now);
            if (!expiredList.isEmpty()) {
                log.info("Releasing {} expired pending reservations at {}", expiredList.size(), now);
                for (Booking b : expiredList) {
                    b.setStatus("EXPIRED");
                    b.setPaymentStatus("FAILED");
                    b.setSlotLocked(false);
                    bookingRepository.save(b);
                }
            }
        } catch (Exception e) {
            log.warn("Notice: could not run expired reservation cleaner: {}", e.getMessage());
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
                .map(this::enrichResponse)
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

        BookingResponse response = enrichResponse(BookingResponse.fromEntity(booking));

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
     * If the booking was paid, automatically initiates a refund via Razorpay.
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

        // Initiate refund if payment was captured
        String refundId = null;
        String refundStatus = null;
        if ("SUCCESS".equalsIgnoreCase(booking.getPaymentStatus()) && booking.getRazorpayPaymentId() != null) {
            try {
                refundId = razorpayService.createRefund(booking.getRazorpayPaymentId(), booking.getTotalPrice());
                refundStatus = refundId != null ? "REFUND_INITIATED" : "REFUND_PENDING_MANUAL";
            } catch (Exception e) {
                log.warn("Refund creation failed for booking {}: {}", reference, e.getMessage());
                refundStatus = "REFUND_PENDING_MANUAL";
            }
        }

        // Perform atomic conditional update: only if currently CONFIRMED
        Query query = new Query(Criteria.where("bookingReference").is(reference).and("status").is("CONFIRMED"));
        Update update = new Update()
                .set("status", "CANCELLED")
                .set("slotLocked", false)
                .set("cancelledAt", Instant.now());

        if (refundId != null) {
            update.set("refundId", refundId);
        }
        if (refundStatus != null) {
            update.set("refundStatus", refundStatus);
        }

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
        return enrichResponse(BookingResponse.fromEntity(updated));
    }

    private BookingResponse enrichResponse(BookingResponse res) {
        if (res != null && razorpayService != null) {
            res.setRazorpayKeyId(razorpayService.getKeyId());
        }
        return res;
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
