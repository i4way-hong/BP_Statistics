package com.example.bpsample14.service;

import com.example.bpsample14.web.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OAuthClient {
    private final WebClient webClient;

    public OAuthClient(WebClient.Builder builder, @Value("${external.oauth.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<TokenResponse> getToken(String clientId, String clientSecret, String scope) {
        return webClient.post()
                .uri("/configapi/v2/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("scope", scope)
                        .with("grant_type", "client_credentials"))
                .retrieve()
                .bodyToMono(TokenResponse.class);
    }
}
