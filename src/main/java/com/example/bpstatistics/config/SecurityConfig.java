package com.example.bpstatistics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .httpBasic(h -> h.disable())
            .formLogin(f -> f.disable())
            .headers(h -> h.frameOptions(f -> f.sameOrigin())); // H2 콘솔 대비
        return http.build();
    }
}
