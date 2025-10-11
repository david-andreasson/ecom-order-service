package se.moln.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.moln.orderservice.model.Payment;

import java.util.UUID;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);
}
