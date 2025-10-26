package se.moln.orderservice.payment;

import se.moln.orderservice.payment.dto.PaymentCreateRequest;
import se.moln.orderservice.payment.dto.ProviderCreateResult;

public interface PaymentProvider {
    String id(); // e.g. "stripe", "monero"

    ProviderCreateResult create(PaymentCreateRequest req);
}
