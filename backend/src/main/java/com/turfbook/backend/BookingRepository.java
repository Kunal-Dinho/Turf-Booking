package com.turfbook.backend;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends MongoRepository<Booking, String> {

    List<Booking> findByTurfIdAndBookingDateAndStatus(String turfId, LocalDate bookingDate, String status);

    Optional<Booking> findByBookingReference(String bookingReference);

    boolean existsBySlotKeysInAndStatus(Collection<String> slotKeys, String status);
}
