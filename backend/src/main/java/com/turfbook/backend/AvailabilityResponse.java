package com.turfbook.backend;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public class AvailabilityResponse {

    private String turfId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private List<String> bookedSlots;
    private String operatingStart;
    private String operatingEnd;

    public AvailabilityResponse() {
    }

    public AvailabilityResponse(String turfId, LocalDate date, List<String> bookedSlots,
                                String operatingStart, String operatingEnd) {
        this.turfId = turfId;
        this.date = date;
        this.bookedSlots = bookedSlots;
        this.operatingStart = operatingStart;
        this.operatingEnd = operatingEnd;
    }

    public String getTurfId() {
        return turfId;
    }

    public void setTurfId(String turfId) {
        this.turfId = turfId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<String> getBookedSlots() {
        return bookedSlots;
    }

    public void setBookedSlots(List<String> bookedSlots) {
        this.bookedSlots = bookedSlots;
    }

    public String getOperatingStart() {
        return operatingStart;
    }

    public void setOperatingStart(String operatingStart) {
        this.operatingStart = operatingStart;
    }

    public String getOperatingEnd() {
        return operatingEnd;
    }

    public void setOperatingEnd(String operatingEnd) {
        this.operatingEnd = operatingEnd;
    }
}
