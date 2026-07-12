package com.payment.test;

/**
 * 清算接口压测入口。
 *
 * <p>示例:
 * <pre>
 * java -jar pay-test/target/pay-test-1.0.0-SNAPSHOT.jar
 * java -jar pay-test/target/pay-test-1.0.0-SNAPSHOT.jar --clients=1000 --tps=2000 --duration=60
 * </pre>
 */
public class ClearanceLoadTestMain {

    public static void main(String[] args) throws Exception {
        LoadTestConfig config = LoadTestConfig.load(args);
        ClearanceLoadTestRunner runner = new ClearanceLoadTestRunner(config);
        LoadTestMetrics.Report report = runner.run();
        System.out.println(report);
        System.out.printf("目标 TPS=%d, 实际 TPS=%.2f%n", config.tps, report.actualTps());
        if (report.failed() > 0) {
            System.exit(1);
        }
    }
}
