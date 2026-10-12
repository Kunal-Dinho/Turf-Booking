package com.turfbook.backend;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DatabaseController {

    private final MongoTemplate mongoTemplate;

    public DatabaseController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/api/db-check")
    public Map<String, String> checkDatabase() {
        mongoTemplate.executeCommand(new Document("ping", 1));
        return Map.of("status", "UP", "database", "MongoDB");
    }
}