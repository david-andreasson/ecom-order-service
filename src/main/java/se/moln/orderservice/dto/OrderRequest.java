package se.moln.orderservice.dto;


import java.util.List;

public record OrderRequest(
        String jwtToken,
        List<OrderItemRequest> items
) {}