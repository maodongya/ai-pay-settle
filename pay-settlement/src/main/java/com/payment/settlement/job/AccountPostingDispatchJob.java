package com.payment.settlement.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.account.api.result.ApiResults;
import com.payment.common.config.PayAccountProperties;
import com.payment.common.exception.BizException;
import com.payment.domain.entity.AccountPostingOutboxEntity;
import com.payment.domain.repository.AccountPostingOutboxRepository;
import com.payment.settlement.account.AccountPostingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 账务过账发件箱异步投递 Job（ACCOUNT_ONLY 模式）。
 */
@Component
public class AccountPostingDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingDispatchJob.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 20;

    private final PayAccountProperties accountProperties;
    private final ObjectProvider<AccountPostingClient> accountPostingClient;
    private final AccountPostingOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AccountPostingDispatchJob(PayAccountProperties accountProperties,
                                     ObjectProvider<AccountPostingClient> accountPostingClient,
                                     AccountPostingOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.accountProperties = accountProperties;
        this.accountPostingClient = accountPostingClient;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${pay.account.dispatch-interval-ms:3000}")
    public void dispatch() {
        if (!accountProperties.isAccountOnly()) {
            return;
        }
        AccountPostingClient client = accountPostingClient.getIfAvailable();
        if (client == null) {
            return;
        }
        for (AccountPostingOutboxEntity item : outboxRepository.findPending(BATCH_SIZE)) {
            dispatchOne(client, item);
        }
    }

    private void dispatchOne(AccountPostingClient client, AccountPostingOutboxEntity item) {
        try {
            JsonNode payload = objectMapper.readTree(item.payloadJson);
            Long merchantId = payload.get("merchantId").asLong();
            String billNo = payload.get("billNo").asText();
            BigDecimal merchantIncome = decimal(payload, "merchantIncome");
            BigDecimal platformFee = decimal(payload, "platformFee");
            Instant occurredAt = parseBizTime(payload.path("bizTime").asText(null));
            String direction = payload.path("direction").asText("CREDIT");

            ApiResults.PostingResult result;
            if (merchantIncome != null && merchantIncome.signum() == 0 && platformFee.signum() > 0) {
                log.warn("skip fee-only posting billNo={} platformFee={} (postClearance pending)", billNo, platformFee);
                outboxRepository.markFailed(item.id, item.merchantId, "fee-only posting not supported yet");
                return;
            }
            if ("DEBIT".equalsIgnoreCase(direction) || (merchantIncome != null && merchantIncome.signum() < 0)) {
                result = postWithAccountRecovery(client, item, () -> client.debitMerchant(
                        merchantId,
                        billNo,
                        merchantIncome.abs(),
                        "settle debit",
                        occurredAt));
            } else if (merchantIncome != null && merchantIncome.signum() > 0) {
                result = postWithAccountRecovery(client, item, () -> client.settleMerchant(
                        merchantId,
                        billNo,
                        merchantIncome,
                        platformFee,
                        null,
                        occurredAt));
            } else {
                outboxRepository.markSuccess(item.id, item.merchantId, null);
                return;
            }
            outboxRepository.markSuccess(item.id, item.merchantId, result.transactionNo());
        } catch (Exception e) {
            log.warn("account posting outbox failed id={} bizNo={}", item.id, item.bizNo, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            int retries = item.retryCount == null ? 0 : item.retryCount;
            if (retries + 1 >= MAX_RETRY || isPermanent(e)) {
                outboxRepository.markFailed(item.id, item.merchantId, msg);
            } else {
                outboxRepository.markRetry(item.id, item.merchantId, msg);
            }
        }
    }

    private static boolean isPermanent(Exception e) {
        if (e instanceof BizException biz) {
            int code = biz.getCode();
            return code == com.payment.common.exception.ErrorCode.INVALID_PARAM.getCode()
                    || code == com.payment.common.exception.ErrorCode.INSUFFICIENT_BALANCE.getCode();
        }
        return false;
    }

    private ApiResults.PostingResult postWithAccountRecovery(AccountPostingClient client,
                                                             AccountPostingOutboxEntity item,
                                                             PostingAction action) {
        try {
            return action.execute();
        } catch (BizException e) {
            if (e.getCode() == com.payment.common.exception.ErrorCode.MERCHANT_INVALID.getCode()) {
                client.openSettleAccounts(item.merchantId);
                return action.execute();
            }
            throw e;
        }
    }

    private static BigDecimal decimal(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText());
    }

    private static Instant parseBizTime(String bizTime) {
        if (bizTime == null || bizTime.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(bizTime);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(bizTime).atZone(java.time.ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e) {
                return Instant.now();
            }
        }
    }

    @FunctionalInterface
    private interface PostingAction {
        ApiResults.PostingResult execute();
    }
}
