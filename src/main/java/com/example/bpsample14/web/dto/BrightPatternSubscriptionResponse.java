package com.example.bpsample14.web.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;

/**
 * BrightPattern 구독 응답 DTO - 필드 가변성 대비
 */
public class BrightPatternSubscriptionResponse {
    private String subscriptionId; // 예상되는 식별자 (없으면 dynamic map 에 저장)
    private String status;
    private Long expiresAt;
    private final Map<String,Object> others = new HashMap<>();

    public String getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    @JsonAnySetter
    public void put(String k, Object v) { others.put(k,v); }
    @JsonAnyGetter
    public Map<String,Object> getOthers() { return others; }
}
