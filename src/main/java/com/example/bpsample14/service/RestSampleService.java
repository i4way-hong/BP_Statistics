package com.example.bpsample14.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class RestSampleService {
    private static final Logger log = LoggerFactory.getLogger(RestSampleService.class);
    private final WebClient webClient;

    public RestSampleService(WebClient.Builder builder,
                             @Value("${external.sample.base-url:https://httpbin.org}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<String> getUuid() {
        return webClient.get().uri("/uuid")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnSubscribe(s -> log.debug("Calling /uuid"))
                .doOnSuccess(v -> log.debug("/uuid success"))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200)))
                .onErrorResume(e -> {
                    log.error("/uuid error", e);
                    return Mono.error(e);
                });
    }

    public Mono<String> delay(int seconds) {
        return webClient.get().uri("/delay/" + seconds)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(seconds + 3L))
                .doOnSubscribe(s -> log.debug("Calling /delay/{}", seconds))
                .doOnSuccess(v -> log.debug("/delay/{} success", seconds))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(300)))
                .onErrorResume(e -> {
                    log.error("/delay/{} error", seconds, e);
                    return Mono.error(e);
                });
    }
}
