package se.moln.orderservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.model.OrderStatus;
import se.moln.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    WebClient.Builder webClientBuilder;

    @Mock
    OrderRepository orderRepository;

    @Mock
    JwtService jwtService;

    @InjectMocks
    OrderService orderService;

    @Test
    void getOrderHistory_mapsEntitiesToDtos() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("token")).thenReturn(userId);

        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setOrderNumber("ORD-12345678");
        o.setStatus(OrderStatus.COMPLETED);
        o.setOrderDate(OffsetDateTime.now());
        o.setTotalAmount(new BigDecimal("49.98"));

        OrderItem it = new OrderItem();
        it.setProductId(UUID.randomUUID());
        it.setProductName("USB-C Hub 8-in-1");
        it.setQuantity(2);
        it.setPriceAtPurchase(new BigDecimal("24.99"));
        it.setOrder(o);
        o.setOrderItems(List.of(it));

        Page<Order> page = new PageImpl<>(List.of(o), PageRequest.of(0, 10), 1);
        when(orderRepository.findByUserId(eq(userId), any())).thenReturn(page);

        Mono<List<OrderHistoryDto>> mono = orderService.getOrderHistory("token", 0, 10);
        List<OrderHistoryDto> dtos = mono.block();
        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        OrderHistoryDto dto = dtos.get(0);
        assertEquals(o.getId(), dto.id());
        assertEquals(o.getOrderNumber(), dto.orderNumber());
        assertEquals(o.getTotalAmount(), dto.totalAmount());
        assertEquals(o.getStatus(), dto.status());
        assertEquals(o.getOrderDate(), dto.orderDate());
        assertEquals(1, dto.items().size());
        assertEquals(it.getProductId(), dto.items().get(0).productId());
        assertEquals(it.getProductName(), dto.items().get(0).productName());
        assertEquals(it.getQuantity(), dto.items().get(0).quantity());
        assertEquals(it.getPriceAtPurchase(), dto.items().get(0).priceAtPurchase());
    }

    @Test
    void getOrderHistory_errorsOnMissingToken() {
        Mono<List<OrderHistoryDto>> mono = orderService.getOrderHistory("", 0, 10);
        Exception ex = assertThrows(IllegalArgumentException.class, mono::block);
        assertTrue(ex.getMessage().toLowerCase().contains("missing bearer token"));
    }
}
