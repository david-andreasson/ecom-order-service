package se.moln.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.moln.orderservice.payment.PaymentService;
import se.moln.orderservice.payment.dto.PaymentCreateRequest;
import se.moln.orderservice.payment.dto.PaymentCreateResponse;

@RestController
@RequestMapping("/api/orders/payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(path = "/create-intent", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create payment intent (Stripe) or crypto request (Monero stub)")
    public ResponseEntity<PaymentCreateResponse> create(@Valid @RequestBody PaymentCreateRequest req) {
        return ResponseEntity.ok(paymentService.create(req));
    }

    // Webhook endpoint will be added later (Stripe + Monero status updates)
}
