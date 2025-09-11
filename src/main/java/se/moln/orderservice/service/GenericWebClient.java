package se.moln.orderservice.service;


import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GenericWebClient {
    private final WebClient webClient;

    public GenericWebClient(WebClient.Builder webClientBuilder){
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8081")
                .build();
    }

    public <T, R>Mono<R> post(String uri, T requestBody, Class<R> responseType){
        return webClient.post()
                .uri(uri)
                .body(Mono.just(requestBody), requestBody.getClass())
                .retrieve()
                .bodyToMono(responseType);
    }


    public <R> Mono<R> get(String uri, Class<R> responseType) {
        return webClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Fel vid anrop till: " + uri + ". Status: " + clientResponse.statusCode() + ". Body: " + errorBody))))
                .bodyToMono(responseType);
    }

}
