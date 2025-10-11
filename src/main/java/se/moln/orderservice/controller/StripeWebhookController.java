package se.moln.orderservice.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import se.moln.orderservice.payment.dto.PaymentStatus;
import se.moln.orderservice.repository.PaymentRepository;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/orders/payments/webhook")
public class StripeWebhookController {

    private final PaymentRepository paymentRepository;
    private final String webhookSecret;

    public StripeWebhookController(PaymentRepository paymentRepository,
                                   @Value("${STRIPE_WEBHOOK_SECRET:}") String webhookSecret) {
        this.paymentRepository = paymentRepository;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping(path = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<String> handleStripe(@RequestHeader(name = "Stripe-Signature", required = false) String sigHeader,
                                               @RequestBody byte[] payloadBytes) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body("Missing stripe.webhook-secret configuration");
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // We only care about PaymentIntent events
        if ("payment_intent.succeeded".equals(event.getType()) ||
            "payment_intent.payment_failed".equals(event.getType()) ||
            "payment_intent.canceled".equals(event.getType()) ||
            "payment_intent.processing".equals(event.getType())) {
            try {
                PaymentIntent pi = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (pi != null) {
                    paymentRepository.findByProviderAndProviderRef("stripe", pi.getId())
                            .ifPresent(p -> {
                                p.setStatus(mapStripeStatus(pi.getStatus()));
                                paymentRepository.save(p);
                            });
                }
            } catch (ClassCastException ignored) {
                // ignore non PI payloads
            }
        }
        return ResponseEntity.ok("ok");
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
