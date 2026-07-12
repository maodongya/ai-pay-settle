package com.payment.test.mq;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;

/**
 * MQ 压测配置，默认 10 客户端、1000 TPS、20 分钟。
 */
public class MqLoadTestConfig {

    public String nameServer = "127.0.0.1:9876";
    public String topic = "trade_pay_topic";
    public String tag = "PAY";
    public String producerGroup = "pay-mq-load-test-producer";
    public int clients = 10;
    public int tps = 1000;
    public int durationSeconds = 1200;
    public int warmupSeconds = 5;
    public int reportIntervalSeconds = 60;
    public long merchantId = 100001L;
    public Long agentId = 200001L;
    public Long secondAgentId = 200002L;
    public BigDecimal tradeAmount = new BigDecimal("1000.00");

    public static MqLoadTestConfig load(String[] args) throws IOException {
        MqLoadTestConfig config = new MqLoadTestConfig();
        Properties props = new Properties();
        try (InputStream in = MqLoadTestConfig.class.getClassLoader().getResourceAsStream("mq-load-test.properties")) {
            if (in != null) {
                props.load(in);
            }
        }
        config.nameServer = props.getProperty("pay.mq.test.name-server", config.nameServer);
        config.topic = props.getProperty("pay.mq.test.topic", config.topic);
        config.tag = props.getProperty("pay.mq.test.tag", config.tag);
        config.producerGroup = props.getProperty("pay.mq.test.producer-group", config.producerGroup);
        config.clients = Integer.parseInt(props.getProperty("pay.mq.test.clients", String.valueOf(config.clients)));
        config.tps = Integer.parseInt(props.getProperty("pay.mq.test.tps", String.valueOf(config.tps)));
        config.durationSeconds = Integer.parseInt(props.getProperty("pay.mq.test.duration-seconds",
                String.valueOf(config.durationSeconds)));
        config.warmupSeconds = Integer.parseInt(props.getProperty("pay.mq.test.warmup-seconds",
                String.valueOf(config.warmupSeconds)));
        config.reportIntervalSeconds = Integer.parseInt(props.getProperty("pay.mq.test.report-interval-seconds",
                String.valueOf(config.reportIntervalSeconds)));
        config.merchantId = Long.parseLong(props.getProperty("pay.mq.test.merchant-id", String.valueOf(config.merchantId)));
        config.agentId = Long.parseLong(props.getProperty("pay.mq.test.agent-id", String.valueOf(config.agentId)));
        config.secondAgentId = Long.parseLong(props.getProperty("pay.mq.test.second-agent-id",
                String.valueOf(config.secondAgentId)));
        config.tradeAmount = new BigDecimal(props.getProperty("pay.mq.test.trade-amount",
                config.tradeAmount.toPlainString()));
        config.applyArgs(args);
        return config;
    }

    private void applyArgs(String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int idx = arg.indexOf('=');
            if (idx <= 2) {
                continue;
            }
            String key = arg.substring(2, idx);
            String value = arg.substring(idx + 1);
            switch (key) {
                case "name-server", "namesrv" -> nameServer = value;
                case "topic" -> topic = value;
                case "tag" -> tag = value;
                case "producer-group" -> producerGroup = value;
                case "clients" -> clients = Integer.parseInt(value);
                case "tps" -> tps = Integer.parseInt(value);
                case "duration", "duration-seconds" -> durationSeconds = Integer.parseInt(value);
                case "warmup", "warmup-seconds" -> warmupSeconds = Integer.parseInt(value);
                case "report-interval", "report-interval-seconds" -> reportIntervalSeconds = Integer.parseInt(value);
                case "merchant-id" -> merchantId = Long.parseLong(value);
                case "agent-id" -> agentId = Long.parseLong(value);
                case "second-agent-id" -> secondAgentId = Long.parseLong(value);
                case "amount", "trade-amount" -> tradeAmount = new BigDecimal(value);
                default -> {
                }
            }
        }
    }

    public void validate() {
        if (clients <= 0) {
            throw new IllegalArgumentException("clients must be > 0");
        }
        if (tps <= 0) {
            throw new IllegalArgumentException("tps must be > 0");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("duration-seconds must be > 0");
        }
        if (nameServer == null || nameServer.isBlank()) {
            throw new IllegalArgumentException("name-server must not be blank");
        }
    }
}
