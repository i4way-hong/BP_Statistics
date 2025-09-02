package com.example.bpsample14.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BrightPattern OAuth2 Client Credentials 토큰 서비스
 */
@Service
public class OAuthTokenService {
    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    @Value("${external.oauth.base-url}")
    private String baseUrl;
    @Value("${external.oauth.client-id}")
    private String clientId;
    @Value("${external.oauth.client-secret}")
    private String clientSecret;
    @Value("${external.oauth.scope:i4way.brightpattern.com}")
    private String scope;

    private final WebClient webClient;

    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiry = new AtomicReference<>();

    public OAuthTokenService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public String getValidToken() {
        var now = Instant.now();
        var exp = tokenExpiry.get();
        var token = accessToken.get();
        if (token != null && exp != null && now.isBefore(exp.minusSeconds(10))) {
            return token;
        }
        return fetchToken().block();
    }

    private Mono<String> fetchToken() {
        String tokenUrl = baseUrl.endsWith("/") ? baseUrl + "configapi/v2/oauth/token" : baseUrl + "/configapi/v2/oauth/token";
        log.info("OAuth 토큰 요청 시작 url={}", tokenUrl);
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("grant_type","client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", scope);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> {
                    // 단순 파싱 (정식 Jackson 사용)
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
                        String at = root.path("access_token").asText(null);
                        long expiresIn = root.path("expires_in").asLong(600);
                        if (at == null) throw new IllegalStateException("access_token 누락");
                        accessToken.set(at);
                        tokenExpiry.set(Instant.now().plusSeconds(expiresIn));
                        log.info("OAuth 토큰 획득 성공 (만료 {}s)", expiresIn);
                        return at;
                    } catch (Exception e) {
                        log.error("OAuth 토큰 파싱 실패: {}", e.getMessage());
                        throw new IllegalStateException("OAuth 토큰 파싱 실패", e);
                    }
                });
    }

    public String getAccessTokenMasked() {
        var t = accessToken.get();
        if (t == null) return null;
        return t.length() <= 8 ? t : t.substring(0,8) + "***";
    }
}
