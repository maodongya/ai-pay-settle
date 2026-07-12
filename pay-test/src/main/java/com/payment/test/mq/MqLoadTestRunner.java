package com.payment.test.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.test.LoadTestMetrics;
import com.payment.test.TpsRateLimiter;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQ 压测执行器：多客户端并发，全局限流 TPS，向 trade_pay_topic 持续发送消息。
 */
public class MqLoadTestRunner {

    private final MqLoadTestConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqLoadTestRunner(MqLoadTestConfig config) {
        this.config = config;
    }

    public LoadTestMetrics.Report run() throws Exception {
        config.validate();
        TpsRateLimiter limiter = new TpsRateLimiter(config.tps);
        LoadTestMetrics metrics = new LoadTestMetrics();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong sequence = new AtomicLong();

        DefaultMQProducer producer = new DefaultMQProducer(config.producerGroup);
        producer.setNamesrvAddr(config.nameServer);
        producer.setSendMsgTimeout(10_000);
        System.out.println("正在连接 NameServer ...");
        producer.start();
        System.out.println("Producer 已启动");

        CountDownLatch ready = new CountDownLatch(config.clients);
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(config.clients);

        System.out.printf("MQ 压测启动: topic=%s, tag=%s, nameServer=%s, clients=%d, tps=%d, duration=%ds, warmup=%ds%n",
                config.topic, config.tag, config.nameServer, config.clients, config.tps,
                config.durationSeconds, config.warmupSeconds);

        for (int clientId = 0; clientId < config.clients; clientId++) {
            int id = clientId;
            workers.submit(() -> {
                ready.countDown();
                try {
                    started.await();
                    while (running.get()) {
                        limiter.acquire();
                        if (!running.get()) {
                            break;
                        }
                        long seq = sequence.incrementAndGet();
                        sendOnce(producer, id, seq, metrics);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    System.err.printf("worker-%d crashed: %s%n", id, t.getMessage());
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

        Thread reporter = startProgressReporter(metrics, begin, running);
        Thread.sleep(config.durationSeconds * 1000L);
        running.set(false);

        workers.shutdown();
        if (!workers.awaitTermination(120, TimeUnit.SECONDS)) {
            workers.shutdownNow();
        }
        reporter.interrupt();
        reporter.join(2000);

        producer.shutdown();

        long elapsed = System.currentTimeMillis() - begin;
        return metrics.report(elapsed);
    }

    private void sendOnce(DefaultMQProducer producer, int clientId, long seq, LoadTestMetrics metrics) {
        long start = System.nanoTime();
        try {
            TradePayMessage body = TradePayMessage.sample(config, clientId, seq);
            String json = objectMapper.writeValueAsString(body);
            Message message = new Message(config.topic, config.tag, body.billNo,
                    json.getBytes(StandardCharsets.UTF_8));
            SendResult result = producer.send(message);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            if (result.getSendStatus() == SendStatus.SEND_OK) {
                metrics.recordSuccess(latencyMs);
            } else {
                metrics.recordFailure(latencyMs);
            }
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            metrics.recordFailure(latencyMs);
            if (metrics.totalCount() <= 3) {
                System.err.printf("send failed billNo=%s err=%s%n",
                        "MQ" + clientId + "-" + seq, e.getMessage());
            }
        }
    }

    private Thread startProgressReporter(LoadTestMetrics metrics, long beginMillis, AtomicBoolean running) {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(config.reportIntervalSeconds * 1000L);
                    if (!running.get() && metrics.totalCount() == 0) {
                        continue;
                    }
                    long elapsed = System.currentTimeMillis() - beginMillis;
                    LoadTestMetrics.Report snapshot = metrics.report(elapsed);
                    System.out.printf("[进度] elapsed=%ds total=%d success=%d failed=%d actualTps=%.2f p99=%dms%n",
                            elapsed / 1000, snapshot.total(), snapshot.success(), snapshot.failed(),
                            snapshot.actualTps(), snapshot.p99LatencyMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "mq-load-reporter");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
