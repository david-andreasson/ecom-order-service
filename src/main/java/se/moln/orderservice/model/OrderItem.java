package se.moln.orderservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID productId;
    private int quantity;
    private BigDecimal priceAtPurchase;
    private String productName;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
}