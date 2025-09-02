package com.example.bpsample14.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BrightPattern 인증 서비스
 * 제공된 curl 명령어를 기반으로 BrightPattern API 인증을 수행합니다.
 */
@Service
public class BrightPatternAuthService {

    private static final Logger log = LoggerFactory.getLogger(BrightPatternAuthService.class);

    private final WebClient webClient;

    @Value("${brightpattern.auth.url}")
    private String authUrl;

    @Value("${brightpattern.auth.tenant}")
    private String tenantUrl;

    @Value("${brightpattern.auth.username}")
    private String username;

    @Value("${brightpattern.auth.password}")
    private String password;

    @Value("${brightpattern.auth.cookie:}")
    private String authCookie; // 외부에서 제공된 고정 쿠키 (선택)

    private final AtomicReference<String> sessionToken = new AtomicReference<>();
    private final AtomicReference<Instant> sessionExpiry = new AtomicReference<>();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OAuthTokenService oAuthTokenService;

    public BrightPatternAuthService(WebClient.Builder webClientBuilder, OAuthTokenService oAuthTokenService) {
        // ReactiveHttpClientConfig 에서 설정한 builder 주입됨
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.oAuthTokenService = oAuthTokenService;
    }

    /**
     * BrightPattern 인증 요청
     * curl 명령어와 동일한 요청을 수행합니다.
     */
    public Mono<String> authenticate() {
        log.info("BrightPattern 인증 시작 - URL: {} username={}", authUrl, username);

        // 요청 바디 생성
        Map<String, String> authRequest = Map.of(
                "tenant_url", tenantUrl,
                "username", username,
                "password", password
        );

        String bearer = oAuthTokenService.getValidToken();
        return webClient.post()
                .uri(authUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.COOKIE, buildCookieHeader())
                .bodyValue(authRequest)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                        .filter(ex -> ex instanceof PrematureCloseException || ex instanceof IOException || ex instanceof TimeoutException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .map(resp -> {
                    if (!parseAndStoreSession(resp)) {
                        throw new IllegalStateException("세션 토큰을 응답에서 찾지 못했습니다.");
                    }
                    log.info("BrightPattern 인증 성공 (세션 저장)");
                    return resp;
                })
                .doOnError(error -> log.error("BrightPattern 인증 실패: {}", error.getMessage()));
    }

    /**
     * 인증 상태 확인
     */
    public Mono<String> checkAuthStatus() {
        if (!isAuthenticated()) {
            return Mono.just("{\"authenticated\":false,\"sessionPresent\":" + (sessionToken.get() != null) + "}");
        }
        return Mono.just("{\"authenticated\":true,\"sessionToken\":\"" + sessionToken.get() + "\"}");
    }

    /**
     * 토큰 정보 반환
     */
    public Map<String, Object> getTokenInfo() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("authUrl", authUrl);
        m.put("tenantUrl", tenantUrl);
        m.put("username", username);
        m.put("oauthAccessTokenMasked", oAuthTokenService.getAccessTokenMasked());
        var st = sessionToken.get();
        var se = sessionExpiry.get();
        m.put("sessionPresent", st != null);
        if (st != null) m.put("sessionToken", st);
        if (se != null) m.put("sessionExpiry", se);
        m.put("authenticated", isAuthenticated());
        return m;
    }

    /**
     * 세션 토큰 및 만료 시간 확인
     */
    public boolean isAuthenticated() {
        var token = sessionToken.get();
        var exp = sessionExpiry.get();
        return token != null && exp != null && Instant.now().isBefore(exp.minusSeconds(5));
    }

    /**
     * 세션 토큰 반환
     */
    public String getSessionToken() {
        return sessionToken.get();
    }

    /**
     * 세션 정보 파싱 및 저장
     */
    private boolean parseAndStoreSession(String rawJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            String sid = findSessionIdRecursive(root);
            if (sid == null || sid.isBlank()) {
                log.warn("세션 토큰 키를 어디에서도 찾지 못했습니다.");
                return false;
            }
            sessionToken.set(sid);
            long expiresSec = findExpiryRecursive(root);
            if (expiresSec <= 0) {
                expiresSec = 600; // 기본 10분
            }
            sessionExpiry.set(Instant.now().plusSeconds(expiresSec));
            log.debug("세션 파싱 완료 token={} exp={}", sid, sessionExpiry.get());
            return true;
        } catch (Exception e) {
            log.error("세션 파싱 중 예외: {}", e.getMessage());
            return false;
        }
    }

    private static final Set<String> SESSION_KEYS = Set.of("session_id", "sessionId", "session_token", "sessionToken", "token", "id");
    private static final Set<String> EXPIRE_KEYS = Set.of("expires_in", "expiresIn", "expiry", "ttl");

    private String findSessionIdRecursive(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            for (String k : SESSION_KEYS) {
                JsonNode v = node.get(k);
                if (v != null && v.isTextual() && !v.asText().isBlank()) {
                    return v.asText();
                }
            }
            var it = node.fieldNames();
            while (it.hasNext()) {
                String name = it.next();
                String r = findSessionIdRecursive(node.get(name));
                if (r != null) return r;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String r = findSessionIdRecursive(child);
                if (r != null) return r;
            }
        }
        return null;
    }

    private long findExpiryRecursive(JsonNode node) {
        if (node == null) return -1;
        if (node.isObject()) {
            for (String k : EXPIRE_KEYS) {
                JsonNode v = node.get(k);
                if (v != null && v.canConvertToLong()) return v.asLong();
            }
            var it = node.fieldNames();
            while (it.hasNext()) {
                long r = findExpiryRecursive(node.get(it.next()));
                if (r > 0) return r;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                long r = findExpiryRecursive(child);
                if (r > 0) return r;
            }
        }
        return -1;
    }

    /**
     * 쿠키 헤더 구성
     * curl 명령어에 포함된 쿠키를 그대로 사용
     */
    private String buildCookieHeader() {
        return authCookie == null ? "" : authCookie;
    }
}
