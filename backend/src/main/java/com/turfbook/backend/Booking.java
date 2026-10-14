package com.turfbook.backend;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document representing a turf reservation.
 *
 * Concurrency & Double-Booking Strategy:
 * The `slotKeys` list stores composite keys representing each 1-hour booked block
 * (e.g., "turfId_2026-10-15_18:00"). A unique index on `slotKeys` guarantees at the
 * storage engine level that overlapping slots cannot be booked concurrently.
 */
@Document(collection = "bookings")
public class Booking {

    @Id
    private String id;

    @Indexed(unique = true)
    private String bookingReference;

    @Indexed
    private String turfId;
    private String turfName;

    // Linked customer ID (null for guest checkout, populated for authenticated customers)
    @Indexed
    private String customerId;

    private String customerName;
    private String customerEmail;
    private String customerPhone;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bookingDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private int durationHours;
    private double hourlyRate;
    private double totalPrice;

    // Status: CONFIRMED, CANCELLED (Payment Phase: will include PENDING_PAYMENT)
    private String status;

    /**
     * Unique slot keys for each discrete hour reserved.
     * Format: "<turfId>_<bookingDate>_<hourSlot>"
     */
    private List<String> slotKeys = new ArrayList<>();

    private Instant createdAt;
    private Instant cancelledAt;

    public Booking() {
    }

    public Booking(String bookingReference, String turfId, String turfName,
                   String customerName, String customerEmail, String customerPhone,
                   LocalDate bookingDate, LocalTime startTime, LocalTime endTime,
                   int durationHours, double hourlyRate, double totalPrice,
                   String status, List<String> slotKeys, Instant createdAt) {
        this.bookingReference = bookingReference;
        this.turfId = turfId;
        this.turfName = turfName;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationHours = durationHours;
        this.hourlyRate = hourlyRate;
        this.totalPrice = totalPrice;
        this.status = status;
        this.slotKeys = slotKeys;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getTurfId() {
        return turfId;
    }

    public void setTurfId(String turfId) {
        this.turfId = turfId;
    }

    public String getTurfName() {
        return turfName;
    }

    public void setTurfName(String turfName) {
        this.turfName = turfName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }

    public double getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getSlotKeys() {
        return slotKeys;
    }

    public void setSlotKeys(List<String> slotKeys) {
        this.slotKeys = slotKeys;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
