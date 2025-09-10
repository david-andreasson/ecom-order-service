package se.moln.orderservice.dto;


import jakarta.validation.constraints.Min;

public record AdjustStockRequest(
        @Min(1) int quantity
) {}