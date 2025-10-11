package se.moln.orderservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders/debug")
public class DebugController {

    private final String stripeSecretKey;
    private final String stripePublishableKey;

    public DebugController(
            @Value("${STRIPE_SECRET_KEY:NOT_SET}") String stripeSecretKey,
            @Value("${STRIPE_PUBLISHABLE_KEY:NOT_SET}") String stripePublishableKey) {
        this.stripeSecretKey = stripeSecretKey;
        this.stripePublishableKey = stripePublishableKey;
    }

    @GetMapping("/env")
    public ResponseEntity<Map<String, String>> getEnv() {
        return ResponseEntity.ok(Map.of(
                "stripeSecretKeyPresent", (stripeSecretKey != null && !stripeSecretKey.equals("NOT_SET") && !stripeSecretKey.isBlank()) ? "YES (length: " + stripeSecretKey.length() + ")" : "NO",
                "stripePublishableKeyPresent", (stripePublishableKey != null && !stripePublishableKey.equals("NOT_SET") && !stripePublishableKey.isBlank()) ? "YES (length: " + stripePublishableKey.length() + ")" : "NO"
        ));
    }
}
