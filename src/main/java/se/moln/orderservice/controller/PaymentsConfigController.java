package se.moln.orderservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders/payments")
public class PaymentsConfigController {

    private final String publishableKey;

    public PaymentsConfigController(@Value("${STRIPE_PUBLISHABLE_KEY:}") String publishableKey) {
        this.publishableKey = publishableKey;
    }

    @GetMapping(path = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "publishableKey", publishableKey == null ? "" : publishableKey
        ));
    }
}
