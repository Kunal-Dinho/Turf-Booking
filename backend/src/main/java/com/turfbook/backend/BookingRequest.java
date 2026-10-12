package com.turfbook.backend;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class BookingRequest {

    @NotBlank(message = "Turf ID is required")
    private String turfId;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Valid email is required")
    private String customerEmail;

    private String customerPhone;

    @NotNull(message = "Booking date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bookingDate;

    @NotNull(message = "Start time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @Min(value = 1, message = "Duration must be at least 1 hour")
    @Max(value = 6, message = "Duration cannot exceed 6 hours")
    private int durationHours;

    public BookingRequest() {
    }

    public BookingRequest(String turfId, String customerName, String customerEmail,
                          String customerPhone, LocalDate bookingDate,
                          LocalTime startTime, int durationHours) {
        this.turfId = turfId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.durationHours = durationHours;
    }

    public String getTurfId() {
        return turfId;
    }

    public void setTurfId(String turfId) {
        this.turfId = turfId;
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

    public int getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(int durationHours) {
        this.durationHours = durationHours;
    }
}
