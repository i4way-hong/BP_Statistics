package com.example.bpstatistics.service;

import com.example.bpstatistics.web.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OAuthService {
    private final OAuthClient oAuthClient;

    @Value("${external.oauth.client-id}")
    private String clientId;
    @Value("${external.oauth.client-secret}")
    private String clientSecret;
    @Value("${external.oauth.scope}")
    private String scope;

    public OAuthService(OAuthClient oAuthClient) {
        this.oAuthClient = oAuthClient;
    }

    public Mono<TokenResponse> fetchToken() {
        return oAuthClient.getToken(clientId, clientSecret, scope);
    }
}
