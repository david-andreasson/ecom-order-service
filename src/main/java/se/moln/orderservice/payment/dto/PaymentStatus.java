package se.moln.orderservice.payment.dto;

public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED,
    CANCELED,
    EXPIRED
}
