package com.example.bpsample14.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.lang.reflect.Constructor;

class RestSampleServiceTest {
    private MockWebServer server;
    private RestSampleService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        // Spring Context 없이 직접 인스턴스 생성 위해 리플렉션 사용
        Constructor<RestSampleService> ctor = RestSampleService.class.getConstructor(WebClient.Builder.class, String.class);
        service = ctor.newInstance(builder, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getUuid() {
        server.enqueue(new MockResponse().setBody("{\"uuid\":\"1234\"}"));
        StepVerifier.create(service.getUuid())
                .expectNextMatches(body -> body.contains("1234"))
                .verifyComplete();
    }
}
