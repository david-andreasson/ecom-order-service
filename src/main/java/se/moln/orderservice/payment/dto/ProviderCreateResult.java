package se.moln.orderservice.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCreateResult {
    private String provider;        // stripe | monero
    private String providerRef;     // PaymentIntent id, invoice id, tx hash, etc.
    private String clientSecret;    // Stripe client_secret
    private String addressOrUrl;    // Monero subaddress / payment URI
    private String currencyCrypto;  // e.g. XMR
    private String amountCrypto;    // string for precision
    private PaymentStatus status;   // initial status
}
