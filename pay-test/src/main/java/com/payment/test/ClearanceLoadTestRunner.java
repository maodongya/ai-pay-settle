package com.payment.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 清算接口压测执行器：1000 客户端并发，全局限流 TPS。
 */
public class ClearanceLoadTestRunner {

    private final LoadTestConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClearanceLoadTestRunner(LoadTestConfig config) {
        this.config = config;
    }

    public LoadTestMetrics.Report run() throws InterruptedException {
        config.validate();
        String endpoint = config.endpoint();
        TpsRateLimiter limiter = new TpsRateLimiter(config.tps);
        LoadTestMetrics metrics = new LoadTestMetrics();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong sequence = new AtomicLong();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newFixedThreadPool(Math.min(config.clients, 512)))
                .build();

        CountDownLatch ready = new CountDownLatch(config.clients);
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(config.clients);

        System.out.printf("压测启动: endpoint=%s, clients=%d, tps=%d, duration=%ds, warmup=%ds%n",
                endpoint, config.clients, config.tps, config.durationSeconds, config.warmupSeconds);

        for (int clientId = 0; clientId < config.clients; clientId++) {
            int id = clientId;
            workers.submit(() -> {
                ready.countDown();
                try {
                    started.await();
                    while (running.get()) {
                        limiter.acquire();
                        long seq = sequence.incrementAndGet();
                        invokeOnce(httpClient, endpoint, id, seq, metrics);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        if (config.warmupSeconds > 0) {
            System.out.printf("预热 %d 秒...%n", config.warmupSeconds);
            Thread.sleep(config.warmupSeconds * 1000L);
        }

        long begin = System.currentTimeMillis();
        started.countDown();
        Thread.sleep(config.durationSeconds * 1000L);
        running.set(false);

        workers.shutdown();
        workers.awaitTermination(60, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - begin;
        return metrics.report(elapsed);
    }

    private void invokeOnce(HttpClient httpClient, String endpoint, int clientId, long seq, LoadTestMetrics metrics) {
        long start = System.nanoTime();
        try {
            ClearanceBillRequest body = ClearanceBillRequest.sample(config, clientId, seq);
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            if (response.statusCode() == 200 && isBizSuccess(response.body())) {
                metrics.recordSuccess(latencyMs);
            } else {
                metrics.recordFailure(latencyMs);
            }
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            metrics.recordFailure(latencyMs);
        }
    }

    private boolean isBizSuccess(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        return root.has("code") && root.get("code").asInt() == 0;
    }
}
