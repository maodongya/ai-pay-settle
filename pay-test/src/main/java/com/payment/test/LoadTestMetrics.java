package com.payment.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 压测指标收集与汇总。
 */
public class LoadTestMetrics {

    private final LongAdder total = new LongAdder();
    private final LongAdder success = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder latencySumMs = new LongAdder();
    private final AtomicLong maxLatencyMs = new AtomicLong();
    private final List<Long> latencySamples = Collections.synchronizedList(new ArrayList<>());

    public void recordSuccess(long latencyMs) {
        total.increment();
        success.increment();
        recordLatency(latencyMs);
    }

    public void recordFailure(long latencyMs) {
        total.increment();
        failed.increment();
        recordLatency(latencyMs);
    }

    private void recordLatency(long latencyMs) {
        latencySumMs.add(latencyMs);
        maxLatencyMs.updateAndGet(current -> Math.max(current, latencyMs));
        latencySamples.add(latencyMs);
    }

    public long totalCount() {
        return total.sum();
    }

    public Report report(long elapsedMillis) {
        long totalCount = total.sum();
        double actualTps = elapsedMillis <= 0 ? 0 : totalCount * 1000.0 / elapsedMillis;
        double avgLatency = totalCount == 0 ? 0 : latencySumMs.sum() * 1.0 / totalCount;
        return new Report(
                totalCount,
                success.sum(),
                failed.sum(),
                actualTps,
                avgLatency,
                maxLatencyMs.get(),
                percentile(50),
                percentile(95),
                percentile(99),
                elapsedMillis
        );
    }

    private long percentile(int p) {
        if (latencySamples.isEmpty()) {
            return 0;
        }
        List<Long> copy;
        synchronized (latencySamples) {
            copy = new ArrayList<>(latencySamples);
        }
        copy.sort(Long::compareTo);
        int index = (int) Math.ceil(p / 100.0 * copy.size()) - 1;
        index = Math.max(0, Math.min(index, copy.size() - 1));
        return copy.get(index);
    }

    public record Report(
            long total,
            long success,
            long failed,
            double actualTps,
            double avgLatencyMs,
            long maxLatencyMs,
            long p50LatencyMs,
            long p95LatencyMs,
            long p99LatencyMs,
            long elapsedMs
    ) {
        public double successRate() {
            return total == 0 ? 0 : success * 100.0 / total;
        }

        @Override
        public String toString() {
            return """
                    ========== 压测结果 ==========
                    总请求数     : %d
                    成功         : %d
                    失败         : %d
                    成功率       : %.2f%%
                    目标 TPS     : (见配置)
                    实际 TPS     : %.2f
                    耗时(ms)     : %d
                    平均延迟(ms) : %.2f
                    最大延迟(ms) : %d
                    P50(ms)      : %d
                    P95(ms)      : %d
                    P99(ms)      : %d
                    ==============================
                    """.formatted(total, success, failed, successRate(), actualTps, elapsedMs,
                    avgLatencyMs, maxLatencyMs, p50LatencyMs, p95LatencyMs, p99LatencyMs);
        }
    }
}
