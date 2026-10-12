
package com.turfbook.backend;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TurfRepository extends MongoRepository<Turf, String> {
}
