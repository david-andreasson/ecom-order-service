package se.moln.orderservice.dto;

import java.util.UUID;

public record PurchaseResponse(
        UUID productId,
        int purchasedQuantity,
        int newStockQuantity,
        String message
) {}