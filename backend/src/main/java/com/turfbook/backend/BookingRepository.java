package com.turfbook.backend;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends MongoRepository<Booking, String> {

    List<Booking> findByTurfIdAndBookingDateAndStatus(String turfId, LocalDate bookingDate, String status);

    List<Booking> findByTurfIdAndBookingDateAndSlotLockedTrue(String turfId, LocalDate bookingDate);

    Optional<Booking> findByBookingReference(String bookingReference);

    Optional<Booking> findByRazorpayOrderId(String razorpayOrderId);

    boolean existsBySlotKeysInAndStatus(Collection<String> slotKeys, String status);

    boolean existsBySlotKeysInAndSlotLockedTrue(Collection<String> slotKeys);

    List<Booking> findByCustomerIdOrderByBookingDateDescStartTimeDesc(String customerId);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Booking> findBySlotLockedTrueAndStatusAndExpiresAtBefore(String status, Instant instant);

    List<Booking> findByStatusAndExpiresAtBefore(String status, Instant instant);
}
