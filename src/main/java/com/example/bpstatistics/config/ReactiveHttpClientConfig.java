package com.example.bpstatistics.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.ConnectionObserver;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Reactor Netty HttpClient/WebClient 중앙 설정
 * PrematureCloseException 감소를 위해 idle/연결 관리 및 타임아웃 명시.
 */
@Configuration
@EnableConfigurationProperties(ReactiveHttpClientConfig.HttpProps.class)
public class ReactiveHttpClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider connectionProvider(HttpProps props){
        return ConnectionProvider.builder("bp-fixed")
                .maxConnections(props.getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(props.getPendingAcquireTimeoutMs()))
                .maxIdleTime(Duration.ofSeconds(props.getMaxIdleTimeSeconds()))
                .evictInBackground(Duration.ofSeconds(props.getEvictInBackgroundSeconds()))
                .lifo()
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpClient httpClient(ConnectionProvider provider, HttpProps props){
        HttpClient base = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                .responseTimeout(Duration.ofSeconds(props.getResponseTimeoutSeconds()))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(props.getReadTimeoutSeconds()))
                        .addHandlerLast(new WriteTimeoutHandler(props.getWriteTimeoutSeconds())))
                .compress(true)
                .keepAlive(true)
                .observe((ConnectionObserver) (connection, newState) -> {
                    if(newState == ConnectionObserver.State.RELEASED){
                        // verbose: log.debug("Connection released: {}", connection);
                    }
                });
        if(props.isWiretap()){
            base = base.wiretap("reactor.netty.http.client");
        }
        return base;
    }

    @Bean
    public WebClient.Builder webClientBuilder(HttpClient httpClient){
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build());
    }

    @ConfigurationProperties(prefix = "app.http")
    public static class HttpProps {
        private boolean wiretap = false;
        private int maxConnections = 50;
        private long pendingAcquireTimeoutMs = 5000;
        private int maxIdleTimeSeconds = 30; // 서버 idle timeout 보다 작게
        private int evictInBackgroundSeconds = 30;
        private int connectTimeoutMs = 5000;
        private int responseTimeoutSeconds = 20;
        private int readTimeoutSeconds = 25; // responseTimeout 보다 크게
        private int writeTimeoutSeconds = 10;
        public boolean isWiretap() { return wiretap; }
        public void setWiretap(boolean wiretap) { this.wiretap = wiretap; }
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public long getPendingAcquireTimeoutMs() { return pendingAcquireTimeoutMs; }
        public void setPendingAcquireTimeoutMs(long pendingAcquireTimeoutMs) { this.pendingAcquireTimeoutMs = pendingAcquireTimeoutMs; }
        public int getMaxIdleTimeSeconds() { return maxIdleTimeSeconds; }
        public void setMaxIdleTimeSeconds(int maxIdleTimeSeconds) { this.maxIdleTimeSeconds = maxIdleTimeSeconds; }
        public int getEvictInBackgroundSeconds() { return evictInBackgroundSeconds; }
        public void setEvictInBackgroundSeconds(int evictInBackgroundSeconds) { this.evictInBackgroundSeconds = evictInBackgroundSeconds; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getResponseTimeoutSeconds() { return responseTimeoutSeconds; }
        public void setResponseTimeoutSeconds(int responseTimeoutSeconds) { this.responseTimeoutSeconds = responseTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
        public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
        public void setWriteTimeoutSeconds(int writeTimeoutSeconds) { this.writeTimeoutSeconds = writeTimeoutSeconds; }
    }
}
