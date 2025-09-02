package com.example.bpsample14.web.dto;

/**
 * BrightPattern 인증 응답 DTO
 */
public class BrightPatternAuthResponse {
    private String status;
    private String message;
    private String sessionId;
    private String userId;
    private Long timestamp;
    private Object data;  // 추가 데이터가 있을 경우

    public BrightPatternAuthResponse() {}

    public BrightPatternAuthResponse(String status, String message, String sessionId, String userId, Long timestamp, Object data) {
        this.status = status;
        this.message = message;
        this.sessionId = sessionId;
        this.userId = userId;
        this.timestamp = timestamp;
        this.data = data;
    }

    // Getters and Setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    @Override
    public String toString() {
        return "BrightPatternAuthResponse{" +
                "status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                ", data=" + data +
                '}';
    }
}
