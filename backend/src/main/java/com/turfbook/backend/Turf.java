
package com.turfbook.backend;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "turfs")
public class Turf {

    @Id
    private String id;

    private String name;
    private String location;
    private String sport;
    private double pricePerHour;

    public Turf() {
    }

    public Turf(String name, String location, String sport,
                double pricePerHour) {
        this.name = name;
        this.location = location;
        this.sport = sport;
        this.pricePerHour = pricePerHour;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSport() {
        return sport;
    }

    public void setSport(String sport) {
        this.sport = sport;
    }

    public double getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(double pricePerHour) {
        this.pricePerHour = pricePerHour;
    }
}
