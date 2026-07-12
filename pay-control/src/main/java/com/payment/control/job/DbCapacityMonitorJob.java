package com.payment.control.job;

import com.payment.common.enums.TaskStatus;
import com.payment.control.config.DbMonitorProperties;
import com.payment.control.service.AlertService;
import com.payment.domain.repository.AlertRecordRepository;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.ExceptionRecordRepository;
import com.payment.domain.repository.OutboxMessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据库容量与未关闭告警/工单指标巡检。
 */
@Component
@ConditionalOnProperty(name = "pay.monitor.db.enabled", havingValue = "true", matchIfMissing = true)
public class DbCapacityMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(DbCapacityMonitorJob.class);
    public static final String ALERT_DB_CAPACITY = "DB_CAPACITY";

    private final DbMonitorProperties dbProperties;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ClearanceTaskRepository clearanceTaskRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final ExceptionRecordRepository exceptionRecordRepository;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    public DbCapacityMonitorJob(DbMonitorProperties dbProperties,
                                OutboxMessageRepository outboxMessageRepository,
                                ClearanceTaskRepository clearanceTaskRepository,
                                AlertRecordRepository alertRecordRepository,
                                ExceptionRecordRepository exceptionRecordRepository,
                                AlertService alertService,
                                ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.dbProperties = dbProperties;
        this.outboxMessageRepository = outboxMessageRepository;
        this.clearanceTaskRepository = clearanceTaskRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.exceptionRecordRepository = exceptionRecordRepository;
        this.alertService = alertService;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        registerGaugesIfNeeded();
    }

    private void registerGaugesIfNeeded() {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder("pay_table_pending_count", outboxMessageRepository, r -> r.countByStatus(0))
                .tag("table", "outbox_message")
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("pay_table_pending_count", clearanceTaskRepository,
                        r -> r.countByStatus(TaskStatus.PENDING.getCode()))
                .tag("table", "clearance_task")
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("pay_alert_open_total", alertRecordRepository, AlertRecordRepository::countOpen)
                .register(meterRegistry);
        Gauge.builder("pay_exception_open_total", exceptionRecordRepository, ExceptionRecordRepository::countOpen)
                .register(meterRegistry);
        Gauge.builder("pay_outbox_pending_age_seconds", outboxMessageRepository,
                        OutboxMessageRepository::oldestPendingAgeSeconds)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${pay.monitor.db.check-interval-ms:300000}")
    public void checkCapacity() {
        if (meterRegistry == null) {
            return;
        }
        long outboxPending = outboxMessageRepository.countByStatus(0);
        long clearancePending = clearanceTaskRepository.countByStatus(TaskStatus.PENDING.getCode());
        long outboxAge = outboxMessageRepository.oldestPendingAgeSeconds();
        long openAlerts = alertRecordRepository.countOpen();
        long openExceptions = exceptionRecordRepository.countOpen();

        log.debug("db capacity outboxPending={} clearancePending={} outboxAge={}s alerts={} exceptions={}",
                outboxPending, clearancePending, outboxAge, openAlerts, openExceptions);

        if (outboxAge >= 1800) {
            alertService.send(ALERT_DB_CAPACITY, AlertService.LEVEL_ERROR,
                    "outbox oldest pending age seconds=" + outboxAge);
        } else if (outboxAge >= 600) {
            alertService.send(ALERT_DB_CAPACITY, AlertService.LEVEL_WARN,
                    "outbox oldest pending age seconds=" + outboxAge);
        }
        if (openExceptions > 0) {
            alertService.send(ALERT_DB_CAPACITY, AlertService.LEVEL_WARN,
                    "exception open total=" + openExceptions);
        }
    }
}
