package se.moln.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private EntitlementClient entitlementClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, "http://users", "http://products", jwtService, entitlementClient);
    }

    @Test
    void getOrderHistory_mapsEntitiesToDtos() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("token")).thenReturn(userId);

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-12345678");
        order.setStatus(OrderStatus.COMPLETED);
        order.setOrderDate(OffsetDateTime.now());
        order.setTotalAmount(new BigDecimal("49.98"));

        OrderItem item = new OrderItem();
        item.setProductId(UUID.randomUUID());
        item.setProductName("USB-C Hub 8-in-1");
        item.setQuantity(2);
        item.setPriceAtPurchase(new BigDecimal("24.99"));
        item.setOrder(order);
        order.setOrderItems(List.of(item));

        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(orderRepository.findByUserId(any(UUID.class), any())).thenReturn(page);

        List<OrderHistoryDto> dtos = orderService.getOrderHistory("token", 0, 10);
        assertEquals(1, dtos.size());
        OrderHistoryDto dto = dtos.get(0);
        assertEquals(order.getId(), dto.id());
        assertEquals(order.getOrderNumber(), dto.orderNumber());
        assertEquals(order.getTotalAmount(), dto.totalAmount());
        assertEquals(order.getStatus(), dto.status());
        assertEquals(order.getOrderDate(), dto.orderDate());
        assertEquals(1, dto.items().size());
        assertEquals(item.getProductId(), dto.items().get(0).productId());
        assertEquals(item.getProductName(), dto.items().get(0).productName());
        assertEquals(item.getQuantity(), dto.items().get(0).quantity());
        assertEquals(item.getPriceAtPurchase(), dto.items().get(0).priceAtPurchase());
    }

    @Test
    void getOrderHistory_errorsOnMissingToken() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.getOrderHistory("", 0, 10));
        assertTrue(ex.getMessage().toLowerCase().contains("missing bearer token"));
    }
}
