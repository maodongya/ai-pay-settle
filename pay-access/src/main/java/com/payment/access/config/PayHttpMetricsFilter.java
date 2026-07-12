package com.payment.access.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 接入层 TPS 与耗时指标（仅 /api/v1/**）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PayHttpMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry registry;

    public PayHttpMetricsFilter(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return registry == null || uri == null || !uri.startsWith("/api/v1");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String uri = normalizeUri(request.getRequestURI());
        String method = request.getMethod();
        try {
            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            registry.counter("pay_http_request_total",
                    "uri", uri,
                    "method", method,
                    "status", String.valueOf(status)).increment();
            registry.timer("pay_http_request_duration", "uri", uri, "method", method)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private String normalizeUri(String uri) {
        if (uri == null) {
            return "unknown";
        }
        return uri.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
    }
}
