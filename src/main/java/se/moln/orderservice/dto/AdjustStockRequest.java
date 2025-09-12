package se.moln.orderservice.dto;

import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(
        @NotNull int delta
) {}