
package com.turfbook.backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/turfs")
public class TurfController {

    private final TurfRepository turfRepository;

    public TurfController(TurfRepository turfRepository) {
        this.turfRepository = turfRepository;
    }

    // GET: Fetch all turfs
    @GetMapping
    public List<Turf> getAllTurfs() {
        return turfRepository.findAll();
    }

    // POST: Add a new turf
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Turf createTurf(@Valid @RequestBody Turf turf) {
        if (turf.getName() == null || turf.getName().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Turf name is required"
            );
        }

        if (turf.getLocation() == null || turf.getLocation().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Turf location is required"
            );
        }

        if (turf.getPricePerHour() <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Price must be greater than zero"
            );
        }

        turf.setId(null);
        return turfRepository.save(turf);
    }
}
