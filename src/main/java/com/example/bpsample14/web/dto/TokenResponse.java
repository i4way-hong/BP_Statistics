package com.example.bpsample14.web.dto;

public record TokenResponse(String access_token, String token_type, long expires_in, String scope) {}
