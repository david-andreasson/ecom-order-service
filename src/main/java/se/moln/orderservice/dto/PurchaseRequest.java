package se.moln.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PurchaseRequest(
        @NotNull List<OrderItemRequest> items,
        String paymentId  // Optional: link to existing payment
) {
    public record OrderItemRequest(
            @NotNull UUID productId,
            @Min(1) int quantity
    ) {}
}
