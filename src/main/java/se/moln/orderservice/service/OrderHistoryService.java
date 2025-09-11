package se.moln.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import se.moln.orderservice.dto.OrderHistoryDto;
import se.moln.orderservice.dto.OrderItemDto;
import se.moln.orderservice.model.Order;
import se.moln.orderservice.model.OrderItem;
import se.moln.orderservice.repository.OrderRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderHistoryService {

    private final WebClient.Builder webClientBuilder;
    private final OrderRepository orderRepository;

    private final String userServiceUrl = "https://userservice.drillbi.se";

    public Mono<List<OrderHistoryDto>> getOrdersForUser(String jwtToken, int page, int size) {
        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .flatMap(user -> {
                    UUID userId = user.getId();

                    return Mono.fromCallable(() -> orderRepository
                                    .findByUserId(userId, PageRequest.of(page, size, Sort.by("orderDate").descending()))
                                    .getContent())
                            .map(this::mapToDtoList);
                });
    }

    private List<OrderHistoryDto> mapToDtoList(List<Order> orders) {
        return orders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private OrderHistoryDto mapToDto(Order order) {
        List<OrderItemDto> itemDtos = order.getOrderItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());

        return new OrderHistoryDto(
                order.getId(),
                order.getOrderDate(),
                order.getTotalAmount(),
                itemDtos
        );
    }

    private OrderItemDto mapItemToDto(OrderItem item) {
        return new OrderItemDto(
                item.getProductName(),
                item.getQuantity(),
                item.getPriceAtPurchase()
        );
    }


    private static class UserResponse {
        private UUID userId;
        public UUID getId() {
            return userId;
        }
    }
}