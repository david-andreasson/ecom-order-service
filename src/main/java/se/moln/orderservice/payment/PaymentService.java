package se.moln.orderservice.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.moln.orderservice.model.Payment;
import se.moln.orderservice.payment.dto.PaymentCreateRequest;
import se.moln.orderservice.payment.dto.PaymentCreateResponse;
import se.moln.orderservice.payment.dto.ProviderCreateResult;
import se.moln.orderservice.payment.dto.PaymentStatus;
import se.moln.orderservice.repository.PaymentRepository;

import java.util.List;

@Service
public class PaymentService {

    private final List<PaymentProvider> providers;
    private final PaymentRepository paymentRepository;

    public PaymentService(List<PaymentProvider> providers, PaymentRepository paymentRepository) {
        this.providers = providers;
        this.paymentRepository = paymentRepository;
    }

    private PaymentProvider resolveProvider(String providerId) {
        String id = (providerId == null || providerId.isBlank()) ? "stripe" : providerId.toLowerCase();
        return providers.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + id));
    }

    @Transactional
    public PaymentCreateResponse create(PaymentCreateRequest req) {
        PaymentProvider provider = resolveProvider(req.getProvider());
        ProviderCreateResult result = provider.create(req);

        Payment payment = Payment.builder()
                .provider(provider.id())
                .providerRef(result.getProviderRef())
                .status(result.getStatus() == null ? PaymentStatus.PENDING : result.getStatus())
                .amountFiat(req.getAmountFiat())
                .currencyFiat(req.getCurrencyFiat())
                .currencyCrypto(result.getCurrencyCrypto())
                .amountCrypto(result.getAmountCrypto())
                .addressOrUrl(result.getAddressOrUrl())
                .build();
        payment = paymentRepository.save(payment);

        return PaymentCreateResponse.builder()
                .paymentId(payment.getId())
                .provider(payment.getProvider())
                .clientSecret(result.getClientSecret())
                .addressOrUrl(result.getAddressOrUrl())
                .amountFiat(payment.getAmountFiat())
                .currencyFiat(payment.getCurrencyFiat())
                .currencyCrypto(payment.getCurrencyCrypto())
                .amountCrypto(payment.getAmountCrypto())
                .status(payment.getStatus())
                .build();
    }
}
