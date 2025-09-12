package se.moln.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseResponse(
        UUID orderId,
        String orderNumber,
        BigDecimal totalAmount
) {}