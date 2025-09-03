package com.example.bpstatistics.service;

import com.example.bpstatistics.web.dto.BrightPatternSubscriptionRequest;
import com.example.bpstatistics.web.dto.BrightPatternSubscriptionResponse;
import com.example.bpstatistics.web.exception.NotAuthenticatedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.netty.http.client.PrematureCloseException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BrightPattern 이벤트 구독 관련 서비스 (예시)
 */
@Service
public class BrightPatternSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(BrightPatternSubscriptionService.class);

    private final WebClient webClient;
    private final BrightPatternAuthService authService;
    private final AtomicBoolean reauthInProgress = new AtomicBoolean(false);

    @Value("${brightpattern.auth.token}")
    private String bearerToken;

    @Value("${brightpattern.subscription.base-url:https://i4way.brightpattern.com/statsapi/subscription}")
    private String subscriptionBaseUrl; // 단수 endpoint 로 수정

    @Value("${brightpattern.auth.cookie:}")
    private String cookieHeader; // 필요 시 application.yml에 설정

    @Value("${brightpattern.subscription.data-url:}")
    private String subscriptionDataUrl; // 선택적 오버라이드

    @Autowired(required = false)
    private ObjectMapper objectMapper; // 선택 주입 (Spring 기본 ObjectMapper 사용)

    public BrightPatternSubscriptionService(WebClient.Builder builder, BrightPatternAuthService authService) {
        // ReactiveHttpClientConfig 에서 커스텀 ConnectionProvider / HttpClient 적용된 builder 주입
        this.webClient = builder.build();
        this.authService = authService;
    }

    private String buildCookie() { return cookieHeader == null || cookieHeader.isBlank() ? "" : cookieHeader; }

    private String resolveDataUrl() { return (subscriptionDataUrl != null && !subscriptionDataUrl.isBlank()) ? subscriptionDataUrl : subscriptionBaseUrl + "/data"; }

    private String buildCookieWithSession() {
        String base = buildCookie();
        String st = authService.getSessionToken();
        if (st != null && !st.isBlank() && (base == null || !base.contains("X-BP-SESSION-ID"))) {
            if (base == null || base.isBlank()) return "X-BP-SESSION-ID=" + st;
            return base + "; X-BP-SESSION-ID=" + st;
        }
        return base == null ? "" : base;
    }

    private String authHeaderToken() {
        var session = authService.getSessionToken();
        return session != null ? session : bearerToken; // 둘 다 그대로 사용 (Bearer prefix 없음)
    }

    // 자동 재인증 비동기 보장
    private Mono<Void> ensureAuthenticatedReactive() {
        if (authService.isAuthenticated()) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            if (authService.isAuthenticated()) return Mono.empty();
            if (reauthInProgress.compareAndSet(false, true)) {
                log.info("세션 없음/만료 - 자동 재인증 시도");
                return authService.authenticate()
                        .onErrorResume(e -> {
                            log.error("자동 재인증 실패: {}", e.getMessage());
                            return Mono.empty();
                        })
                        .then(Mono.fromRunnable(() -> reauthInProgress.set(false)))
                        .then(Mono.defer(() -> authService.isAuthenticated()
                                ? Mono.empty()
                                : Mono.error(new NotAuthenticatedException("자동 재인증 실패"))));
            } else {
                // 다른 쓰레드가 재인증 중 -> 약간 대기 후 재확인
                return Mono.delay(Duration.ofMillis(300))
                        .then(Mono.defer(() -> authService.isAuthenticated()
                                ? Mono.empty()
                                : Mono.error(new NotAuthenticatedException("자동 재인증 진행 중이었으나 실패"))));
            }
        });
    }

    // PrematureClose / 네트워크 성격 오류만 재시도 (POST 는 2회, GET 은 1회)
    private Retry retryForPost(int maxRetries) {
        return Retry.backoff(maxRetries, Duration.ofMillis(400))
                .filter(ex -> ex instanceof PrematureCloseException || ex instanceof IOException || ex instanceof TimeoutException)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private Retry retryForGet(int maxRetries) {
        return Retry.backoff(maxRetries, Duration.ofMillis(250))
                .filter(ex -> ex instanceof PrematureCloseException || ex instanceof TimeoutException)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    // Raw 구조 간단 유효성 검사
    private void validateRawStructure(Map<?,?> root) {
        if (root == null || root.isEmpty()) throw new IllegalArgumentException("구독 JSON이 비어 있습니다.");
        Object first = root.get("1");
        if (!(first instanceof Map<?,?> firstObj)) throw new IllegalArgumentException("루트 키 '1' 객체가 필요합니다.");
        Object grids = firstObj.get("agent_grids");
        if (!(grids instanceof java.util.List<?> list) || list.isEmpty()) throw new IllegalArgumentException("'1.agent_grids' 배열이 비어있거나 없음");
        int idx = 0;
        for (Object o : list) {
            idx++;
            Map<?,?> gridMap = null;
            if (o instanceof Map<?,?> gm) {
                gridMap = gm;
            } else if (o instanceof BrightPatternSubscriptionRequest.AgentGrid ag) {
                // DTO 직접 입력된 경우 리플렉션으로 최소 필드 확인
                if (ag.getId() == null || ag.getId().isBlank()) throw new IllegalArgumentException("agent_grids["+idx+"] id 누락");
                if (ag.getColumns() == null || ag.getColumns().isEmpty()) throw new IllegalArgumentException("agent_grids["+idx+"] columns 누락");
                continue; // OK
            } else if (objectMapper != null) {
                try {
                    @SuppressWarnings("unchecked") Map<String,Object> converted = objectMapper.convertValue(o, Map.class);
                    gridMap = converted;
                } catch (IllegalArgumentException convEx) {
                    throw new IllegalArgumentException("agent_grids["+idx+"] 변환 불가: " + convEx.getMessage());
                }
            }
            if (gridMap == null) {
                throw new IllegalArgumentException("agent_grids["+idx+"] 요소는 객체(Map/DTO)여야 합니다.");
            }
            Object id = gridMap.get("id");
            if (id == null || id.toString().isBlank()) throw new IllegalArgumentException("agent_grids["+idx+"] id 누락");
            Object columns = gridMap.get("columns");
            if (!(columns instanceof java.util.List<?> cols) || cols.isEmpty()) throw new IllegalArgumentException("agent_grids["+idx+"] columns 배열 누락");
        }
    }

    public Mono<BrightPatternSubscriptionResponse> createRaw(Object rawBody) {
        return ensureAuthenticatedReactive()
                .then(Mono.defer(() -> {
                    log.info("구독 생성(raw) 호출");
                    if (rawBody instanceof Map<?,?> mapBody) {
                        validateRawStructure((Map<?,?>) mapBody);
                    }
                    return webClient.post()
                            .uri(subscriptionBaseUrl)
                            .header(HttpHeaders.AUTHORIZATION, authHeaderToken())
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .header(HttpHeaders.COOKIE, buildCookieWithSession())
                            .bodyValue(rawBody)
                            .exchangeToMono(res -> {
                                if (res.statusCode().is2xxSuccessful()) {
                                    return res.bodyToMono(BrightPatternSubscriptionResponse.class);
                                }
                                return handleError(res, BrightPatternSubscriptionResponse.class);
                            })
                            .timeout(Duration.ofSeconds(30))
                            .retryWhen(retryForPost(2))
                            .doOnSuccess(r -> { if (r!=null) log.info("구독 생성 성공 id={}", r.getSubscriptionId()); })
                            .doOnError(e -> log.error("구독 생성 실패", e));
                }));
    }

    // 기존 create 메서드는 BrightPatternSubscriptionRequest 를 그대로 body로 넣는데
    // 실제 API는 숫자 키("1") 루트에 agent_grids 배열을 감싸는 구조이므로 wrapping 처리
    public Mono<BrightPatternSubscriptionResponse> create(BrightPatternSubscriptionRequest req) {
        return ensureAuthenticatedReactive()
                .then(Mono.defer(() -> {
                    log.info("구독 생성 요청(agent_grids) size={}", req.getAgent_grids()==null?0:req.getAgent_grids().size());
                    var wrapper = java.util.Map.of("1", java.util.Map.of("agent_grids", req.getAgent_grids()));
                    return createRaw(wrapper);
                }));
    }

    public Mono<Void> delete(String subscriptionId) {
        return ensureAuthenticatedReactive()
                .then(Mono.defer(() -> {
                    log.info("구독 삭제 요청 id={}", subscriptionId);
                    return webClient.delete()
                            .uri(subscriptionBaseUrl + "/" + subscriptionId)
                            .header(HttpHeaders.AUTHORIZATION, authHeaderToken())
                            .header(HttpHeaders.COOKIE, buildCookieWithSession())
                            .exchangeToMono(res -> {
                                if (res.statusCode().is2xxSuccessful() || res.statusCode()==HttpStatus.NO_CONTENT) {
                                    return Mono.empty();
                                }
                                return handleError(res, Void.class);
                            })
                            .timeout(Duration.ofSeconds(15))
                            .retryWhen(retryForPost(1))
                            .doOnSuccess(v -> log.info("구독 삭제 성공 id={}", subscriptionId))
                            .doOnError(e -> log.error("구독 삭제 실패 id=" + subscriptionId, e));
                }));
    }

    public Mono<BrightPatternSubscriptionResponse> get(String subscriptionId) {
        return ensureAuthenticatedReactive()
                .then(Mono.defer(() -> {
                    log.debug("구독 조회 id={}", subscriptionId);
                    return webClient.get()
                            .uri(subscriptionBaseUrl + "/" + subscriptionId)
                            .header(HttpHeaders.AUTHORIZATION, authHeaderToken())
                            .header(HttpHeaders.COOKIE, buildCookieWithSession())
                            .exchangeToMono(res -> {
                                if (res.statusCode().is2xxSuccessful()) {
                                    return res.bodyToMono(BrightPatternSubscriptionResponse.class);
                                }
                                return handleError(res, BrightPatternSubscriptionResponse.class);
                            })
                            .timeout(Duration.ofSeconds(10))
                            .retryWhen(retryForGet(1))
                            .doOnError(e -> log.error("구독 조회 실패 id=" + subscriptionId, e));
                }));
    }

    public Mono<String> getData() {
        return ensureAuthenticatedReactive()
                .then(Mono.defer(() -> {
                    log.debug("구독 데이터 조회");
                    return webClient.get()
                            .uri(resolveDataUrl())
                            .header(HttpHeaders.AUTHORIZATION, authHeaderToken())
                            .header(HttpHeaders.COOKIE, buildCookieWithSession())
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(15))
                            .retryWhen(retryForGet(1))
                            .doOnError(e -> log.error("구독 데이터 조회 실패", e));
                }));
    }

    // 누락된 오류 처리 메서드 복구
    private <T> Mono<T> handleError(ClientResponse res, Class<T> bodyType) {
        return res.bodyToMono(String.class).flatMap(msg -> {
            log.error("BrightPattern API error status={} body={}", res.statusCode(), msg);
            return Mono.error(new IllegalStateException("BrightPattern API error: " + res.statusCode()));
        });
    }
}
