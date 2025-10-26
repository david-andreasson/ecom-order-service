package se.moln.orderservice.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PaymentCreateRequest {
    /** Amount in minor units (e.g. cents). */
    @NotNull
    @Min(1)
    private Long amountFiat;

    /** Currency like SEK, USD */
    @NotBlank
    private String currencyFiat;

    /** Optional customer email */
    private String email;

    /** Provider id: "stripe" (default) or "monero" */
    private String provider;

    /** Optional metadata to attach to provider object */
    private Map<String, String> metadata;
}
