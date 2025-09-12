package se.moln.orderservice.dto;

import java.util.UUID;

public class UserResponse {
    private UUID userId;
    private UUID id;

    public UUID getId() {
        return userId != null ? userId : id;
    }
}