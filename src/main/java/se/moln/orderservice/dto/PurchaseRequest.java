package se.moln.orderservice.dto;

import java.util.UUID;

public record PurchaseRequest(
        UUID productId,
        int quantity
) {}