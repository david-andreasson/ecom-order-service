package se.moln.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OrderHistoryDto {

    private UUID id;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount;
    private List<OrderItemDto> items;

    public UUID getId() {
        return id;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public OrderHistoryDto(UUID id, LocalDateTime orderDate, BigDecimal totalAmount, List<OrderItemDto> items) {
        this.id = id;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.items = items;
    }
}