package se.moln.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
        UUID productId,
        String productName,
        int quantity,
        BigDecimal priceAtPurchase
) {}