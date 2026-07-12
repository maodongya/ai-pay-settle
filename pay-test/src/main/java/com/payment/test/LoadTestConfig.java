package com.payment.test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;

/**
 * 压测配置，支持 properties 文件与命令行参数覆盖。
 */
public class LoadTestConfig {

    public String baseUrl = "http://127.0.0.1:18089";
    public String path = "/api/v1/clearance/bill/submit";
    public int clients = 1000;
    public int tps = 2000;
    public int durationSeconds = 60;
    public int warmupSeconds = 3;
    public long merchantId = 100001L;
    public Long agentId = 200001L;
    public Long secondAgentId = 200002L;
    public BigDecimal tradeAmount = new BigDecimal("1000.00");

    public static LoadTestConfig load(String[] args) throws IOException {
        LoadTestConfig config = new LoadTestConfig();
        Properties props = new Properties();
        try (InputStream in = LoadTestConfig.class.getClassLoader().getResourceAsStream("load-test.properties")) {
            if (in != null) {
                props.load(in);
            }
        }
        config.baseUrl = props.getProperty("pay.test.base-url", config.baseUrl);
        config.path = props.getProperty("pay.test.path", config.path);
        config.clients = Integer.parseInt(props.getProperty("pay.test.clients", String.valueOf(config.clients)));
        config.tps = Integer.parseInt(props.getProperty("pay.test.tps", String.valueOf(config.tps)));
        config.durationSeconds = Integer.parseInt(props.getProperty("pay.test.duration-seconds",
                String.valueOf(config.durationSeconds)));
        config.warmupSeconds = Integer.parseInt(props.getProperty("pay.test.warmup-seconds",
                String.valueOf(config.warmupSeconds)));
        config.merchantId = Long.parseLong(props.getProperty("pay.test.merchant-id", String.valueOf(config.merchantId)));
        config.agentId = Long.parseLong(props.getProperty("pay.test.agent-id", String.valueOf(config.agentId)));
        config.secondAgentId = Long.parseLong(props.getProperty("pay.test.second-agent-id",
                String.valueOf(config.secondAgentId)));
        config.tradeAmount = new BigDecimal(props.getProperty("pay.test.trade-amount", config.tradeAmount.toPlainString()));
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
                case "url", "base-url" -> baseUrl = value;
                case "path" -> path = value;
                case "clients" -> clients = Integer.parseInt(value);
                case "tps" -> tps = Integer.parseInt(value);
                case "duration", "duration-seconds" -> durationSeconds = Integer.parseInt(value);
                case "warmup", "warmup-seconds" -> warmupSeconds = Integer.parseInt(value);
                case "merchant-id" -> merchantId = Long.parseLong(value);
                case "agent-id" -> agentId = Long.parseLong(value);
                case "second-agent-id" -> secondAgentId = Long.parseLong(value);
                case "amount", "trade-amount" -> tradeAmount = new BigDecimal(value);
                default -> {
                }
            }
        }
    }

    public String endpoint() {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
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
    }
}
