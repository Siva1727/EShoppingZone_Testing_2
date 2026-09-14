package com.eshoppingzone.gateway.filter;

import com.eshoppingzone.gateway.config.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global Rate Limiter filter using an in-memory Token Bucket algorithm.
 * Operates at the API Gateway layer to protect backend microservices.
 */
@Component
public class RateLimiterFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);

    private final RateLimiterConfig config;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterFilter(RateLimiterConfig config) {
        this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(exchange.getRequest());
        TokenBucket bucket = buckets.computeIfAbsent(clientKey,
                k -> new TokenBucket(config.getBurstCapacity(), config.getReplenishRate()));

        if (bucket.tryConsume()) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit exceeded for client: {}, path: {}", clientKey, exchange.getRequest().getURI().getPath());
        return onRateLimitExceeded(exchange);
    }

    private String resolveClientKey(ServerHttpRequest request) {
        // If user is authenticated, use User ID or User Name attached by AuthenticationFilter
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        String userName = request.getHeaders().getFirst("X-User-Name");
        if (userName != null && !userName.isBlank()) {
            return "user:" + userName;
        }

        // Check for X-Forwarded-For header in proxy environments
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        // Fallback to remote IP address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }

        return "ip:anonymous";
    }

    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        response.getHeaders().set("X-RateLimit-Remaining", "0");
        response.getHeaders().set("X-RateLimit-Burst-Capacity", String.valueOf(config.getBurstCapacity()));
        response.getHeaders().set("X-RateLimit-Replenish-Rate", String.valueOf(config.getReplenishRate()));

        String errorBody = "{\"success\":false,\"message\":\"Too many requests. Rate limit exceeded. Please try again later.\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Runs immediately after AuthenticationFilter (order = -100)
        return -90;
    }

    /**
     * Thread-safe Token Bucket implementation
     */
    public static class TokenBucket {
        private final int capacity;
        private final int refillRatePerSecond;
        private double tokens;
        private long lastRefillNanos;

        public TokenBucket(int capacity, int refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        public synchronized double getRemainingTokens() {
            refill();
            return tokens;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + (elapsedSeconds * refillRatePerSecond));
                lastRefillNanos = now;
            }
        }
    }
}
