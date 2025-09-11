package se.moln.orderservice.dto;

import java.math.BigDecimal;

public class OrderItemDto {

    private String productName;
    private int quantity;
    private BigDecimal priceAtPurchase;

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceAtPurchase() {
        return priceAtPurchase;
    }

    public OrderItemDto(String productName, int quantity, BigDecimal priceAtPurchase) {
        this.productName = productName;
        this.quantity = quantity;
        this.priceAtPurchase = priceAtPurchase;
    }
}