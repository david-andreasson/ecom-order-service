package se.moln.orderservice.dto;

import se.moln.orderservice.model.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderHistoryDto(
        UUID id,
        String orderNumber,
        BigDecimal totalAmount,
        OrderStatus status,
        OffsetDateTime orderDate,
        List<OrderItemDto> items
) {}