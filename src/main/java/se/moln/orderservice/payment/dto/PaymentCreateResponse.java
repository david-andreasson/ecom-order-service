package se.moln.orderservice.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateResponse {
    private UUID paymentId;
    private String provider;       // stripe | monero
    private String clientSecret;   // Stripe client_secret (if stripe)
    private String addressOrUrl;   // Monero subaddress or checkout URL
    private Long amountFiat;
    private String currencyFiat;
    private String currencyCrypto; // XMR (optional)
    private String amountCrypto;   // optional
    private PaymentStatus status;
}
