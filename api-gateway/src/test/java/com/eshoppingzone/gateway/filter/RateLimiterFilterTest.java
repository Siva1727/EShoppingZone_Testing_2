package com.eshoppingzone.gateway.filter;

import com.eshoppingzone.gateway.config.RateLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterFilterTest {

    private RateLimiterConfig config;
    private RateLimiterFilter filter;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
        config.setEnabled(true);
        config.setReplenishRate(10);
        config.setBurstCapacity(20);
        filter = new RateLimiterFilter(config);
    }

    @Test
    @DisplayName("Normal request within limit should be forwarded through the filter chain")
    void testNormalRequestWithinLimit() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Requests exceeding burst capacity should return HTTP 429 Too Many Requests")
    void testRequestsExceedingLimitReturn429() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        config.setBurstCapacity(5);
        config.setReplenishRate(1);
        filter = new RateLimiterFilter(config);

        String clientIp = "192.168.1.50";

        // Consume all 5 available tokens
        for (int i = 0; i < 5; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/products")
                    .remoteAddress(new InetSocketAddress(clientIp, 8080))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
        }

        verify(chain, times(5)).filter(any());

        // 6th request must be rejected with 429
        MockServerHttpRequest excessRequest = MockServerHttpRequest.get("/api/v1/products")
                .remoteAddress(new InetSocketAddress(clientIp, 8080))
                .build();
        MockServerWebExchange excessExchange = MockServerWebExchange.from(excessRequest);

        filter.filter(excessExchange, chain).block();

        // Chain should NOT have been called for the 6th request
        verify(chain, times(5)).filter(any());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, excessExchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, excessExchange.getResponse().getHeaders().getContentType());
        assertEquals("1", excessExchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("0", excessExchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals("5", excessExchange.getResponse().getHeaders().getFirst("X-RateLimit-Burst-Capacity"));
        assertEquals("1", excessExchange.getResponse().getHeaders().getFirst("X-RateLimit-Replenish-Rate"));
    }

    @Test
    @DisplayName("Different clients should have isolated rate limit buckets")
    void testIsolatedBucketsPerClient() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        config.setBurstCapacity(2);
        config.setReplenishRate(1);
        filter = new RateLimiterFilter(config);

        // Exhaust client A's tokens
        for (int i = 0; i < 2; i++) {
            MockServerHttpRequest reqA = MockServerHttpRequest.get("/api/v1/products")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                    .build();
            filter.filter(MockServerWebExchange.from(reqA), chain).block();
        }

        // Client A's 3rd request gets 429
        MockServerHttpRequest reqA3 = MockServerHttpRequest.get("/api/v1/products")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exA3 = MockServerWebExchange.from(reqA3);
        filter.filter(exA3, chain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exA3.getResponse().getStatusCode());

        // Client B can still make requests normally
        MockServerHttpRequest reqB = MockServerHttpRequest.get("/api/v1/products")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 8080))
                .build();
        MockServerWebExchange exB = MockServerWebExchange.from(reqB);
        filter.filter(exB, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exB.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Authenticated users are rate-limited by X-User-Id")
    void testAuthenticatedUserKeyResolution() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        config.setBurstCapacity(1);
        config.setReplenishRate(1);
        filter = new RateLimiterFilter(config);

        // First request with user ID 101 passes
        MockServerHttpRequest req1 = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "101")
                .build();
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        filter.filter(ex1, chain).block();
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ex1.getResponse().getStatusCode());

        // Second request with same user ID gets 429
        MockServerHttpRequest req2 = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "101")
                .build();
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
        filter.filter(ex2, chain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex2.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Disabled rate limiter should allow all requests")
    void testDisabledRateLimiter() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        config.setEnabled(false);
        config.setBurstCapacity(1);
        filter = new RateLimiterFilter(config);

        for (int i = 0; i < 5; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/products")
                    .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getResponse().getStatusCode());
        }

        verify(chain, times(5)).filter(any());
    }

    @Test
    @DisplayName("Filter order should be -90 to run after AuthenticationFilter (-100)")
    void testFilterOrder() {
        assertEquals(-90, filter.getOrder());
    }
}
