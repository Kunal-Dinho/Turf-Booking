package com.turfbook.backend;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public class BookingResponse {

    private String id;
    private String bookingReference;
    private String turfId;
    private String turfName;
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
    private String status;
    private Instant createdAt;

    public BookingResponse() {
    }

    public static BookingResponse fromEntity(Booking booking) {
        BookingResponse res = new BookingResponse();
        res.setId(booking.getId());
        res.setBookingReference(booking.getBookingReference());
        res.setTurfId(booking.getTurfId());
        res.setTurfName(booking.getTurfName());
        res.setCustomerName(booking.getCustomerName());
        res.setCustomerEmail(booking.getCustomerEmail());
        res.setCustomerPhone(booking.getCustomerPhone());
        res.setBookingDate(booking.getBookingDate());
        res.setStartTime(booking.getStartTime());
        res.setEndTime(booking.getEndTime());
        res.setDurationHours(booking.getDurationHours());
        res.setHourlyRate(booking.getHourlyRate());
        res.setTotalPrice(booking.getTotalPrice());
        res.setStatus(booking.getStatus());
        res.setCreatedAt(booking.getCreatedAt());
        return res;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
