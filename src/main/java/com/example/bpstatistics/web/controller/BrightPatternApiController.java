package com.example.bpstatistics.web.controller;

import com.example.bpstatistics.service.BrightPatternAuthService;
import com.example.bpstatistics.service.BrightPatternSubscriptionService;
import com.example.bpstatistics.web.dto.BrightPatternSubscriptionRequest;
import com.example.bpstatistics.web.dto.BrightPatternSubscriptionResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// 변경 전: Spring Web annotation import 누락되어 컴파일 오류 발생
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping(path = "/api/brightpattern", produces = MediaType.APPLICATION_JSON_VALUE)

public class BrightPatternApiController {
    private static final Logger log = LoggerFactory.getLogger(BrightPatternApiController.class);
    private final BrightPatternAuthService authService;
    private final BrightPatternSubscriptionService subscriptionService;

    public BrightPatternApiController(BrightPatternAuthService authService,
                                      BrightPatternSubscriptionService subscriptionService) {
        this.authService = authService;
        this.subscriptionService = subscriptionService;
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

    // 변경 전 통합 엔드포인트 (필요 시 재활성화 가능)
    // @PostMapping(value = "/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    // public Mono<ResponseEntity<?>> createUnified(@RequestBody(required = false) Map<String,Object> body) { /* 통합 로직 */ }

    // 복원: DTO 기반 구독 생성 (기존 방식)
    @PostMapping(value = "/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<BrightPatternSubscriptionResponse> create(@Valid @RequestBody BrightPatternSubscriptionRequest req) {
        log.info("API 구독 생성 요청(DTO)");
        return subscriptionService.create(req);
    }

    // Raw JSON 기반 구독 생성 (유효성 검증은 Service 내부 validateRawStructure 수행)
    @PostMapping(value = "/subscriptions/raw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> createRaw(@RequestBody Map<String,Object> raw) {
        log.info("API 구독 생성 요청(RAW)");
        return subscriptionService.createRaw(raw)
                .map(r -> ResponseEntity.ok().body((Object) r))
                .onErrorResume(e -> Mono.<ResponseEntity<Object>>just(
                        ResponseEntity.badRequest().body(Map.of(
                                "error", "ValidationError",
                                "message", e.getMessage() == null ? "구독 JSON 검증 실패" : e.getMessage()
                        ))
                ));
    }

    // 선택적: Map -> DTO 변환 유틸 (현재는 사용하지 않지만 향후 통합 엔드포인트 복구 시 활용 가능)
    // private BrightPatternSubscriptionRequest mapToDto(Map<String,Object> body) {
    //     var dto = new BrightPatternSubscriptionRequest();
    //     Object grids = body.get("agent_grids");
    //     if (grids instanceof List<?> list) {
    //         List<BrightPatternSubscriptionRequest.AgentGrid> converted = list.stream()
    //                 .filter(m -> m instanceof Map)
    //                 .map(m -> objectMapper.convertValue(m, BrightPatternSubscriptionRequest.AgentGrid.class))
    //                 .toList();
    //         dto.setAgent_grids(converted);
    //     }
    //     return dto;
    // }

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
