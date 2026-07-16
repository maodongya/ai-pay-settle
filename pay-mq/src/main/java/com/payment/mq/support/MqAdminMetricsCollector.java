package com.payment.mq.support;

import com.payment.mq.MqConsumerGroups;
import com.payment.mq.MqTopics;
import com.payment.mq.config.PayMqProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通过 RocketMQ Admin API 采集 Consumer Lag / DLQ 指标，暴露到 Prometheus。
 * <p>
 * 依赖 rocketmq-tools/client/remoting 均为 5.3.0；admin 类型在
 * {@code org.apache.rocketmq.remoting.protocol.admin}。
 */
@Component
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true")
public class MqAdminMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MqAdminMetricsCollector.class);

    private static final String[][] GROUP_TOPICS = {
            {MqConsumerGroups.ACCESS, MqTopics.TRADE_PAY},
            {MqConsumerGroups.ACCESS_REFUND, MqTopics.TRADE_REFUND},
            {MqConsumerGroups.CALC, MqTopics.CLEARANCE_TASK},
            {MqConsumerGroups.SETTLEMENT, MqTopics.SETTLE_AMOUNT},
            {MqConsumerGroups.SETTLEMENT + "-payment", MqTopics.PAYMENT_RESULT}
    };

    private final PayMqProperties payMqProperties;
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> lagHolders = new LinkedHashMap<>();
    private final Map<String, AtomicLong> dlqHolders = new LinkedHashMap<>();
    private volatile DefaultMQAdminExt admin;

    public MqAdminMetricsCollector(PayMqProperties payMqProperties,
                                   ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.payMqProperties = payMqProperties;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        registerGauges();
    }

    private void registerGauges() {
        if (meterRegistry == null) {
            return;
        }
        for (String[] pair : GROUP_TOPICS) {
            String group = pair[0];
            String topic = pair[1];
            String lagKey = lagKey(group, topic);
            AtomicLong lagHolder = new AtomicLong(0);
            lagHolders.put(lagKey, lagHolder);
            Gauge.builder("pay_mq_consumer_lag", lagHolder, AtomicLong::get)
                    .description("RocketMQ consumer lag by group and topic")
                    .tag("group", group)
                    .tag("topic", topic)
                    .register(meterRegistry);
        }
        for (String group : distinctGroups()) {
            AtomicLong dlqHolder = new AtomicLong(0);
            dlqHolders.put(group, dlqHolder);
            Gauge.builder("pay_mq_dlq_count", dlqHolder, AtomicLong::get)
                    .description("RocketMQ DLQ message count by consumer group")
                    .tag("group", group)
                    .register(meterRegistry);
        }
    }

    private static String[] distinctGroups() {
        return java.util.Arrays.stream(GROUP_TOPICS).map(p -> p[0]).distinct().toArray(String[]::new);
    }

    @Scheduled(fixedDelayString = "${pay.mq.infra-metrics-interval-ms:30000}")
    public void refresh() {
        if (meterRegistry == null || !payMqProperties.isEnabled()) {
            return;
        }
        try {
            DefaultMQAdminExt client = ensureAdmin();
            for (String[] pair : GROUP_TOPICS) {
                String group = pair[0];
                String topic = pair[1];
                lagHolders.get(lagKey(group, topic)).set(queryLag(client, group, topic));
            }
            for (String group : distinctGroups()) {
                dlqHolders.get(group).set(queryDlqCount(client, group));
            }
        } catch (Exception e) {
            log.warn("mq infra metrics refresh failed: {}", e.getMessage());
        }
    }

    /** 供 DlqInspectJob 复用 */
    public long currentDlqCount(String consumerGroup) {
        AtomicLong holder = dlqHolders.get(consumerGroup);
        return holder == null ? 0L : holder.get();
    }

    long queryDlqCount(DefaultMQAdminExt client, String consumerGroup) {
        try {
            String dlqTopic = "%DLQ%" + consumerGroup;
            TopicStatsTable stats = client.examineTopicStats(dlqTopic);
            long sum = 0L;
            for (TopicOffset offset : stats.getOffsetTable().values()) {
                sum += Math.max(0, offset.getMaxOffset() - offset.getMinOffset());
            }
            return sum;
        } catch (Exception e) {
            log.trace("dlq query failed group={} msg={}", consumerGroup, e.getMessage());
            return 0L;
        }
    }

    private long queryLag(DefaultMQAdminExt client, String group, String topic) {
        try {
            ConsumeStats stats = client.examineConsumeStats(group, topic);
            return Math.max(0, stats.computeTotalDiff());
        } catch (Exception e) {
            log.trace("lag query failed group={} topic={} msg={}", group, topic, e.getMessage());
            return 0L;
        }
    }

    private DefaultMQAdminExt ensureAdmin() throws MQClientException {
        if (admin == null) {
            synchronized (this) {
                if (admin == null) {
                    DefaultMQAdminExt ext = new DefaultMQAdminExt();
                    ext.setNamesrvAddr(payMqProperties.getNameServer());
                    ext.setInstanceName("pay-infra-metrics-" + System.currentTimeMillis());
                    ext.start();
                    admin = ext;
                }
            }
        }
        return admin;
    }

    @PreDestroy
    public void shutdown() {
        if (admin != null) {
            admin.shutdown();
        }
    }

    private static String lagKey(String group, String topic) {
        return group + "|" + topic;
    }
}
