package se.moln.orderservice.payment.providers;

import org.springframework.stereotype.Component;
import se.moln.orderservice.payment.PaymentProvider;
import se.moln.orderservice.payment.dto.PaymentCreateRequest;
import se.moln.orderservice.payment.dto.PaymentStatus;
import se.moln.orderservice.payment.dto.ProviderCreateResult;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MoneroPaymentProvider implements PaymentProvider {

    private static final SecureRandom RNG = new SecureRandom();

    @Override
    public String id() {
        return "monero";
    }

    @Override
    public ProviderCreateResult create(PaymentCreateRequest req) {
        // Stub implementation: generate a pseudo subaddress and suggest a placeholder amount in XMR.
        String pseudoAddress = "4" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(48));
        // Very naive placeholder conversion: 1 XMR == 2000 SEK (for demo only!)
        double rateSekPerXmr = 2000.0;
        double amountXmr = (req.getAmountFiat() / 100.0) / rateSekPerXmr; // amountFiat is minor units
        String amountXmrStr = String.format(java.util.Locale.US, "%.6f", amountXmr);
        return ProviderCreateResult.builder()
                .provider(id())
                .providerRef(pseudoAddress)
                .addressOrUrl(pseudoAddress)
                .currencyCrypto("XMR")
                .amountCrypto(amountXmrStr)
                .status(PaymentStatus.PENDING)
                .build();
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }
}
