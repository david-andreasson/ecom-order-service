package se.moln.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import se.moln.orderservice.payment.dto.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String provider; // e.g., stripe, monero

    @Column(nullable = true)
    private String providerRef; // PaymentIntent id, invoice id, tx hash, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // Fiat reference (minor units, e.g., cents)
    @Column(nullable = false)
    private Long amountFiat;

    @Column(nullable = false)
    private String currencyFiat; // e.g., SEK, USD

    // Optional crypto reference
    private String currencyCrypto; // e.g., XMR
    private String amountCrypto;   // keep as string for precision (atomic units if needed)

    private String addressOrUrl;   // monero subaddress or checkout url

    private Instant expiresAt;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
