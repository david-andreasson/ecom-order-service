package se.moln.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryPurchaseRequest(
        @NotNull @Min(1) int quantity
) {}
