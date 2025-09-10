package com.example.bpstatistics.web.controller;

import com.example.bpstatistics.service.BrightPatternAuthService;
import com.example.bpstatistics.service.BrightPatternSubscriptionService;
import com.example.bpstatistics.web.dto.BrightPatternSubscriptionRequest;
import com.example.bpstatistics.web.dto.BrightPatternSubscriptionResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import reactor.core.publisher.Mono;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@RestController
@RequestMapping(path = "/api/brightpattern", produces = MediaType.APPLICATION_JSON_VALUE)

public class BrightPatternApiController {
    private static final Logger log = LoggerFactory.getLogger(BrightPatternApiController.class);
    private final BrightPatternAuthService authService;
    private final BrightPatternSubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public BrightPatternApiController(BrightPatternAuthService authService,
                                      BrightPatternSubscriptionService subscriptionService,
                                      ObjectMapper objectMapper) {
        this.authService = authService;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/auth")
    public Mono<String> authenticate() { 
        log.info("API 인증 요청");
        return authService.authenticate(); }

    @GetMapping("/auth/status")
    public Mono<String> status() { return authService.checkAuthStatus(); }

    @GetMapping("/auth/info")
    public Map<String, Object> info() { 
        log.info("API 인증 정보요청");
        return authService.getTokenInfo(); }

    @PostMapping(value = "/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> create(@RequestBody JsonNode body) {
        log.info("API 구독 생성 요청(통합) - shape keys: {}", body != null && body.isObject() ? body.fieldNames().hasNext() ? body.fieldNames().next() : "(empty)" : "(non-object)");

        if (body != null && body.isObject() && body.has("1")) {
            Map<String, Object> raw = objectMapper.convertValue(body, new TypeReference<Map<String, Object>>(){});
            return subscriptionService.createRaw(raw)
                    .map(r -> ResponseEntity.ok().body((Object) r))
                    .onErrorResume(e -> Mono.just(
                            ResponseEntity.badRequest().body(Map.of(
                                    "error", "ValidationError",
                                    "message", e.getMessage() == null ? "구독 JSON 검증 실패" : e.getMessage()
                            ))
                    ));
        }

        if (body != null && body.isObject() && body.has("agent_grids")) {
            try {
                BrightPatternSubscriptionRequest req = objectMapper.convertValue(body, BrightPatternSubscriptionRequest.class);
                return subscriptionService.create(req)
                        .map(r -> ResponseEntity.ok().body((Object) r))
                        .onErrorResume(e -> Mono.just(
                                ResponseEntity.badRequest().body(Map.of(
                                        "error", "ValidationError",
                                        "message", e.getMessage() == null ? "구독 DTO 검증 실패" : e.getMessage()
                                ))
                        ));
            } catch (IllegalArgumentException iae) {
                return Mono.just(ResponseEntity.badRequest().body(Map.of(
                        "error", "ValidationError",
                        "message", "요청 본문을 DTO로 변환할 수 없습니다: " + iae.getMessage()
                )));
            }
        }

        return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "error", "InvalidRequest",
                "message", "지원하지 않는 요청 형식입니다. {'1':{...}} 또는 {'agent_grids':[...]} 형태만 허용됩니다."
        )));
    }

    // 변경 전: 통합으로 인해 제거되었던 RAW 엔드포인트
    // @PostMapping(value = "/subscriptions/raw", consumes = MediaType.APPLICATION_JSON_VALUE)
    // public Mono<ResponseEntity<Object>> createRaw(@RequestBody Map<String,Object> raw) { /* 기존 구현 */ }
    // 변경 후: 호환성 유지를 위한 RAW 엔드포인트 복원 (통합 로직과 동일한 처리 경로 사용)
    @PostMapping(value = "/subscriptions/raw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> createRawCompat(@RequestBody JsonNode body) {
        log.warn("호환 엔드포인트 호출: POST /subscriptions/raw (향후 제거 예정)");
        if (body == null || !body.isObject()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", "InvalidRequest",
                    "message", "JSON 객체 본문이 필요합니다."
            )));
        }
        Map<String, Object> raw = objectMapper.convertValue(body, new TypeReference<Map<String, Object>>(){});
        return subscriptionService.createRaw(raw)
                .map(r -> ResponseEntity.ok().body((Object) r))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest().body(Map.of(
                                "error", "ValidationError",
                                "message", e.getMessage() == null ? "구독 JSON 검증 실패" : e.getMessage()
                        ))
                ));
    }

    @GetMapping("/subscriptions/{id}")
    public Mono<BrightPatternSubscriptionResponse> get(@PathVariable String id) {
        log.info("API 구독 조회 요청 - ID: {}", id);
        return subscriptionService.get(id);
    }

    @GetMapping("/subscriptions/data")
    public Mono<String> getSubscriptionData() {
        log.info("API 구독 데이터 조회 요청");
        return subscriptionService.getData();
    }

    @DeleteMapping("/subscriptions/{id}")
    public Mono<Void> delete(@PathVariable String id) {
        log.info("API 구독 삭제 요청 - ID: {}", id);
        return subscriptionService.delete(id);
    }
}
