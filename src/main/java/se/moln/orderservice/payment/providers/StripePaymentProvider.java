package se.moln.orderservice.payment.providers;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.moln.orderservice.payment.PaymentProvider;
import se.moln.orderservice.payment.dto.PaymentCreateRequest;
import se.moln.orderservice.payment.dto.PaymentStatus;
import se.moln.orderservice.payment.dto.ProviderCreateResult;

import java.util.HashMap;
import java.util.Map;

@Component
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final String secretKey;

    public StripePaymentProvider(@Value("${STRIPE_SECRET_KEY:}") String secretKey) {
        this.secretKey = secretKey;
        log.info("StripePaymentProvider initialized. Secret key length: {}", secretKey != null ? secretKey.length() : 0);
    }

    @Override
    public String id() {
        return "stripe";
    }

    @Override
    public ProviderCreateResult create(PaymentCreateRequest req) {
        log.info("StripePaymentProvider.create called. secretKey present: {}", secretKey != null && !secretKey.isBlank());
        if (secretKey == null || secretKey.isBlank()) {
            // No key configured: return stubbed error-like result
            log.error("STRIPE_SECRET_KEY is missing or blank!");
            return ProviderCreateResult.builder()
                    .provider(id())
                    .status(PaymentStatus.FAILED)
                    .build();
        }
        Stripe.apiKey = secretKey;
        try {
            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(req.getAmountFiat())
                    .setCurrency(req.getCurrencyFiat().toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                    );
            if (req.getEmail() != null && !req.getEmail().isBlank()) {
                Map<String, Object> receiptEmail = new HashMap<>();
                // Stripe supports receipt_email directly on PaymentIntent
                builder.setReceiptEmail(req.getEmail());
            }
            if (req.getMetadata() != null && !req.getMetadata().isEmpty()) {
                builder.putAllMetadata(req.getMetadata());
            }
            PaymentIntent pi = PaymentIntent.create(builder.build().toMap());
            log.info("Stripe PaymentIntent created successfully. ID: {}, clientSecret present: {}", pi.getId(), pi.getClientSecret() != null);
            return ProviderCreateResult.builder()
                    .provider(id())
                    .providerRef(pi.getId())
                    .clientSecret(pi.getClientSecret())
                    .status(mapStripeStatus(pi.getStatus()))
                    .build();
        } catch (StripeException e) {
            log.error("Stripe payment intent creation failed: {} - Full error: {}", e.getMessage(), e.getClass().getSimpleName(), e);
            return ProviderCreateResult.builder()
                    .provider(id())
                    .status(PaymentStatus.FAILED)
                    .build();
        }
    }

    private PaymentStatus mapStripeStatus(String s) {
        if (s == null) return PaymentStatus.PENDING;
        return switch (s) {
            case "requires_payment_method", "processing" -> PaymentStatus.PENDING;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            default -> PaymentStatus.PENDING;
        };
    }
}
