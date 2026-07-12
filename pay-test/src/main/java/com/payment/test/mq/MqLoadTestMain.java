package com.payment.test.mq;

import com.payment.test.LoadTestMetrics;

/**
 * MQ 压测入口：10 客户端、1000 TPS、20 分钟（可通过参数覆盖）。
 *
 * <p>示例:
 * <pre>
 * java -cp pay-test/target/pay-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
 *   com.payment.test.mq.MqLoadTestMain
 *
 * java -cp pay-test/target/pay-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
 *   com.payment.test.mq.MqLoadTestMain --clients=10 --tps=1000 --duration=1200
 * </pre>
 */
public class MqLoadTestMain {

    public static void main(String[] args) throws Exception {
        MqLoadTestConfig config = MqLoadTestConfig.load(args);
        MqLoadTestRunner runner = new MqLoadTestRunner(config);
        LoadTestMetrics.Report report = runner.run();
        System.out.println(report);
        System.out.printf("目标 TPS=%d, 实际 TPS=%.2f, 预计总量≈%d%n",
                config.tps, report.actualTps(), (long) config.tps * config.durationSeconds);
        if (report.failed() > 0) {
            System.exit(1);
        }
    }
}
